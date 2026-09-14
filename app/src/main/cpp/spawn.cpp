#include "spawn.h"

namespace Spawn {

namespace {
constexpr float kGroundClearance = 0.15f;  // meters above terrain surface
}

Point DefaultSpawn(const World& world) {
    Point p;
    if (!world.HasHeightData()) {
        // No usable terrain - safe defined fallback rather than a guess
        // that could place the player outside the map (this is exactly
        // the bug that caused a spawn far off the actual terrain bounds
        // before this system existed).
        return p;
    }

    p.x = (world.MinX() + world.MaxX()) * 0.5f;
    p.z = (world.MinZ() + world.MaxZ()) * 0.5f;
    p.y = world.HeightAt(p.x, p.z) + kGroundClearance;
    p.yawDeg = 0.0f;
    return p;
}

} // namespace Spawn
