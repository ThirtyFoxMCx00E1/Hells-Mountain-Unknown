#include "world.h"

#include <android/log.h>
#include <android/asset_manager.h>
#include <cstdint>
#include <cstring>
#include <vector>

#define LOG_TAG "HellsMountainUnknown"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
// Matches the format written by the offline terrain-processing step:
//   magic "UCTM", uint32 version, uint32 vertexCount, uint32 indexCount,
//   then vertexCount * (px,py,pz,nx,ny,nz) as float32,
//   then indexCount * uint32.
struct Vertex { float px, py, pz, nx, ny, nz; };
}

World::~World() {
    if (vbo_) glDeleteBuffers(1, &vbo_);
    if (ibo_) glDeleteBuffers(1, &ibo_);
    if (vao_) glDeleteVertexArrays(1, &vao_);
}

bool World::LoadFromAssets(AAssetManager* assetManager, const char* path) {
    if (assetManager == nullptr) {
        LOGE("World::LoadFromAssets: null AAssetManager");
        return false;
    }

    AAsset* asset = AAssetManager_open(assetManager, path, AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        LOGE("World::LoadFromAssets: could not open %s", path);
        return false;
    }

    const off_t length = AAsset_getLength(asset);
    const void* buf = AAsset_getBuffer(asset);
    if (buf == nullptr || length < 16) {
        LOGE("World::LoadFromAssets: %s empty or unreadable", path);
        AAsset_close(asset);
        return false;
    }

    const uint8_t* p = static_cast<const uint8_t*>(buf);
    if (std::memcmp(p, "UCTM", 4) != 0) {
        LOGE("World::LoadFromAssets: %s bad magic header", path);
        AAsset_close(asset);
        return false;
    }
    p += 4;

    uint32_t version = 0, vertexCount = 0, indexCount = 0;
    std::memcpy(&version, p, 4); p += 4;
    std::memcpy(&vertexCount, p, 4); p += 4;
    std::memcpy(&indexCount, p, 4); p += 4;

    const size_t expectedSize =
        16 + static_cast<size_t>(vertexCount) * sizeof(Vertex) +
        static_cast<size_t>(indexCount) * sizeof(uint32_t);
    if (version != 1 || expectedSize != static_cast<size_t>(length)) {
        LOGE("World::LoadFromAssets: %s size mismatch (expected %zu, got %ld)",
             path, expectedSize, static_cast<long>(length));
        AAsset_close(asset);
        return false;
    }

    std::vector<Vertex> vertices(vertexCount);
    std::memcpy(vertices.data(), p, vertexCount * sizeof(Vertex));
    p += vertexCount * sizeof(Vertex);

    std::vector<uint32_t> indices(indexCount);
    std::memcpy(indices.data(), p, indexCount * sizeof(uint32_t));

    AAsset_close(asset);

    glGenVertexArrays(1, &vao_);
    glBindVertexArray(vao_);

    glGenBuffers(1, &vbo_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, vertices.size() * sizeof(Vertex), vertices.data(), GL_STATIC_DRAW);

    glGenBuffers(1, &ibo_);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo_);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(uint32_t), indices.data(), GL_STATIC_DRAW);

    // Matches the Renderer's existing shader attribute layout: position (0),
    // normal (1) - same as the placeholder cube, so both lightweight and
    // balanced programs work unmodified against terrain geometry too.
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, sizeof(Vertex), (void*)offsetof(Vertex, px));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, sizeof(Vertex), (void*)offsetof(Vertex, nx));

    glBindVertexArray(0);

    indexCount_ = static_cast<GLsizei>(indices.size());
    loaded_ = true;
    LOGI("World loaded: %u vertices, %u indices from %s", vertexCount, indexCount, path);
    return true;
}

void World::Draw() const {
    if (!loaded_) return;
    glBindVertexArray(vao_);
    glDrawElements(GL_TRIANGLES, indexCount_, GL_UNSIGNED_INT, nullptr);
    glBindVertexArray(0);
}
