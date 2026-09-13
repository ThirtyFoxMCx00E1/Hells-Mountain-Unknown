#ifndef HMU_PLAYER_H
#define HMU_PLAYER_H

// First-person player state: position, look direction, movement, and the
// camera head-bob effect. No player model/mesh yet - this only drives the
// camera, matching what a first-person view needs before any visible
// character exists.
//
// Head-bob approach: track total horizontal distance walked, then offset
// the eye position with a sine wave keyed to that distance (not to wall
// time) so the bob frequency naturally scales with movement speed and
// stops the instant the player stops, rather than continuing to oscillate
// while standing still. This is the same underlying technique used by
// most first-person controllers (Quake onward) - a distance-phased sine
// wave, just implemented directly rather than ported from any specific
// engine's scripting API.
class Player {
public:
    struct Vec3 { float x = 0, y = 0, z = 0; };

    // Per-frame input, gathered by the platform layer (touch joystick +
    // look-drag on Android) and fed in every Update() call. Values are
    // already normalized: move in [-1,1] per axis, look in raw accumulated
    // screen-drag pixels since the last frame.
    struct Input {
        float moveForward = 0.0f;  // -1 back .. +1 forward
        float moveStrafe = 0.0f;   // -1 left .. +1 right
        float lookDeltaYaw = 0.0f;
        float lookDeltaPitch = 0.0f;
    };

    void Reset(float x, float y, float z, float yawDeg = 0.0f);

    void Update(float dtSeconds, const Input& input);

    // Eye position INCLUDING head-bob offset - use this for the view matrix.
    Vec3 EyePosition() const;

    // Raw feet/body position, EXCLUDING head-bob - use this for save games,
    // collision, and anything that shouldn't jitter with the bob wave.
    Vec3 BodyPosition() const { return position_; }

    float Yaw() const { return yawDeg_; }
    float Pitch() const { return pitchDeg_; }

    void SetBobEnabled(bool enabled) { bobEnabled_ = enabled; }

private:
    Vec3 position_;
    float yawDeg_ = 0.0f;
    float pitchDeg_ = 0.0f;

    static constexpr float kEyeHeight = 1.65f;      // meters, roughly human eye height
    static constexpr float kMoveSpeed = 3.2f;       // meters/second, walking pace
    static constexpr float kLookSensitivity = 0.15f; // degrees per screen pixel of drag
    static constexpr float kMaxPitch = 85.0f;
    static constexpr float kBobFrequency = 1.8f;     // wave cycles per meter walked
    static constexpr float kBobAmplitudeY = 0.045f;  // vertical bob, meters
    static constexpr float kBobAmplitudeX = 0.025f;  // horizontal sway, meters

    float distanceWalked_ = 0.0f;
    bool bobEnabled_ = true;
};

#endif // HMU_PLAYER_H
