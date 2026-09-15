#ifndef HMU_SETTINGS_H
#define HMU_SETTINGS_H

#include "quality_settings.h"

// Runtime settings actually consumed by native rendering code. Persistence
// and the settings UI both live in Java (GameSettings.java) - MainActivity,
// the safe launcher, must never load native code, so it can't depend on
// anything here to display or edit settings. This struct only holds what
// gets pushed down from Java once gameplay actually starts (see
// nativeStartRun in main.cpp), and RuntimeSettings::Apply() is what turns
// those raw values into the QualitySettings the Renderer already knows
// how to use.
//
// Fields NOT here yet (bloom, depth of field, HDR, true dynamic resolution,
// vsync/frame-rate limiting, anti-aliasing quality) are stored on the Java
// side already, ready to be added here once the corresponding rendering
// techniques are actually implemented - deliberately not wiring in fields
// this layer can't yet act on.
struct RuntimeSettings {
    int qualityTier = 1;        // matches GameSettings.QUALITY_* on the Java side
    float viewDistance = 800.0f; // world units, see GameSettings.viewDistanceWorldUnits()

    static GraphicsQuality TierFromInt(int tier) {
        switch (tier) {
            case 0: return GraphicsQuality::kLightweight;
            case 2: return GraphicsQuality::kHigh;
            case 3: return GraphicsQuality::kUltra;
            default: return GraphicsQuality::kBalanced;
        }
    }
};

namespace RuntimeSettingsValidation {
float ClampViewDistance(float requested);
}

#endif // HMU_SETTINGS_H
