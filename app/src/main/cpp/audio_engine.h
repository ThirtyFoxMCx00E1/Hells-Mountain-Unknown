#ifndef UNSOLVEDCASE_AUDIO_ENGINE_H
#define UNSOLVEDCASE_AUDIO_ENGINE_H

// Audio is intentionally optional in the safe startup build.
// Keeping this interface lets gameplay audio be added later without making
// the title/menu depend on an external native audio runtime.
class AudioEngine {
public:
    bool Start() { return false; }
    void Stop() {}
};

#endif
