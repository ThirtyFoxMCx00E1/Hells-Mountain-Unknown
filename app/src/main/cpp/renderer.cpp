#include "renderer.h"

#include <android/log.h>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

#define LOG_TAG "HellsMountainUnknown"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

const char* kVertexShaderSrc = R"(#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vNormal;
out vec3 vWorldPos;
void main() {
    vNormal = mat3(uModel) * aNormal;
    vec4 world = uModel * vec4(aPosition, 1.0);
    vWorldPos = world.xyz;
    gl_Position = uMVP * vec4(aPosition, 1.0);
}
)";

const char* kFragmentShaderLightweightSrc = R"(#version 300 es
precision mediump float;
in vec3 vNormal;
in vec3 vWorldPos;
out vec4 fragColor;
uniform vec3 uBaseColor;
void main() {
    vec3 n = normalize(vNormal);
    float lambert = max(dot(n, normalize(vec3(0.3, 0.8, 0.4))), 0.15);
    fragColor = vec4(uBaseColor * lambert, 1.0);
}
)";

const char* kFragmentShaderBalancedSrc = R"(#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorldPos;
out vec4 fragColor;
uniform vec3 uBaseColor;
uniform vec3 uLightPositions[4];
uniform vec3 uLightColors[4];
uniform int uLightCount;
void main() {
    vec3 n = normalize(vNormal);
    vec3 result = uBaseColor * 0.08;
    for (int i = 0; i < 4; i++) {
        if (i >= uLightCount) break;
        vec3 toLight = uLightPositions[i] - vWorldPos;
        float dist = length(toLight);
        vec3 lightDir = toLight / max(dist, 0.001);
        float attenuation = 1.0 / (1.0 + 0.15 * dist + 0.05 * dist * dist);
        float diffuse = max(dot(n, lightDir), 0.0);
        result += uBaseColor * uLightColors[i] * diffuse * attenuation;
    }
    fragColor = vec4(result, 1.0);
}
)";

struct Mat4 { float m[16]; };

Mat4 Multiply(const Mat4& a, const Mat4& b) {
    Mat4 r{};
    for (int c = 0; c < 4; c++) {
        for (int row = 0; row < 4; row++) {
            r.m[c * 4 + row] = 0.0f;
            for (int k = 0; k < 4; k++) {
                r.m[c * 4 + row] += a.m[k * 4 + row] * b.m[c * 4 + k];
            }
        }
    }
    return r;
}

Mat4 Perspective(float fovYRad, float aspect, float zNear, float zFar) {
    Mat4 r{};
    float f = 1.0f / std::tan(fovYRad / 2.0f);
    r.m[0] = f / aspect; r.m[5] = f;
    r.m[10] = (zFar + zNear) / (zNear - zFar);
    r.m[11] = -1.0f;
    r.m[14] = (2 * zFar * zNear) / (zNear - zFar);
    return r;
}

Mat4 LookAt(float ex, float ey, float ez, float cx, float cy, float cz) {
    float fx = cx - ex, fy = cy - ey, fz = cz - ez;
    float flen = std::sqrt(fx*fx + fy*fy + fz*fz);
    fx /= flen; fy /= flen; fz /= flen;
    float upx = 0, upy = 1, upz = 0;
    float sx = fy*upz - fz*upy, sy = fz*upx - fx*upz, sz = fx*upy - fy*upx;
    float slen = std::sqrt(sx*sx + sy*sy + sz*sz);
    sx /= slen; sy /= slen; sz /= slen;
    float ux = sy*fz - sz*fy, uy = sz*fx - sx*fz, uz = sx*fy - sy*fx;

    Mat4 r{};
    r.m[0]=sx; r.m[4]=sy; r.m[8]=sz;
    r.m[1]=ux; r.m[5]=uy; r.m[9]=uz;
    r.m[2]=-fx; r.m[6]=-fy; r.m[10]=-fz;
    r.m[15]=1;
    r.m[12] = -(sx*ex + sy*ey + sz*ez);
    r.m[13] = -(ux*ex + uy*ey + uz*ez);
    r.m[14] = (fx*ex + fy*ey + fz*ez);
    return r;
}

Mat4 Identity() {
    Mat4 r{};
    r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1.0f;
    return r;
}

} // namespace

Renderer::~Renderer() {
    TermEglContext();
}

bool Renderer::InitEglContext() {
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }
    if (!eglInitialize(display_, nullptr, nullptr)) {
        LOGE("eglInitialize failed");
        return false;
    }
    if (!eglBindAPI(EGL_OPENGL_ES_API)) {
        LOGE("eglBindAPI(EGL_OPENGL_ES_API) failed (err=0x%x)", eglGetError());
        return false;
    }

    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_NONE
    };
    EGLint numConfigs = 0;
    if (!eglChooseConfig(display_, attribs, &config_, 1, &numConfigs) || numConfigs == 0) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, ctxAttribs);
    if (context_ == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return false;
    }
    return true;
}

void Renderer::TermEglContext() {
    OnWindowTerm();
    if (display_ != EGL_NO_DISPLAY) {
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
            context_ = EGL_NO_CONTEXT;
        }
        eglTerminate(display_);
        display_ = EGL_NO_DISPLAY;
    }
}

bool Renderer::OnWindowInit(ANativeWindow* window) {
    window_ = window;

    if (display_ == EGL_NO_DISPLAY) {
        if (!InitEglContext()) return false;
    }

    EGLint format = 0;
    eglGetConfigAttrib(display_, config_, EGL_NATIVE_VISUAL_ID, &format);
    ANativeWindow_setBuffersGeometry(window_, 0, 0, format);

    if (surface_ != EGL_NO_SURFACE) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(display_, surface_);
        surface_ = EGL_NO_SURFACE;
    }

    surface_ = eglCreateWindowSurface(display_, config_, window_, nullptr);
    if (surface_ == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed (err=0x%x)", eglGetError());
        return false;
    }
    if (!eglMakeCurrent(display_, surface_, surface_, context_)) {
        LOGE("eglMakeCurrent failed (err=0x%x)", eglGetError());
        eglDestroySurface(display_, surface_);
        surface_ = EGL_NO_SURFACE;
        return false;
    }

    eglQuerySurface(display_, surface_, EGL_WIDTH, &width_);
    eglQuerySurface(display_, surface_, EGL_HEIGHT, &height_);
    glViewport(0, 0, width_, height_);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);

    if (!shadersReady_) { BuildShaderPrograms(); }
    EnsureWorldLoaded();

    if (!shadersReady_) {
        LOGE("Renderer initialization incomplete; keeping Java menu alive");
        return false;
    }

    LOGI("EGL surface ready: %dx%d, quality tier=%s, world loaded=%s", width_, height_,
         quality_.tier == GraphicsQuality::kLightweight ? "lightweight" :
         (quality_.tier == GraphicsQuality::kHigh ? "high" : "balanced"),
         world_.IsLoaded() ? "yes" : "no");
    return true;
}

void Renderer::EnsureWorldLoaded() {
    if (world_.IsLoaded() || assetManager_ == nullptr) return;
    if (!world_.LoadFromAssets(assetManager_, "world/terrain.mesh")) {
        LOGE("Terrain failed to load - rendering will show an empty world rather than crash");
    }
}

void Renderer::SpawnNewGame() {
    Spawn::Point p = Spawn::DefaultSpawn(world_);
    player_.Reset(p.x, p.y, p.z, p.yawDeg);
    LOGI("Spawned new game at (%.1f, %.1f, %.1f)", p.x, p.y, p.z);
}

void Renderer::OnWindowTerm() {
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface_ != EGL_NO_SURFACE) {
            eglDestroySurface(display_, surface_);
            surface_ = EGL_NO_SURFACE;
        }
    }
    window_ = nullptr;
}

GLuint Renderer::CompileShader(GLenum type, const char* src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        char log[512];
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        LOGE("Shader compile failed: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint Renderer::LinkProgram(GLuint vs, GLuint fs) {
    GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        char log[512];
        glGetProgramInfoLog(program, sizeof(log), nullptr, log);
        LOGE("Program link failed: %s", log);
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

void Renderer::BuildShaderPrograms() {
    GLuint vs = CompileShader(GL_VERTEX_SHADER, kVertexShaderSrc);
    GLuint fsLight = CompileShader(GL_FRAGMENT_SHADER, kFragmentShaderLightweightSrc);
    GLuint fsBalanced = CompileShader(GL_FRAGMENT_SHADER, kFragmentShaderBalancedSrc);

    programLightweight_ = LinkProgram(vs, fsLight);
    programBalanced_ = LinkProgram(vs, fsBalanced);

    glDeleteShader(vs);
    glDeleteShader(fsLight);
    glDeleteShader(fsBalanced);

    shadersReady_ = (programLightweight_ != 0 && programBalanced_ != 0);
}

void Renderer::DrawFrame(double elapsedSeconds, double dtSeconds) {
    if (surface_ == EGL_NO_SURFACE || !shadersReady_) return;

    player_.Update(static_cast<float>(dtSeconds), pendingInput_);
    // Look deltas are per-frame drag amounts, already consumed - clear them
    // so a held-but-unmoving finger doesn't keep spinning the camera.
    pendingInput_.lookDeltaYaw = 0.0f;
    pendingInput_.lookDeltaPitch = 0.0f;

    glClearColor(0.03f, 0.03f, 0.045f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    GLuint program = (quality_.tier == GraphicsQuality::kLightweight) ? programLightweight_ : programBalanced_;
    glUseProgram(program);

    Player::Vec3 eye = player_.EyePosition();
    float yawRad = player_.Yaw() * 3.14159265f / 180.0f;
    float pitchRad = player_.Pitch() * 3.14159265f / 180.0f;
    float lookX = eye.x + std::sin(yawRad) * std::cos(pitchRad);
    float lookY = eye.y + std::sin(pitchRad);
    float lookZ = eye.z - std::cos(yawRad) * std::cos(pitchRad);

    Mat4 model = Identity();
    Mat4 view = LookAt(eye.x, eye.y, eye.z, lookX, lookY, lookZ);
    float aspect = height_ > 0 ? static_cast<float>(width_) / height_ : 1.0f;
    Mat4 proj = Perspective(1.1f, aspect, 0.1f, 500.0f);
    Mat4 mvp = Multiply(Multiply(proj, view), model);

    GLint mvpLoc = glGetUniformLocation(program, "uMVP");
    GLint modelLoc = glGetUniformLocation(program, "uModel");
    GLint colorLoc = glGetUniformLocation(program, "uBaseColor");
    glUniformMatrix4fv(mvpLoc, 1, GL_FALSE, mvp.m);
    glUniformMatrix4fv(modelLoc, 1, GL_FALSE, model.m);
    // Muted earthy green-grey - placeholder until real terrain textures.
    glUniform3f(colorLoc, 0.32f, 0.36f, 0.26f);

    if (quality_.tier != GraphicsQuality::kLightweight) {
        float lightPos[4 * 3] = {
            20.0f, 40.0f, 20.0f,
            -20.0f, 25.0f, -15.0f,
            0, 0, 0, 0, 0, 0
        };
        float lightColor[4 * 3] = {
            1.0f, 0.95f, 0.85f,  // sun
            0.4f, 0.45f, 0.6f,   // sky fill
            0, 0, 0, 0, 0, 0
        };
        glUniform3fv(glGetUniformLocation(program, "uLightPositions"), 4, lightPos);
        glUniform3fv(glGetUniformLocation(program, "uLightColors"), 4, lightColor);
        glUniform1i(glGetUniformLocation(program, "uLightCount"), 2);
    }

    world_.Draw();

    if (!eglSwapBuffers(display_, surface_)) {
        EGLint err = eglGetError();
        LOGE("eglSwapBuffers failed (err=0x%x)", err);
    }
}
