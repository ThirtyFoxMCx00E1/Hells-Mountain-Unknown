#include "savegame.h"

#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <sys/stat.h>

#define LOG_TAG "HellsMountainUnknown"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace SaveGame {
namespace {

constexpr char kMagic[4] = {'U', 'C', 'S', 'V'};
constexpr uint32_t kVersion = 1;

struct FileLayout {
    char magic[4];
    uint32_t version;
    PlayerState state;
};

bool BuildPath(const char* filesDir, int slot, char* outPath, size_t outSize) {
    if (filesDir == nullptr || slot < 1 || slot > kSlotCount) return false;
    std::snprintf(outPath, outSize, "%s/saves", filesDir);
    // Best-effort directory creation - if it already exists, mkdir fails
    // harmlessly and we proceed; if the whole path is unwritable, the
    // subsequent fopen() below will fail cleanly instead.
    mkdir(outPath, 0771);
    std::snprintf(outPath, outSize, "%s/saves/slot%d.sav", filesDir, slot);
    return true;
}

} // namespace

bool LoadSlot(const char* filesDir, int slot, PlayerState* outState) {
    char path[512];
    if (!BuildPath(filesDir, slot, path, sizeof(path)) || outState == nullptr) return false;

    FILE* f = std::fopen(path, "rb");
    if (f == nullptr) return false;

    FileLayout layout{};
    size_t read = std::fread(&layout, 1, sizeof(layout), f);
    std::fclose(f);

    if (read != sizeof(layout) || std::memcmp(layout.magic, kMagic, 4) != 0 ||
        layout.version != kVersion) {
        LOGE("SaveGame::LoadSlot: slot %d corrupt or wrong version, ignoring", slot);
        return false;
    }

    *outState = layout.state;
    return true;
}

bool SaveSlot(const char* filesDir, int slot, const PlayerState& state) {
    char path[512];
    if (!BuildPath(filesDir, slot, path, sizeof(path))) return false;

    FileLayout layout{};
    std::memcpy(layout.magic, kMagic, 4);
    layout.version = kVersion;
    layout.state = state;

    // Write to a temp file then rename, so a crash/power-loss mid-write
    // can never leave a half-written (and therefore corrupt) save file
    // sitting in the real slot path.
    char tmpPath[520];
    std::snprintf(tmpPath, sizeof(tmpPath), "%s.tmp", path);

    FILE* f = std::fopen(tmpPath, "wb");
    if (f == nullptr) {
        LOGE("SaveGame::SaveSlot: could not open %s for write", tmpPath);
        return false;
    }
    size_t written = std::fwrite(&layout, 1, sizeof(layout), f);
    std::fclose(f);

    if (written != sizeof(layout)) {
        LOGE("SaveGame::SaveSlot: short write for slot %d", slot);
        std::remove(tmpPath);
        return false;
    }

    if (std::rename(tmpPath, path) != 0) {
        LOGE("SaveGame::SaveSlot: rename failed for slot %d", slot);
        std::remove(tmpPath);
        return false;
    }
    return true;
}

bool SlotExists(const char* filesDir, int slot) {
    char path[512];
    if (!BuildPath(filesDir, slot, path, sizeof(path))) return false;
    struct stat st{};
    return stat(path, &st) == 0;
}

} // namespace SaveGame
