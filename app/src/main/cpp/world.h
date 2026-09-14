#ifndef HMU_WORLD_H
#define HMU_WORLD_H

#include <GLES3/gl3.h>
#include <android/asset_manager.h>
#include <vector>

// Owns the game world's static geometry. Currently just the terrain, loaded
// from a compact custom binary format (see tools/process_terrain.py-style
// preprocessing - the source .stl was 78MB/1.6M triangles, far too dense
// for real-time mobile rendering, so it's baked down to a lightweight
// indexed grid mesh ahead of time and shipped as an asset instead).
class World {
public:
    ~World();

    // Loads app/src/main/assets/world/terrain.mesh and uploads it to GPU
    // buffers. Safe to call once; returns false (and leaves the world
    // empty/undrawable) if the asset is missing or malformed - a missing
    // terrain must never crash the app, just render nothing.
    bool LoadFromAssets(AAssetManager* assetManager, const char* path);

    // Draws the terrain using whatever shader program is currently bound
    // (caller is responsible for glUseProgram + setting uniforms first -
    // World shares the Renderer's existing lightweight/balanced programs
    // rather than owning its own, since the vertex format matches).
    void Draw() const;

    bool IsLoaded() const { return loaded_; }

    // Bilinear-interpolated terrain height at a given (worldX, worldZ).
    // Returns 0 if the world isn't loaded or the grid couldn't be
    // recovered from the mesh - callers should treat that as "unknown
    // ground, don't trust this for spawning" rather than a real height.
    float HeightAt(float worldX, float worldZ) const;
    bool HasHeightData() const { return gridSize_ > 0; }

    float MinX() const { return minX_; }
    float MaxX() const { return maxX_; }
    float MinZ() const { return minZ_; }
    float MaxZ() const { return maxZ_; }

private:
    GLuint vbo_ = 0;
    GLuint ibo_ = 0;
    GLuint vao_ = 0;
    GLsizei indexCount_ = 0;
    bool loaded_ = false;

    // Retained CPU-side copy of the height grid for HeightAt() queries -
    // small (a few KB at typical terrain resolution) and only kept once,
    // separate from the GPU-uploaded copy used for rendering.
    std::vector<float> heightGrid_;
    int gridSize_ = 0;  // grid is gridSize_ x gridSize_; 0 if unknown/unavailable
    float minX_ = 0, maxX_ = 0, minZ_ = 0, maxZ_ = 0;
};

#endif // HMU_WORLD_H
