#ifndef UNSOLVEDCASE_QUALITY_SETTINGS_H
#define UNSOLVEDCASE_QUALITY_SETTINGS_H

enum class GraphicsQuality {
    kLightweight, // low-end: lowest GPU/CPU cost
    kBalanced,    // mid-range: normal presentation
    kHigh         // high-end: extra lighting/post processing
};

struct QualitySettings {
    GraphicsQuality tier = GraphicsQuality::kBalanced;
    float resolutionScale = 1.0f;
    int maxDynamicLights = 2;
    bool enableShadows = false;
    bool enablePostProcess = false;

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
        }
        return s;
    }
};

#endif
