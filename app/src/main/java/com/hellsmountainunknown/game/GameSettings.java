package com.hellsmountainunknown.game;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * All player-configurable settings, backed by SharedPreferences so both the
 * safe launcher (MainActivity - never loads native code) and gameplay
 * (GameEngineActivity) can read/write them without either depending on the
 * other's lifecycle.
 *
 * Quality tier and view distance are the two values that actually change
 * what's rendered right now (see renderer.cpp / quality_settings.h) - they
 * get pushed into native code via nativeStartRun(). Everything else here
 * (bloom, depth of field, HDR, true dynamic resolution, MSAA quality,
 * frame-rate limiting, vsync) is stored and will apply once the
 * corresponding rendering work exists, but doesn't change output yet -
 * flagged clearly in code and in the UI's field descriptions rather than
 * silently pretending they're doing something they aren't.
 */
public final class GameSettings {
    private static final String PREFS = "game_settings";

    // Video
    public static final int WINDOW_FULLSCREEN = 0, WINDOW_WINDOWED = 1, WINDOW_BORDERLESS = 2;
    public static final int QUALITY_FAST = 0, QUALITY_BALANCED = 1, QUALITY_HIGH = 2, QUALITY_ULTRA = 3;
    // Displayed values for view distance / anti-aliasing sliders: index 0..4
    // map to 2/4/6/8/10, index 5 is the "0" position representing unlimited.
    public static final int[] STEP_VALUES = {2, 4, 6, 8, 10, 0};

    private GameSettings() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int windowMode(Context c) { return prefs(c).getInt("window_mode", WINDOW_FULLSCREEN); }
    public static void setWindowMode(Context c, int v) { prefs(c).edit().putInt("window_mode", v).apply(); }

    public static int frameLimit(Context c) { return prefs(c).getInt("frame_limit", 144); } // 144 = unlimited
    public static void setFrameLimit(Context c, int v) { prefs(c).edit().putInt("frame_limit", v).apply(); }

    public static boolean vsync(Context c) { return prefs(c).getBoolean("vsync", false); }
    public static void setVsync(Context c, boolean v) { prefs(c).edit().putBoolean("vsync", v).apply(); }

    public static boolean dynamicResolution(Context c) { return prefs(c).getBoolean("dynamic_resolution", false); }
    public static void setDynamicResolution(Context c, boolean v) { prefs(c).edit().putBoolean("dynamic_resolution", v).apply(); }

    public static float resolutionScale(Context c) { return prefs(c).getFloat("resolution_scale", 1.0f); }
    public static void setResolutionScale(Context c, float v) { prefs(c).edit().putFloat("resolution_scale", v).apply(); }

    public static boolean hdr(Context c) { return prefs(c).getBoolean("hdr", false); }
    public static void setHdr(Context c, boolean v) { prefs(c).edit().putBoolean("hdr", v).apply(); }

    public static boolean bloom(Context c) { return prefs(c).getBoolean("bloom", false); }
    public static void setBloom(Context c, boolean v) { prefs(c).edit().putBoolean("bloom", v).apply(); }

    public static boolean depthOfField(Context c) { return prefs(c).getBoolean("depth_of_field", false); }
    public static void setDepthOfField(Context c, boolean v) { prefs(c).edit().putBoolean("depth_of_field", v).apply(); }

    /** -1 = not yet chosen by the player; caller should auto-detect and save a default. */
    public static int qualityTier(Context c) { return prefs(c).getInt("quality_tier", -1); }
    public static void setQualityTier(Context c, int v) { prefs(c).edit().putInt("quality_tier", v).apply(); }

    public static int viewDistanceIndex(Context c) { return prefs(c).getInt("view_distance_index", 3); } // default "8"
    public static void setViewDistanceIndex(Context c, int v) { prefs(c).edit().putInt("view_distance_index", v).apply(); }

    public static int antiAliasingIndex(Context c) { return prefs(c).getInt("anti_aliasing_index", 2); } // default "6"
    public static void setAntiAliasingIndex(Context c, int v) { prefs(c).edit().putInt("anti_aliasing_index", v).apply(); }

    // Control
    public static boolean controllerExperimental(Context c) { return prefs(c).getBoolean("controller_experimental", false); }
    public static void setControllerExperimental(Context c, boolean v) { prefs(c).edit().putBoolean("controller_experimental", v).apply(); }

    // Audio
    public static int musicVolume(Context c) { return prefs(c).getInt("music_volume", 80); }
    public static void setMusicVolume(Context c, int v) { prefs(c).edit().putInt("music_volume", v).apply(); }

    public static int effectsVolume(Context c) { return prefs(c).getInt("effects_volume", 90); }
    public static void setEffectsVolume(Context c, int v) { prefs(c).edit().putInt("effects_volume", v).apply(); }

    // Gameplay
    public static boolean firstPersonEnabled(Context c) { return prefs(c).getBoolean("first_person", true); }
    public static void setFirstPersonEnabled(Context c, boolean v) { prefs(c).edit().putBoolean("first_person", v).apply(); }

    public static boolean thirdPersonEnabled(Context c) { return prefs(c).getBoolean("third_person", false); }
    public static void setThirdPersonEnabled(Context c, boolean v) { prefs(c).edit().putBoolean("third_person", v).apply(); }

    /** Converts a view-distance/AA step index (0-5) to its displayed value (2/4/6/8/10/0). */
    public static int stepIndexToDisplayValue(int index) {
        if (index < 0 || index >= STEP_VALUES.length) return STEP_VALUES[0];
        return STEP_VALUES[index];
    }

    /** World-unit far clip distance for a given view-distance step index. Index 5 ("0" / unlimited) maps to a large but finite distance appropriate for this terrain's ~900-unit scale. */
    public static float viewDistanceWorldUnits(int index) {
        int display = stepIndexToDisplayValue(index);
        return display == 0 ? 2000f : display * 100f;
    }

    public static void resetToDefaults(Context c) {
        prefs(c).edit().clear().apply();
    }
}
