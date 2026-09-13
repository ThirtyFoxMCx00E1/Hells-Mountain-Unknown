#ifndef HMU_SAVEGAME_H
#define HMU_SAVEGAME_H

#include <cstdint>

// Simple 7-slot save system. Each slot is one small fixed-size binary file
// under <filesDir>/saves/slotN.sav. Kept deliberately simple (a flat struct,
// no versioned schema yet) since there's no real gameplay state beyond
// player position/orientation so far - this will need to grow once there
// are actual game-progress fields to persist.
//
// Note: whether a save slot EXISTS is also checked directly from Java
// (MainActivity, to enable/disable the Continue button) using plain
// File.exists() against this same directory/naming convention - that check
// deliberately does NOT go through native code, since the launcher must
// never load the native library. Keep the "saves/slotN.sav" path and
// naming in sync with MainActivity.java's SAVE_DIR/SAVE_PREFIX constants
// if either changes.
namespace SaveGame {

constexpr int kSlotCount = 7;

struct PlayerState {
    float x = 0, y = 0, z = 0;
    float yawDeg = 0;
};

// Returns false if the slot has no save data or the file is unreadable/
// corrupt. Never throws - a bad save file should be treated as "no save",
// not crash the loader.
bool LoadSlot(const char* filesDir, int slot, PlayerState* outState);

// Returns false on write failure (e.g. disk full). Overwrites any existing
// data in that slot ("replace my game" - saving always replaces the slot's
// prior contents, there's no separate confirm-overwrite step at this layer).
bool SaveSlot(const char* filesDir, int slot, const PlayerState& state);

bool SlotExists(const char* filesDir, int slot);

} // namespace SaveGame

#endif // HMU_SAVEGAME_H
