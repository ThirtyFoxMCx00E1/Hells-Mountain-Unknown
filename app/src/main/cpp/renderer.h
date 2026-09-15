#ifndef UNSOLVEDCASE_RENDERER_H
#define UNSOLVEDCASE_RENDERER_H

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <android/asset_manager.h>

#include "quality_settings.h"
#include "world.h"
#include "player.h"
#include "spawn.h"

// Owns the EGL context/surface and the GLES3 draw calls. One instance per
// app lifetime; call OnWindowInit/OnWindowTerm as the native window comes
// and goes (screen off, app backgrounded, etc).
class Renderer {
public:
    Renderer() = default;
    ~Renderer();

    bool OnWindowInit(ANativeWindow* window);
    void OnWindowTerm();
    void DrawFrame(double elapsedSeconds, double dtSeconds);

    void SetQuality(GraphicsQuality tier) { quality_ = QualitySettings::ForTier(tier); }
    const QualitySettings& Quality() const { return quality_; }
    void SetViewDistance(float distance) { quality_.viewDistance = distance; }

    // Loads the terrain from assets. Safe to call before or after
    // OnWindowInit - if there's no GL context yet, geometry upload happens
    // lazily on the first DrawFrame after a context exists.
    void SetAssetManager(AAssetManager* mgr) { assetManager_ = mgr; }

    Player& GetPlayer() { return player_; }
    void SetPlayerInput(const Player::Input& input) { pendingInput_ = input; }

    // Places the player at the world's actual default spawn point (see
    // spawn.cpp). Only meaningful after the world has loaded; safe to call
    // before that too, but will fall back to the origin until it has.
    void SpawnNewGame();

private:
    bool InitEglContext();
    void TermEglContext();
    GLuint CompileShader(GLenum type, const char* src);
    GLuint LinkProgram(GLuint vs, GLuint fs);
    void BuildShaderPrograms();
    void EnsureWorldLoaded();

    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLConfig config_ = nullptr;

    ANativeWindow* window_ = nullptr;
    int32_t width_ = 0;
    int32_t height_ = 0;

    QualitySettings quality_ = QualitySettings::ForTier(GraphicsQuality::kBalanced);

    GLuint programLightweight_ = 0;
    GLuint programBalanced_ = 0;
    bool shadersReady_ = false;

    AAssetManager* assetManager_ = nullptr;
    World world_;
    Player player_;
    Player::Input pendingInput_;
};

#endif // UNSOLVEDCASE_RENDERER_H
