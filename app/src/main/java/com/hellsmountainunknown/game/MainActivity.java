package com.hellsmountainunknown.game;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Launcher only.  This activity NEVER loads the native game library.
 *
 * Keeping the first frame as a plain Android view is intentional: if a
 * renderer/native dependency has a problem, Android can still show the
 * launcher instead of immediately closing the process.
 */
public class MainActivity extends android.app.Activity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MenuView menuView;
    private SettingsView settingsView;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences crashPrefs = getSharedPreferences("crash_log", MODE_PRIVATE);
        String lastCrash = crashPrefs.getString("last_crash", null);
        if (lastCrash != null) {
            crashPrefs.edit().remove("last_crash").apply();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Last crash")
                    .setMessage(lastCrash)
                    .setPositiveButton("OK", null)
                    .show();
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setBackgroundDrawableResource(android.R.color.black);

        // First frame: absolutely minimal Android UI.
        // Do not construct MenuView until the Activity has reached RESUMED.
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        // hideSystemUi() must come AFTER setContentView(). Calling it earlier
        // (before the DecorView exists) threw a NullPointerException on this
        // device's ROM - see PhoneWindow.getInsetsController() in the crash
        // log. This was the actual cause of "keeps stopping" all along.
        hideSystemUi();

        // Notification permission is the only runtime permission requested.
        // VIBRATE is a normal permission and does not require a runtime dialog.
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            mainHandler.postDelayed(() -> {
                if (!isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed())) {
                    try {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 304);
                    } catch (Throwable ignored) {
                        // Permission UI must never prevent the launcher from opening.
                    }
                }
            }, 900);
        }

        mainHandler.post(this::startStudioIntro);

    }

    /**
     * Studio intro sequence, plays once before the existing logo+menu intro:
     * black -> video fades in (7s) -> video fades to black (6s) -> menu
     * fades in (3s). The video clip itself is only ~3s so IntroVideoView
     * loops it to fill the longer fade timeline around it.
     *
     * Timing is a direct implementation of what was requested; if any of
     * the three durations feel off once you see it running, they're each
     * a single number below - easy to adjust.
     */
    private static final long INTRO_FADE_IN_MS = 7000L;
    private static final long INTRO_FADE_OFF_MS = 6000L;
    private static final long INTRO_TO_MENU_FADE_MS = 3000L;

    private void startStudioIntro() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;

        try {
            IntroVideoView videoView = new IntroVideoView(this, null);
            root.addView(videoView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            View blackOverlay = new View(this);
            blackOverlay.setBackgroundColor(0xff000000);
            blackOverlay.setAlpha(1f);
            root.addView(blackOverlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            blackOverlay.animate()
                    .alpha(0f)
                    .setDuration(INTRO_FADE_IN_MS)
                    .withEndAction(() -> {
                        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                        blackOverlay.animate()
                                .alpha(1f)
                                .setDuration(INTRO_FADE_OFF_MS)
                                .withEndAction(() -> finishStudioIntro(videoView, blackOverlay))
                                .start();
                    })
                    .start();
        } catch (Throwable t) {
            // Studio intro is a nice-to-have, never worth blocking the
            // launcher over - skip straight to the main menu if it fails.
            android.util.Log.e("HellsMountainUnknown", "Studio intro failed, skipping", t);
            showMainMenu();
        }
    }

    private void finishStudioIntro(IntroVideoView videoView, View blackOverlay) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;

        try {
            videoView.release();
            root.removeView(videoView);
        } catch (Throwable ignored) {
        }

        try {
            menuView = new MenuView(this);
            menuView.setListener(this::openSettings);
            // Insert below the still-opaque black overlay so the overlay
            // fading out is what reveals the menu (and its own existing
            // logo intro underneath).
            root.addView(menuView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            root.setBackgroundColor(Color.BLACK);
        }

        blackOverlay.animate()
                .alpha(0f)
                .setDuration(INTRO_TO_MENU_FADE_MS)
                .withEndAction(() -> {
                    try { root.removeView(blackOverlay); } catch (Throwable ignored) {}
                })
                .start();
    }

    private void showMainMenu() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
            return;
        }

        try {
            root.removeAllViews();
            menuView = new MenuView(this);
            menuView.setListener(this::openSettings);
            root.addView(menuView,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            // Never let an optional menu/art failure kill the launcher.
            // Keep the already-visible black Android frame alive.
            root.setBackgroundColor(Color.BLACK);
        }
    }

    @Override
    protected void onPause() {
        if (menuView != null) {
            try { menuView.pauseMusic(); } catch (Throwable ignored) {}
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (menuView != null) {
            try { menuView.resumeMusic(); } catch (Throwable ignored) {}
        }
    }

    private void openSettings() {
        if (root == null || settingsView != null) return;
        settingsView = new SettingsView(this, menuView, () -> closeSettings());
        root.addView(settingsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeSettings() {
        if (settingsView == null || root == null) return;
        settingsView.onClosing();
        root.removeView(settingsView);
        settingsView = null;
    }

    @Override
    public void onBackPressed() {
        if (settingsView != null) {
            closeSettings();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        try {
            Window window = getWindow();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) {
            // Some OEM ROMs (confirmed on this device via bug report) can
            // throw here if called too early or in edge-case window states.
            // Hiding system bars is cosmetic - never worth crashing over.
        }
    }
}
