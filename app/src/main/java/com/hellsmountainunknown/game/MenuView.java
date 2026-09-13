package com.hellsmountainunknown.game;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.media.MediaPlayer;
import java.io.IOException;
import java.io.InputStream;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Full-screen title/menu presentation.
 *
 * Sequence:
 *  1. Pure black.
 *  2. The case logo fades in.
 *  3. Logo holds briefly.
 *  4. Logo fades out.
 *  5. The landscape title menu fades in.
 *
 * The view is deliberately drawn with Canvas so the title screen has no
 * dependency on external image files or a game engine.
 */
public final class MenuView extends View {
    private static final long BLACK_MS = 650;
    private static final long FADE_IN_MS = 900;
    private static final long HOLD_MS = 800;
    private static final long FADE_OUT_MS = 950;
    private static final long MENU_FADE_MS = 850;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap logoBitmap;
    private Bitmap introLogoBitmap;
    private Movie menuMovie;
    private Bitmap menuFrameBitmap;
    private Canvas menuFrameCanvas;
    private long menuStartedAt = 0L;

    private long startedAt;
    private int selected = 0;
    private boolean menuVisible = false;
    private boolean panelVisible = false;
    private String panelTitle = "";
    private String status = "";
    private int graphicsTier = 1;
    private final String[] graphicsNames = {"FAST", "BALANCED", "HIGH"};

    // Main-menu music: one complete track with a gentle fade-in/fade-out
    // at each loop. The music is optional and never allowed to crash the app.
    private static final long MUSIC_FADE_IN_MS = 5000L;
    // The 8:51 track is 530.24 seconds long. Start the fade at 8:49,
    // so the final ~2 seconds fade smoothly to silence before the loop.
    private static final long MUSIC_FADE_OUT_MS = 2000L;
    private final Handler musicHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer musicPlayer;
    private boolean musicStarted = false;
    private boolean musicReleased = false;
    private final Runnable musicFadeOut = () -> fadeMusicTo(0f, MUSIC_FADE_OUT_MS);

    private final String[] buttons = {
            "CONTINUE", "SETTINGS", "NEW GAME", "QUIT"
    };
    private boolean continueEnabled = false;

    // New Game / Continue fade-to-black transition, then launch the game
    // activity. Fade-IN once inside GameEngineActivity is handled there
    // (over 10s per spec) - this view only owns the fade-OUT.
    private static final long LAUNCH_FADE_OUT_MS = 1200L;
    private long fadeOutStartedAt = -1;
    private boolean pendingNewGame = false;

    public MenuView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        continueEnabled = SaveGameSlots.anySlotExists(context);
        loadAssets();
        startedAt = SystemClock.uptimeMillis();
        setBackgroundColor(0xff000000);
    }

    private Bitmap loadBitmap(String... names) {
        for (String name : names) {
            try (InputStream in = getContext().getAssets().open(name)) {
                Options options = new Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
                if (bitmap != null) return bitmap;
            } catch (IOException | RuntimeException ignored) {
                // Try the next asset name. Art must never terminate the app.
            } catch (OutOfMemoryError ignored) {
                // On low-memory devices, skip optional art and use the
                // procedural fallback instead of killing the launcher.
                return null;
            }
        }
        return null;
    }

    private void loadAssets() {
        // Keep the intro artwork separate from the main-menu title artwork.
        // The intro uses the original case logo; the main menu uses the
        // Hells Mountain Unknown wordmark in logo_main.png.
        introLogoBitmap = loadBitmap("menu/intro_logo.png");
        logoBitmap = loadBitmap("menu/logo_main.png");
    }

    private void drawMainMenu(Canvas c, float w, float h, int a) {
        if (menuMovie == null) {
            try (InputStream in = getContext().getAssets().open("menu/bg_main.gif")) {
                menuMovie = Movie.decodeStream(in);
            } catch (IOException | RuntimeException ignored) {
                menuMovie = null;
            }
        }

        if (menuMovie != null) {
            int movieW = Math.max(1, menuMovie.width());
            int movieH = Math.max(1, menuMovie.height());

            // Movie.draw() does not reliably honor Canvas transforms on all
            // Android/OEM renderers. Render the current GIF frame to a small
            // bitmap first, then scale that bitmap into the entire view.
            try {
                if (menuFrameBitmap == null
                        || menuFrameBitmap.getWidth() != movieW
                        || menuFrameBitmap.getHeight() != movieH) {
                    if (menuFrameBitmap != null) menuFrameBitmap.recycle();
                    menuFrameBitmap = Bitmap.createBitmap(
                            movieW, movieH, Bitmap.Config.ARGB_8888);
                    menuFrameCanvas = new Canvas(menuFrameBitmap);
                }

                int duration = menuMovie.duration();
                int movieTime = duration > 0
                        ? (int) ((SystemClock.uptimeMillis() - menuStartedAt) % duration)
                        : 0;
                menuMovie.setTime(movieTime);

                menuFrameCanvas.drawColor(0xff000000);
                p.setAlpha(255);
                menuMovie.draw(menuFrameCanvas, 0f, 0f, p);

                // Center-crop to the actual content area: no black bars and
                // no small/native-size GIF sitting in a corner.
                float scale = Math.max(
                        w / (float) movieW,
                        h / (float) movieH);
                float drawW = movieW * scale;
                float drawH = movieH * scale;
                RectF dst = new RectF(
                        (w - drawW) * 0.5f,
                        (h - drawH) * 0.5f,
                        (w + drawW) * 0.5f,
                        (h + drawH) * 0.5f);

                p.setAlpha(a);
                c.drawBitmap(menuFrameBitmap, null, dst, p);
                p.setAlpha(255);
            } catch (Throwable ignored) {
                // Keep the menu usable if an OEM renderer cannot allocate
                // the temporary frame bitmap.
            }
        } else {
            c.drawColor((a << 24) | 0x00030507);

            // Procedural fallback only if the animated background cannot be decoded.
            p.setStyle(Paint.Style.FILL);
            p.setColor((a << 24) | 0x0010181b);
            c.drawRect(w * 0.55f, 0, w, h, p);
            p.setColor((a << 24) | 0x00201927);
            c.drawRect(w * 0.70f, h * 0.08f, w, h * 0.75f, p);
            p.setColor((a << 24) | 0x002c2029);
            c.drawRect(w * 0.78f, h * 0.15f, w * 0.94f, h * 0.72f, p);

            p.setColor((a << 24) | 0x0006090b);
            c.drawRect(w * 0.82f, h * 0.24f, w * 0.91f, h * 0.48f, p);
            c.drawRect(w * 0.60f, h * 0.30f, w * 0.68f, h * 0.54f, p);

            p.setColor((a << 24) | 0x004a0c12);
            c.drawOval(new RectF(-w * 0.12f, h * 0.02f, w * 0.42f, h * 0.75f), p);

            p.setColor((a << 24) | 0x00151a1e);
            c.drawRect(0, h * 0.66f, w, h, p);
            p.setColor((a << 24) | 0x00321a2b);
            c.drawOval(new RectF(w * 0.43f, h * 0.73f, w * 0.88f, h * 0.90f), p);
        }

        // No procedural rain overlay. The supplied bg_main.gif is the complete
        // animated main-menu background.

        // Hells Mountain Unknown logo stays near the top of the main menu.
        if (logoBitmap != null) {
            p.setAlpha(a);
            float logoW = Math.min(w * 0.34f, 360f);
            float logoH = logoW * logoBitmap.getHeight() / (float) Math.max(1, logoBitmap.getWidth());
            RectF logoDst = new RectF(w * 0.07f, h * 0.09f,
                    w * 0.07f + logoW, h * 0.09f + logoH);
            c.drawBitmap(logoBitmap, null, logoDst, p);
            p.setAlpha(255);
        } else {
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            p.setTextSize(Math.max(34f, h * 0.075f));
            p.setColor((a << 24) | 0x00d71925);
            p.setShadowLayer(16f, 0, 0, (a << 24) | 0x00a00000);
            c.drawText("HELLS MOUNTAIN", w * 0.08f, h * 0.17f, p);
            p.clearShadowLayer();
        }

        // Menu list.
        float startY = h * 0.43f;
        float gap = Math.max(48f, h * 0.085f);
        for (int i = 0; i < buttons.length; i++) {
            float y = startY + i * gap;
            boolean hot = i == selected;
            boolean disabled = (i == 0 && !continueEnabled);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,
                    hot ? Typeface.BOLD : Typeface.NORMAL));
            p.setTextSize(Math.max(20f, h * 0.045f));
            int textColor = disabled ? 0x00555a5e : (hot ? 0x00ed3038 : 0x00bfc0c4);
            p.setColor((a << 24) | textColor);
            if (hot) {
                c.drawRect(w * 0.075f, y - h * 0.045f, w * 0.085f, y + 5, p);
            }
            c.drawText(buttons[i], w * 0.10f, y, p);
        }

        // Bottom controller hint.
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        p.setTextSize(Math.max(14f, h * 0.027f));
        p.setColor((a << 24) | 0x007f8085);
        c.drawText("A  SELECT", w * 0.74f, h * 0.92f, p);
        c.drawText("B  BACK", w * 0.87f, h * 0.92f, p);
        p.setAlpha(255);
    }

    private void startMainMenuMusic() {
        if (musicStarted || musicReleased) return;
        musicStarted = true;

        try {
            AssetFileDescriptor afd = getContext().getAssets().openFd("menu/main_theme.mp3");
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            player.setOnPreparedListener(mp -> {
                if (musicReleased) {
                    mp.release();
                    return;
                }
                musicPlayer = mp;
                mp.setVolume(0f, 0f);
                mp.setOnCompletionListener(done -> {
                    if (musicReleased) return;
                    try {
                        done.setVolume(0f, 0f);
                        done.start();
                        fadeMusicTo(1f, MUSIC_FADE_IN_MS);
                        scheduleMusicFadeOut(done.getDuration());
                    } catch (Throwable ignored) {
                    }
                });
                try {
                    mp.start();
                    fadeMusicTo(1f, MUSIC_FADE_IN_MS);
                    scheduleMusicFadeOut(mp.getDuration());
                } catch (Throwable ignored) {
                    releaseMusic();
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                releaseMusic();
                return true;
            });
            player.prepareAsync();
        } catch (Throwable ignored) {
            // Audio is optional; the menu must remain usable if MediaPlayer
            // or the audio codec is unavailable on a particular device.
            musicStarted = true;
        }
    }

    private void scheduleMusicFadeOut(int durationMs) {
        musicHandler.removeCallbacks(musicFadeOut);
        long delay = Math.max(0L, (long) durationMs - MUSIC_FADE_OUT_MS);
        musicHandler.postDelayed(musicFadeOut, delay);
    }

    private void fadeMusicTo(float target, long durationMs) {
        final MediaPlayer player = musicPlayer;
        if (player == null || musicReleased) return;
        final float start = target > 0f ? 0f : 1f;
        final long begin = SystemClock.uptimeMillis();
        final long duration = Math.max(1L, durationMs);
        musicHandler.post(new Runnable() {
            @Override public void run() {
                if (musicReleased || musicPlayer != player) return;
                float t = Math.min(1f, (SystemClock.uptimeMillis() - begin) / (float) duration);
                float eased = t * t * (3f - 2f * t);
                float volume = start + (target - start) * eased;
                try {
                    player.setVolume(volume, volume);
                } catch (Throwable ignored) {
                    return;
                }
                if (t < 1f) {
                    musicHandler.postDelayed(this, 50L);
                }
            }
        });
    }

    /** Call from the host Activity's onPause() so music stops when backgrounded. */
    public void pauseMusic() {
        MediaPlayer player = musicPlayer;
        if (player != null && !musicReleased) {
            try {
                if (player.isPlaying()) player.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Call from the host Activity's onResume() so music continues when foregrounded. */
    public void resumeMusic() {
        MediaPlayer player = musicPlayer;
        if (player != null && !musicReleased) {
            try {
                if (!player.isPlaying()) player.start();
            } catch (Throwable ignored) {
            }
        }
    }

    private void releaseMusic() {
        musicHandler.removeCallbacksAndMessages(null);
        MediaPlayer player = musicPlayer;
        musicPlayer = null;
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Throwable ignored) {
            }
            try {
                player.release();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        musicReleased = true;
        releaseMusic();
        if (menuFrameBitmap != null) {
            try { menuFrameBitmap.recycle(); } catch (Throwable ignored) {}
            menuFrameBitmap = null;
            menuFrameCanvas = null;
        }
        super.onDetachedFromWindow();
    }

    private void drawPanel(Canvas c, float w, float h, float alpha) {
        int a = (int) (220f * clamp01(alpha));
        p.setColor((a << 24) | 0x00000000);
        c.drawRect(0, 0, w, h, p);

        p.setColor((a << 24) | 0x00090b0d);
        c.drawRoundRect(new RectF(w * 0.29f, h * 0.16f, w * 0.78f, h * 0.83f),
                8f, 8f, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor((a << 24) | 0x004c555a);
        c.drawRoundRect(new RectF(w * 0.29f, h * 0.16f, w * 0.78f, h * 0.83f),
                8f, 8f, p);
        p.setStyle(Paint.Style.FILL);

        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(h * 0.055f);
        p.setColor((a << 24) | 0x00d71925);
        c.drawText(panelTitle, w * 0.34f, h * 0.28f, p);

        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(h * 0.032f);
        p.setColor((a << 24) | 0x00b8b9bd);
        c.drawText("AUDIO", w * 0.34f, h * 0.40f, p);
        c.drawText("MUSIC          80%", w * 0.34f, h * 0.48f, p);
        c.drawText("EFFECTS        90%", w * 0.34f, h * 0.55f, p);
        c.drawText("GRAPHICS       " + graphicsNames[graphicsTier], w * 0.34f, h * 0.64f, p);
        c.drawText("TAP GRAPHICS TO CYCLE QUALITY", w * 0.34f, h * 0.69f, p);
        c.drawText("TOUCH / GAMEPAD   ON", w * 0.34f, h * 0.71f, p);
        c.drawText("TAP ANYWHERE TO RETURN", w * 0.34f, h * 0.78f, p);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        final float w = getWidth();
        final float h = getHeight();
        final long now = SystemClock.uptimeMillis();
        final long introTotal = BLACK_MS + FADE_IN_MS + HOLD_MS + FADE_OUT_MS;
        final long elapsed = now - startedAt;

        c.drawColor(Color.BLACK);

        if (elapsed < introTotal) {
            float alpha;
            if (elapsed < BLACK_MS) {
                alpha = 0f;
            } else if (elapsed < BLACK_MS + FADE_IN_MS) {
                alpha = (elapsed - BLACK_MS) / (float) FADE_IN_MS;
            } else if (elapsed < BLACK_MS + FADE_IN_MS + HOLD_MS) {
                alpha = 1f;
            } else {
                alpha = 1f - (elapsed - BLACK_MS - FADE_IN_MS - HOLD_MS)
                        / (float) FADE_OUT_MS;
            }

            if (introLogoBitmap != null && alpha > 0f) {
                p.setAlpha((int) (255f * clamp01(alpha)));
                float logoSize = Math.min(w, h) * 0.58f;
                float left = (w - logoSize) * 0.5f;
                float top = (h - logoSize) * 0.5f;
                c.drawBitmap(introLogoBitmap, null,
                        new RectF(left, top, left + logoSize, top + logoSize), p);
                p.setAlpha(255);
            }

            postInvalidateOnAnimation();
            return;
        }

        if (!menuVisible) {
            menuVisible = true;
            menuStartedAt = now;
            startMainMenuMusic();
        }

        float menuAlpha = clamp01((now - menuStartedAt) / (float) MENU_FADE_MS);
        int a = (int) (255f * menuAlpha);
        drawMainMenu(c, w, h, a);

        if (panelVisible) {
            drawPanel(c, w, h, menuAlpha);
        }

        if (fadeOutStartedAt != -1) {
            long fadeElapsed = now - fadeOutStartedAt;
            float fadeAlpha = clamp01(fadeElapsed / (float) LAUNCH_FADE_OUT_MS);
            p.setColor((int) (255f * fadeAlpha) << 24);
            c.drawRect(0, 0, w, h, p);
            if (fadeElapsed >= LAUNCH_FADE_OUT_MS) {
                boolean newGame = pendingNewGame;
                fadeOutStartedAt = -1;
                performLaunch(newGame);
                return;
            }
        }

        // Keep the GIF animation and menu fade advancing smoothly.
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }

        if (!menuVisible) return true;

        float w = getWidth();
        float h = getHeight();

        if (panelVisible) {
            // Allow the graphics line to be cycled without leaving the panel.
            if (e.getY() >= h * 0.59f && e.getY() <= h * 0.73f) {
                graphicsTier = (graphicsTier + 1) % 3;
                status = "GRAPHICS " + graphicsNames[graphicsTier];
                invalidate();
                return true;
            }
            panelVisible = false;
            invalidate();
            return true;
        }

        float startY = h * 0.43f;
        float gap = Math.max(48f, h * 0.085f);

        int hit = -1;
        for (int i = 0; i < buttons.length; i++) {
            float y = startY + i * gap;
            if (e.getX() >= w * 0.06f && e.getX() <= w * 0.60f
                    && e.getY() >= y - h * 0.065f
                    && e.getY() <= y + h * 0.035f) {
                hit = i;
                break;
            }
        }

        if (hit >= 0) {
            selected = hit;
            activateSelection();
        }
        invalidate();
        return true;
    }

    private void launchGame(boolean newGame) {
        if (fadeOutStartedAt != -1) return; // already transitioning
        pendingNewGame = newGame;
        fadeOutStartedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private void performLaunch(boolean newGame) {
        try {
            Intent intent = new Intent(getContext(), GameEngineActivity.class);
            intent.putExtra(GameEngineActivity.EXTRA_NEW_GAME, newGame);
            int slot = newGame ? 1 : SaveGameSlots.mostRecentSlot(getContext());
            if (slot < 1) slot = 1;
            intent.putExtra(GameEngineActivity.EXTRA_SLOT, slot);
            getContext().startActivity(intent);
        } catch (RuntimeException ex) {
            status = "GAME START FAILED";
            fadeOutStartedAt = -1;
        }
    }

    private void activateSelection() {
        switch (selected) {
            case 0:
                if (!continueEnabled) {
                    status = "NO SAVED GAME YET";
                    break;
                }
                status = "CONTINUE";
                launchGame(false);
                break;
            case 1:
                panelTitle = "SETTINGS";
                panelVisible = true;
                break;
            case 2:
                status = "NEW GAME";
                launchGame(true);
                break;
            case 3:
                ((Activity) getContext()).finishAffinity();
                break;
            default:
                break;
        }
    }

    public String getStatus() {
        return status;
    }
}
