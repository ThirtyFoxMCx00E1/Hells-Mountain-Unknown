#include "world.h"

#include <android/log.h>
#include <android/asset_manager.h>
#include <algorithm>
#include <cmath>
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

    // Recover the height grid for HeightAt() queries. The terrain exporter
    // writes vertices in row-major (row * gridSize + col) order over a
    // square grid, so a perfect-square vertex count is what to expect here;
    // if it isn't (e.g. a future non-heightmap mesh gets loaded through
    // this same path), just skip height data rather than guess wrong.
    int gridSize = static_cast<int>(std::lround(std::sqrt(static_cast<double>(vertexCount))));
    if (static_cast<uint32_t>(gridSize * gridSize) == vertexCount && gridSize > 1) {
        gridSize_ = gridSize;
        heightGrid_.resize(vertexCount);
        minX_ = maxX_ = vertices[0].px;
        minZ_ = maxZ_ = vertices[0].pz;
        for (uint32_t i = 0; i < vertexCount; i++) {
            heightGrid_[i] = vertices[i].py;
            minX_ = std::min(minX_, vertices[i].px);
            maxX_ = std::max(maxX_, vertices[i].px);
            minZ_ = std::min(minZ_, vertices[i].pz);
            maxZ_ = std::max(maxZ_, vertices[i].pz);
        }
    } else {
        LOGE("World::LoadFromAssets: vertex count %u is not a perfect square; "
             "HeightAt() queries will be unavailable", vertexCount);
    }

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

float World::HeightAt(float worldX, float worldZ) const {
    if (gridSize_ <= 0 || heightGrid_.empty()) return 0.0f;

    float u = (maxX_ > minX_) ? (worldX - minX_) / (maxX_ - minX_) : 0.0f;
    float v = (maxZ_ > minZ_) ? (worldZ - minZ_) / (maxZ_ - minZ_) : 0.0f;
    u = std::max(0.0f, std::min(1.0f, u));
    v = std::max(0.0f, std::min(1.0f, v));

    float gx = u * (gridSize_ - 1);
    float gz = v * (gridSize_ - 1);
    int x0 = static_cast<int>(gx);
    int z0 = static_cast<int>(gz);
    int x1 = std::min(x0 + 1, gridSize_ - 1);
    int z1 = std::min(z0 + 1, gridSize_ - 1);
    float fx = gx - x0;
    float fz = gz - z0;

    auto sample = [&](int row, int col) { return heightGrid_[row * gridSize_ + col]; };
    float h00 = sample(z0, x0), h10 = sample(z0, x1);
    float h01 = sample(z1, x0), h11 = sample(z1, x1);
    float h0 = h00 + (h10 - h00) * fx;
    float h1 = h01 + (h11 - h01) * fx;
    return h0 + (h1 - h0) * fz;
}
