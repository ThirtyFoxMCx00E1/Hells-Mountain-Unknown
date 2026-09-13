package com.hellsmountainunknown.game;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Hosts the native renderer/gameplay loop via a plain SurfaceView + JNI. */
public final class GameEngineActivity extends Activity
        implements SurfaceHolder.Callback {

    public static final String EXTRA_NEW_GAME = "new_game";
    public static final String EXTRA_SLOT = "slot";

    private SurfaceView surfaceView;
    private FrameLayout root;
    private View fadeInOverlay;
    private boolean fadeInStarted;
    private PauseMenuView pauseMenuView;
    private boolean nativeLoaded;
    private boolean stopping;
    private boolean started;

    // Two-zone touch scheme: left half of the screen is a virtual joystick
    // for movement, right half is a look-drag. Standard mobile FPS layout.
    private int moveTouchId = -1;
    private float moveOriginX, moveOriginY;
    private int lookTouchId = -1;
    private float lookLastX, lookLastY;
    private static final float JOYSTICK_RADIUS_PX = 140f;

    private static native void nativeSetQuality(int tier);
    private static native void nativeStartRun(AssetManager assetManager, String filesDir,
                                               boolean isNewGame, int slot);
    private static native void nativeSetSurface(Surface surface);
    private static native void nativeStop();
    private static native void nativeSetMoveAxis(float forward, float strafe);
    private static native void nativeAddLookDelta(float dx, float dy);
    private static native void nativeRequestSave(int slot);

    private boolean loadNativeSafely() {
        if (nativeLoaded) return true;
        try {
            System.loadLibrary("unsolvedcase");
            nativeLoaded = true;
            return true;
        } catch (Throwable t) {
            android.util.Log.e("HellsMountainUnknown", "Native load failed", t);
            return false;
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        if (!loadNativeSafely()) {
            TextView error = new TextView(this);
            error.setText("GAME ENGINE UNAVAILABLE\n\nNative libraries could not be loaded.");
            error.setTextColor(0xffdddddd);
            error.setGravity(17);
            error.setBackgroundColor(0xff050607);
            setContentView(error);
            return;
        }

        boolean isNewGame = getIntent().getBooleanExtra(EXTRA_NEW_GAME, true);
        int slot = getIntent().getIntExtra(EXTRA_SLOT, 1);

        try { nativeSetQuality(detectQualityTier()); } catch (Throwable t) {
            android.util.Log.e("HellsMountainUnknown", "Quality setup failed", t);
        }

        try {
            nativeStartRun(getAssets(), getFilesDir().getAbsolutePath(), isNewGame, slot);
            started = true;
        } catch (Throwable t) {
            android.util.Log.e("HellsMountainUnknown", "nativeStartRun failed", t);
        }

        root = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setFocusable(true);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Fade-in from black over 10s as the world becomes visible, matching
        // the fade-out already played in the main menu. Starts once the
        // surface actually exists rather than immediately, so it doesn't
        // burn part of its 10s while the surface is still being created.
        fadeInOverlay = new View(this);
        fadeInOverlay.setBackgroundColor(0xff000000);
        root.addView(fadeInOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private int detectQualityTier() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        long ramGb = 0;
        if (am != null) {
            am.getMemoryInfo(memory);
            ramGb = memory.totalMem / (1024L * 1024L * 1024L);
        }
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores <= 4 || ramGb <= 3) return 0;
        if (cores >= 8 && ramGb >= 6) return 2;
        return 1;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (!nativeLoaded || stopping) return;
        try { nativeSetSurface(holder.getSurface()); }
        catch (Throwable t) { android.util.Log.e("HellsMountainUnknown", "Surface init failed", t); }
        startFadeInOnce();
    }

    private void startFadeInOnce() {
        if (fadeInStarted || fadeInOverlay == null) return;
        fadeInStarted = true;
        fadeInOverlay.animate()
                .alpha(0f)
                .setDuration(10000L)
                .withEndAction(() -> {
                    if (root != null && fadeInOverlay != null) {
                        root.removeView(fadeInOverlay);
                        fadeInOverlay = null;
                    }
                })
                .start();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!nativeLoaded || stopping) return;
        try { nativeSetSurface(holder.getSurface()); } catch (Throwable ignored) {}
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (!nativeLoaded || stopping) return;
        try { nativeSetSurface(null); } catch (Throwable ignored) {}
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pauseMenuView != null) return super.onTouchEvent(event);
        if (!started) return super.onTouchEvent(event);

        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float halfWidth = surfaceView.getWidth() / 2f;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = event.getX(pointerIndex);
                float y = event.getY(pointerIndex);
                if (x < halfWidth && moveTouchId == -1) {
                    moveTouchId = pointerId;
                    moveOriginX = x;
                    moveOriginY = y;
                } else if (x >= halfWidth && lookTouchId == -1) {
                    lookTouchId = pointerId;
                    lookLastX = x;
                    lookLastY = y;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    if (id == moveTouchId) {
                        float dx = event.getX(i) - moveOriginX;
                        float dy = event.getY(i) - moveOriginY;
                        float len = (float) Math.sqrt(dx * dx + dy * dy);
                        float clamped = Math.min(len, JOYSTICK_RADIUS_PX);
                        float nx = len > 0.001f ? (dx / len) * (clamped / JOYSTICK_RADIUS_PX) : 0f;
                        float ny = len > 0.001f ? (dy / len) * (clamped / JOYSTICK_RADIUS_PX) : 0f;
                        // Screen up (-dy) is forward.
                        try { nativeSetMoveAxis(-ny, nx); } catch (Throwable ignored) {}
                    } else if (id == lookTouchId) {
                        float x = event.getX(i);
                        float y = event.getY(i);
                        float dx = x - lookLastX;
                        float dy = y - lookLastY;
                        lookLastX = x;
                        lookLastY = y;
                        try { nativeAddLookDelta(dx, dy); } catch (Throwable ignored) {}
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (pointerId == moveTouchId) {
                    moveTouchId = -1;
                    try { nativeSetMoveAxis(0f, 0f); } catch (Throwable ignored) {}
                }
                if (pointerId == lookTouchId) {
                    lookTouchId = -1;
                }
                break;
            }
            default:
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (pauseMenuView != null) {
            if (pauseMenuView.handleBack()) return; // consumed - e.g. closed a sub-panel
            closePauseMenu();
            return;
        }
        openPauseMenu();
    }

    private void openPauseMenu() {
        if (root == null || pauseMenuView != null) return;
        try { nativeSetMoveAxis(0f, 0f); } catch (Throwable ignored) {}
        pauseMenuView = new PauseMenuView(this, new PauseMenuView.Listener() {
            @Override public void onSaveSlotChosen(int slot) {
                try { nativeRequestSave(slot); } catch (Throwable ignored) {}
                closePauseMenu();
            }
            @Override public void onExitToMenu() {
                finish();
            }
            @Override public void onResumeGame() {
                closePauseMenu();
            }
        });
        root.addView(pauseMenuView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closePauseMenu() {
        if (pauseMenuView == null || root == null) return;
        root.removeView(pauseMenuView);
        pauseMenuView = null;
    }

    @Override protected void onDestroy() {
        stopping = true;
        if (nativeLoaded) {
            try { nativeSetSurface(null); } catch (Throwable ignored) {}
            try { nativeStop(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                        | WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
