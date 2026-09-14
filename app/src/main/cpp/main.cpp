#include <android/native_window_jni.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <mutex>
#include <string>
#include <thread>

#include "renderer.h"
#include "savegame.h"

#define LOG_TAG "HellsMountainUnknown"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex gMutex;
std::condition_variable gCv;
ANativeWindow* gWindow = nullptr;
std::thread gRenderThread;
bool gRunning = false;
std::atomic<int> gQualityTier{1};

Renderer* gRenderer = nullptr;  // owned by RenderLoop's local; raw pointer
                                 // used only for cross-thread save requests,
                                 // guarded by gMutex like everything else here.
std::string gFilesDir;
std::atomic<bool> gSaveRequested{false};
std::atomic<int> gSaveRequestSlot{0};

// Pending spawn state, set by nativeStartRun() before the surface/render
// thread exists yet, consumed once on the first frame after spawn.
std::mutex gSpawnMutex;
bool gSpawnPending = false;
bool gSpawnIsNewGame = true;
int gSpawnSlot = 1;

// Touch input accumulated from the UI thread, consumed once per frame by
// the render thread. Guarded separately from gMutex since it's touched at
// a much higher frequency (every touch move event) and shouldn't contend
// with window-lifecycle locking.
std::mutex gInputMutex;
Player::Input gPendingInput;

GraphicsQuality TierFromInt(int tier) {
    if (tier <= 0) return GraphicsQuality::kLightweight;
    if (tier >= 2) return GraphicsQuality::kHigh;
    return GraphicsQuality::kBalanced;
}

void ApplyPendingSpawn(Renderer& renderer) {
    std::lock_guard<std::mutex> lock(gSpawnMutex);
    if (!gSpawnPending) return;
    gSpawnPending = false;

    if (gSpawnIsNewGame) {
        renderer.SpawnNewGame();
    } else {
        SaveGame::PlayerState state;
        if (SaveGame::LoadSlot(gFilesDir.c_str(), gSpawnSlot, &state)) {
            renderer.GetPlayer().Reset(state.x, state.y, state.z, state.yawDeg);
        } else {
            LOGE("Continue requested but slot %d had no valid save; using default spawn", gSpawnSlot);
            renderer.SpawnNewGame();
        }
    }
}

void RenderLoop(AAssetManager* assetManager) {
    Renderer renderer;
    renderer.SetAssetManager(assetManager);
    bool rendererHasWindow = false;
    ANativeWindow* activeWindow = nullptr;
    auto start = std::chrono::steady_clock::now();
    auto lastFrame = start;

    {
        std::lock_guard<std::mutex> lock(gMutex);
        gRenderer = &renderer;
    }

    while (true) {
        ANativeWindow* nextWindow = nullptr;
        {
            std::unique_lock<std::mutex> lock(gMutex);
            gCv.wait_for(lock, std::chrono::milliseconds(16), [] {
                return !gRunning || gWindow != nullptr;
            });
            if (!gRunning) break;

            nextWindow = gWindow;
            if (nextWindow != nullptr) {
                ANativeWindow_acquire(nextWindow);
            }
        }

        if (nextWindow != activeWindow) {
            if (rendererHasWindow) {
                renderer.OnWindowTerm();
                rendererHasWindow = false;
            }
            if (activeWindow) {
                ANativeWindow_release(activeWindow);
                activeWindow = nullptr;
            }

            activeWindow = nextWindow;
            if (activeWindow) {
                renderer.SetQuality(TierFromInt(gQualityTier.load()));
                rendererHasWindow = renderer.OnWindowInit(activeWindow);
                if (!rendererHasWindow) {
                    LOGE("Native renderer could not initialize; Java activity remains alive");
                }
            }
        } else if (nextWindow) {
            ANativeWindow_release(nextWindow);
        }

        if (!rendererHasWindow) continue;

        ApplyPendingSpawn(renderer);

        if (gSaveRequested.exchange(false)) {
            int slot = gSaveRequestSlot.load();
            Player::Vec3 body = renderer.GetPlayer().BodyPosition();
            SaveGame::PlayerState state{body.x, body.y, body.z, renderer.GetPlayer().Yaw()};
            if (!SaveGame::SaveSlot(gFilesDir.c_str(), slot, state)) {
                LOGE("Save to slot %d failed", slot);
            } else {
                LOGI("Saved slot %d", slot);
            }
        }

        {
            std::lock_guard<std::mutex> lock(gInputMutex);
            renderer.SetPlayerInput(gPendingInput);
        }

        const auto now = std::chrono::steady_clock::now();
        const double elapsed = std::chrono::duration<double>(now - start).count();
        const double dt = std::chrono::duration<double>(now - lastFrame).count();
        lastFrame = now;
        renderer.DrawFrame(elapsed, dt);
    }

    {
        std::lock_guard<std::mutex> lock(gMutex);
        gRenderer = nullptr;
    }
    if (rendererHasWindow) renderer.OnWindowTerm();
    if (activeWindow) ANativeWindow_release(activeWindow);
}

void StopRenderThread() {
    {
        std::lock_guard<std::mutex> lock(gMutex);
        gRunning = false;
        if (gWindow) {
            ANativeWindow_release(gWindow);
            gWindow = nullptr;
        }
    }
    gCv.notify_all();
    if (gRenderThread.joinable()) gRenderThread.join();
}
}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_hellsmountainunknown_game_GameEngineActivity_nativeSetQuality(
        JNIEnv*, jclass, jint tier) {
    gQualityTier.store(static_cast<int>(tier), std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hellsmountainunknown_game_GameEngineActivity_nativeStartRun(
        JNIEnv* env, jclass, jobject assetManagerJava, jstring filesDirJava,
        jboolean isNewGame, jint slot) {
    AAssetManager* assetManager = AAssetManager_fromJava(env, assetManagerJava);

    const char* dirChars = env->GetStringUTFChars(filesDirJava, nullptr);
    {
        std::lock_guard<std::mutex> lock(gMutex);
        gFilesDir = dirChars ? dirChars : "";
    }
    if (dirChars) env->ReleaseStringUTFChars(filesDirJava, dirChars);

    {
        std::lock_guard<std::mutex> lock(gSpawnMutex);
        gSpawnPending = true;
        gSpawnIsNewGame = (isNewGame == JNI_TRUE);
        gSpawnSlot = static_cast<int>(slot);
    }

    {
        std::lock_guard<std::mutex> lock(gMutex);
        if (!gRunning) {
            gRunning = true;
            gRenderThread = std::thread(RenderLoop, assetManager);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_hellsmountainunknown_game_GameEngineActivity_nativeSetSurface(
        JNIEnv* env, jclass, jobject surface) {
    ANativeWindow* newWindow = nullptr;
    if (surface != nullptr) {
        newWindow = ANativeWindow_fromSurface(env, surface);
    }

    {
        std::lock_guard<std::mutex> lock(gMutex);
        if (gWindow) {
            ANativeWindow_release(gWindow);
            gWindow = nullptr;
        }
        gWindow = newWindow;
    }
    gCv.notify_all();
}

extern "C" JNIEXPORT void JNICALL
Java_com_hellsmountainunknown_game_GameEngineActivity_nativeStop(
        JNIEnv*, jclass) {
    StopRenderThread();
}

extern "C" JNIEXPORT void JNICALL
Java_com_hellsmountainunknown_game_GameEngineActivity_nativeSetMoveAxis(
        JNIEnv*, jclass, jfloat forward, jfloat strafe) {
    std::lock_guard<std::mutex> lock(gInputMutex);
    gPendingInput.moveForward = forward;
    gPendingInput.moveStrafe = strafe;
}

extern "C" JNIEXPORT void JNICALL
Java_com_hellsmountainunknown_game_GameEngineActivity_nativeAddLookDelta(
        JNIEnv*, jclass, jfloat dx, jfloat dy) {
    std::lock_guard<std::mutex> lock(gInputMutex);
    gPendingInput.lookDeltaYaw += dx;
    gPendingInput.lookDeltaPitch += dy;
}

extern "C" JNIEXPORT void JNICALL
Java_com_hellsmountainunknown_game_GameEngineActivity_nativeRequestSave(
        JNIEnv*, jclass, jint slot) {
    gSaveRequestSlot.store(static_cast<int>(slot));
    gSaveRequested.store(true);
}

static JNINativeMethod kNativeMethods[] = {
    { const_cast<char*>("nativeSetQuality"), const_cast<char*>("(I)V"),
      reinterpret_cast<void*>(Java_com_hellsmountainunknown_game_GameEngineActivity_nativeSetQuality) },
    { const_cast<char*>("nativeStartRun"),
      const_cast<char*>("(Landroid/content/res/AssetManager;Ljava/lang/String;ZI)V"),
      reinterpret_cast<void*>(Java_com_hellsmountainunknown_game_GameEngineActivity_nativeStartRun) },
    { const_cast<char*>("nativeSetSurface"), const_cast<char*>("(Landroid/view/Surface;)V"),
      reinterpret_cast<void*>(Java_com_hellsmountainunknown_game_GameEngineActivity_nativeSetSurface) },
    { const_cast<char*>("nativeStop"), const_cast<char*>("()V"),
      reinterpret_cast<void*>(Java_com_hellsmountainunknown_game_GameEngineActivity_nativeStop) },
    { const_cast<char*>("nativeSetMoveAxis"), const_cast<char*>("(FF)V"),
      reinterpret_cast<void*>(Java_com_hellsmountainunknown_game_GameEngineActivity_nativeSetMoveAxis) },
    { const_cast<char*>("nativeAddLookDelta"), const_cast<char*>("(FF)V"),
      reinterpret_cast<void*>(Java_com_hellsmountainunknown_game_GameEngineActivity_nativeAddLookDelta) },
    { const_cast<char*>("nativeRequestSave"), const_cast<char*>("(I)V"),
      reinterpret_cast<void*>(Java_com_hellsmountainunknown_game_GameEngineActivity_nativeRequestSave) },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm == nullptr || vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass activity = env->FindClass("com/hellsmountainunknown/game/GameEngineActivity");
    if (activity == nullptr) return JNI_ERR;
    if (env->RegisterNatives(activity, kNativeMethods,
            static_cast<jint>(sizeof(kNativeMethods) / sizeof(kNativeMethods[0]))) != JNI_OK) {
        env->DeleteLocalRef(activity);
        return JNI_ERR;
    }
    env->DeleteLocalRef(activity);
    LOGI("JNI registration complete");
    return JNI_VERSION_1_6;
}
