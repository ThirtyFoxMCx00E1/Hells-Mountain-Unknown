#include "settings.h"

#include <algorithm>

namespace {
// Defensive bounds - a corrupt or unexpected value from the Java side
// (SharedPreferences surviving an app update with a changed range, for
// instance) should degrade to something reasonable, never propagate into
// the renderer as a nonsensical or negative distance.
constexpr float kMinViewDistance = 50.0f;
constexpr float kMaxViewDistance = 5000.0f;
}

// Currently the only thing worth validating here is view distance; quality
// tier is already a small enum-backed int with a safe default case in
// RuntimeSettings::TierFromInt(). As more fields move into this struct
// (once bloom/DOF/HDR etc. have real rendering implementations to drive),
// their validation belongs here too rather than scattered at call sites.
namespace RuntimeSettingsValidation {

float ClampViewDistance(float requested) {
    return std::max(kMinViewDistance, std::min(kMaxViewDistance, requested));
}

} // namespace RuntimeSettingsValidation
