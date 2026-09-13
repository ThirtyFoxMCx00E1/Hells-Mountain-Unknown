#include "player.h"

#include <cmath>

namespace {
constexpr float kPi = 3.14159265358979323846f;
float DegToRad(float deg) { return deg * kPi / 180.0f; }
}

void Player::Reset(float x, float y, float z, float yawDeg) {
    position_ = {x, y, z};
    yawDeg_ = yawDeg;
    pitchDeg_ = 0.0f;
    distanceWalked_ = 0.0f;
}

void Player::Update(float dtSeconds, const Input& input) {
    // Look: screen-drag deltas are already in pixels; scale to degrees.
    yawDeg_ += input.lookDeltaYaw * kLookSensitivity;
    pitchDeg_ -= input.lookDeltaPitch * kLookSensitivity;
    if (pitchDeg_ > kMaxPitch) pitchDeg_ = kMaxPitch;
    if (pitchDeg_ < -kMaxPitch) pitchDeg_ = -kMaxPitch;

    // Movement: forward/strafe are relative to yaw (not pitch - players
    // don't fly by looking up on foot).
    float yawRad = DegToRad(yawDeg_);
    float forwardX = std::sin(yawRad);
    float forwardZ = -std::cos(yawRad);
    float rightX = std::cos(yawRad);
    float rightZ = std::sin(yawRad);

    float moveX = (forwardX * input.moveForward + rightX * input.moveStrafe);
    float moveZ = (forwardZ * input.moveForward + rightZ * input.moveStrafe);
    float moveLen = std::sqrt(moveX * moveX + moveZ * moveZ);
    if (moveLen > 1.0f) {
        moveX /= moveLen;
        moveZ /= moveLen;
        moveLen = 1.0f;
    }

    float step = moveLen * kMoveSpeed * dtSeconds;
    position_.x += moveX * kMoveSpeed * dtSeconds;
    position_.z += moveZ * kMoveSpeed * dtSeconds;
    distanceWalked_ += step;
}

Player::Vec3 Player::EyePosition() const {
    Vec3 eye = position_;
    eye.y += kEyeHeight;

    if (bobEnabled_ && distanceWalked_ > 0.0f) {
        float phase = distanceWalked_ * kBobFrequency * 2.0f * kPi;
        eye.y += std::sin(phase) * kBobAmplitudeY;
        // Horizontal sway at half frequency reads more naturally than
        // matching the vertical bob exactly - mirrors how real footstep
        // weight-shift works (side-to-side is slower than up-down).
        eye.x += std::cos(phase * 0.5f) * kBobAmplitudeX;
    }
    return eye;
}
