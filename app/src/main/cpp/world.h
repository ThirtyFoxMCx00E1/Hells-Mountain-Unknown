#ifndef HMU_WORLD_H
#define HMU_WORLD_H

#include <GLES3/gl3.h>
#include <android/asset_manager.h>

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

private:
    GLuint vbo_ = 0;
    GLuint ibo_ = 0;
    GLuint vao_ = 0;
    GLsizei indexCount_ = 0;
    bool loaded_ = false;
};

#endif // HMU_WORLD_H
