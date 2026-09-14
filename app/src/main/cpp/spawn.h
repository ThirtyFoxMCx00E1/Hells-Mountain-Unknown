#ifndef HMU_SPAWN_H
#define HMU_SPAWN_H

#include "world.h"

// Computes where a new game starts the player. Kept separate from World and
// Player since "where do we put someone at the start of a new game" is a
// question about game design/level layout, not about how terrain renders
// or how the camera moves - those concerns will diverge further once there
// are multiple named spawn points, checkpoints, etc.
namespace Spawn {

struct Point {
    float x = 0, y = 0, z = 0;
    float yawDeg = 0;
};

// Default new-game spawn: horizontal center of the loaded terrain, at the
// terrain's actual surface height there (queried via World::HeightAt, not
// guessed), plus a small clearance so the player starts just above ground
// rather than exactly on it.
//
// Falls back to the origin if the world has no usable height data (e.g.
// terrain failed to load) - not a good spawn, but a safe, defined one
// rather than an arbitrary guess that might land outside the map.
Point DefaultSpawn(const World& world);

} // namespace Spawn

#endif // HMU_SPAWN_H
