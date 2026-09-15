#ifndef UNSOLVEDCASE_QUALITY_SETTINGS_H
#define UNSOLVEDCASE_QUALITY_SETTINGS_H

// Four tiers now (was three) to match the Settings UI's Fast/Balanced/
// High/Ultra dropdown. Internal enum names kept from before Ultra existed;
// the UI is responsible for the user-facing "FAST" label on kLightweight.
enum class GraphicsQuality {
    kLightweight, // "FAST" in the UI - lowest GPU/CPU cost
    kBalanced,
    kHigh,
    kUltra        // extra lighting, shadows, and post-processing budget
};

struct QualitySettings {
    GraphicsQuality tier = GraphicsQuality::kBalanced;
    float resolutionScale = 1.0f;
    int maxDynamicLights = 2;
    bool enableShadows = false;
    bool enablePostProcess = false;

    // View distance in world units - drives the far clip plane. Settings
    // UI exposes this as a stepped 2/4/6/8/10/0 slider (0 = far/effectively
    // unbounded for this terrain's scale) rather than raw meters.
    float viewDistance = 300.0f;

    static QualitySettings ForTier(GraphicsQuality tier) {
        QualitySettings s;
        s.tier = tier;
        switch (tier) {
            case GraphicsQuality::kLightweight:
                s.resolutionScale = 0.70f;
                s.maxDynamicLights = 1;
                s.enableShadows = false;
                s.enablePostProcess = false;
                break;
            case GraphicsQuality::kBalanced:
                s.resolutionScale = 1.0f;
                s.maxDynamicLights = 2;
                s.enableShadows = false;
                s.enablePostProcess = false;
                break;
            case GraphicsQuality::kHigh:
                s.resolutionScale = 1.0f;
                s.maxDynamicLights = 4;
                s.enableShadows = true;
                s.enablePostProcess = true;
                break;
            case GraphicsQuality::kUltra:
                s.resolutionScale = 1.0f;
                s.maxDynamicLights = 4;
                s.enableShadows = true;
                s.enablePostProcess = true;
                break;
        }
        return s;
    }
};

#endif
