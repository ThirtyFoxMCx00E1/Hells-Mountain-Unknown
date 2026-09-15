package com.hellsmountainunknown.game;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

/**
 * Full settings screen: Video / Control / Audio / Gameplay tabs, matching
 * the structure of the reference (Unreal-style) settings menu. Built on
 * Canvas/Paint rather than a UI middleware library (ImGui/RmlUi) - those
 * are the right tools for this kind of interface, but integrating either
 * into this project's OpenGL ES pipeline is a substantial task on its own,
 * separate from actually building the settings screen itself.
 *
 * IMPORTANT HONESTY NOTE (also surfaced in each affected row's description
 * text so it's visible in the UI itself, not just in code): Bloom, Depth
 * of Field, HDR, true Dynamic Resolution, and Anti-Aliasing quality are
 * stored here and will take effect once the corresponding rendering
 * techniques are implemented, but none of those exist in the renderer yet
 * - toggling them currently changes nothing you'll see. Graphics Quality
 * and View Render Distance ARE fully wired to the renderer already.
 *
 * Values apply immediately as you interact (no staged "pending changes" -
 * kept simple intentionally). RESET DEFAULT clears everything back to
 * defaults right away; APPLY and BACK both just close the screen.
 */
public final class SettingsView extends View {

    public interface Listener {
        void onClose();
    }

    private enum Tab { VIDEO, CONTROL, AUDIO, GAMEPLAY }
    private enum RowType { HEADER, DROPDOWN, SLIDER, TOGGLE, INFO }

    private interface IntGet { int get(); }
    private interface IntSet { void set(int v); }
    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }

    private final class Row {
        RowType type;
        String label;
        String description = "";
        // Dropdown
        String[] options;
        IntGet dropdownGet;
        IntSet dropdownSet;
        // Slider (integer range, inclusive)
        int sliderMin, sliderMax;
        IntGet sliderGet;
        IntSet sliderSet;
        boolean sliderIsStepIndex; // true for view-distance/AA: display via GameSettings.STEP_VALUES
        boolean unlimitedAtMax;    // true for frame limit: max value displays "UNLIMITED"
        // Toggle
        BoolGet toggleGet;
        BoolSet toggleSet;

        float top, bottom; // computed per-frame layout, in view pixels
    }

    private final Context ctx;
    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Tab activeTab = Tab.VIDEO;
    private final Tab[] tabs = Tab.values();
    private Row[] currentRows;
    private Row draggingSlider = null;
    private View blurTarget;

    private float popAnim = 0f; // 0..1, pop-in progress

    public SettingsView(Context context, View blurTarget, Listener listener) {
        super(context);
        this.ctx = context;
        this.blurTarget = blurTarget;
        this.listener = listener;
        setClickable(true);
        setFocusable(true);
        p.setTypeface(Typeface.MONOSPACE);
        rebuildRows();
        startPopAnimation();
        applyBlur(true);
    }

    private void startPopAnimation() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(220);
        anim.addUpdateListener(a -> {
            popAnim = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private void applyBlur(boolean enable) {
        if (blurTarget == null || Build.VERSION.SDK_INT < 31) return;
        try {
            if (enable) {
                blurTarget.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP));
            } else {
                blurTarget.setRenderEffect(null);
            }
        } catch (Throwable ignored) {
            // Blur is a visual nicety - never worth crashing over.
        }
    }

    /** Call when removing this view from its parent. */
    public void onClosing() {
        applyBlur(false);
    }

    private void rebuildRows() {
        switch (activeTab) {
            case VIDEO: currentRows = buildVideoRows(); break;
            case CONTROL: currentRows = buildControlRows(); break;
            case AUDIO: currentRows = buildAudioRows(); break;
            case GAMEPLAY: currentRows = buildGameplayRows(); break;
        }
    }

    private Row header(String label) {
        Row r = new Row();
        r.type = RowType.HEADER;
        r.label = label;
        return r;
    }

    private Row dropdown(String label, String desc, String[] options, IntGet get, IntSet set) {
        Row r = new Row();
        r.type = RowType.DROPDOWN;
        r.label = label; r.description = desc; r.options = options;
        r.dropdownGet = get; r.dropdownSet = set;
        return r;
    }

    private Row slider(String label, String desc, int min, int max, IntGet get, IntSet set) {
        Row r = new Row();
        r.type = RowType.SLIDER;
        r.label = label; r.description = desc;
        r.sliderMin = min; r.sliderMax = max;
        r.sliderGet = get; r.sliderSet = set;
        return r;
    }

    private Row toggle(String label, String desc, BoolGet get, BoolSet set) {
        Row r = new Row();
        r.type = RowType.TOGGLE;
        r.label = label; r.description = desc;
        r.toggleGet = get; r.toggleSet = set;
        return r;
    }

    private Row[] buildVideoRows() {
        final String NOT_YET = " (stored - not yet rendered; no matching effect exists in the renderer yet)";
        Row frameLimit = slider("FRAME LIMIT", "Caps rendering rate. Stored - frame pacing/limiting isn't implemented yet.",
                0, 144, () -> GameSettings.frameLimit(ctx), v -> GameSettings.setFrameLimit(ctx, v));
        frameLimit.unlimitedAtMax = true;

        Row viewDist = slider("VIEW RENDER DISTANCE", "How far terrain renders before being clipped. Fully active.",
                0, 5, () -> GameSettings.viewDistanceIndex(ctx), v -> GameSettings.setViewDistanceIndex(ctx, v));
        viewDist.sliderIsStepIndex = true;

        Row aa = slider("ANTI ALIASING QUALITY", "Edge smoothing." + NOT_YET,
                0, 5, () -> GameSettings.antiAliasingIndex(ctx), v -> GameSettings.setAntiAliasingIndex(ctx, v));
        aa.sliderIsStepIndex = true;

        return new Row[] {
            header("DISPLAY"),
            dropdown("WINDOW MODE", "Android is always fullscreen - stored for parity with the reference layout only.",
                    new String[]{"FULLSCREEN", "WINDOWED", "BORDERLESS"},
                    () -> GameSettings.windowMode(ctx), v -> GameSettings.setWindowMode(ctx, v)),
            frameLimit,
            toggle("VERTICAL SYNC", "Stored - not yet applied to the render loop.",
                    () -> GameSettings.vsync(ctx), v -> GameSettings.setVsync(ctx, v)),
            toggle("DYNAMIC RESOLUTION", "Stored - not yet applied; resolution scale below is a fixed value for now.",
                    () -> GameSettings.dynamicResolution(ctx), v -> GameSettings.setDynamicResolution(ctx, v)),
            slider("RESOLUTION SCALE", "Stored." + NOT_YET, 50, 100,
                    () -> Math.round(GameSettings.resolutionScale(ctx) * 100f),
                    v -> GameSettings.setResolutionScale(ctx, v / 100f)),
            toggle("HDR", "Stored." + NOT_YET, () -> GameSettings.hdr(ctx), v -> GameSettings.setHdr(ctx, v)),
            toggle("BLOOM", "Stored." + NOT_YET, () -> GameSettings.bloom(ctx), v -> GameSettings.setBloom(ctx, v)),
            toggle("DEPTH OF FIELD", "Stored." + NOT_YET,
                    () -> GameSettings.depthOfField(ctx), v -> GameSettings.setDepthOfField(ctx, v)),
            header("GRAPHICS"),
            dropdown("GRAPHICS QUALITY", "Lighting, shadows and resolution scale. Fully active.",
                    new String[]{"FAST", "BALANCED", "HIGH", "ULTRA"},
                    () -> { int t = GameSettings.qualityTier(ctx); return t < 0 ? GameSettings.QUALITY_BALANCED : t; },
                    v -> GameSettings.setQualityTier(ctx, v)),
            viewDist,
            aa,
        };
    }

    private Row[] buildControlRows() {
        return new Row[] {
            header("MOBILE CONTROLS"),
            toggle("CUSTOM TOUCH LAYOUT", "Placeholder for repositionable on-screen controls - full customization editor isn't built yet.",
                    () -> GameSettings.controllerExperimental(ctx), v -> GameSettings.setControllerExperimental(ctx, v)),
            header("CONTROLLER (EXPERIMENTAL)"),
            toggle("CONTROLLER SUPPORT", "Early/experimental - full gamepad mapping comes in a later pass.",
                    () -> GameSettings.controllerExperimental(ctx), v -> GameSettings.setControllerExperimental(ctx, v)),
        };
    }

    private Row[] buildAudioRows() {
        return new Row[] {
            header("VOLUME"),
            slider("MUSIC VOLUME", "Main menu and gameplay music.", 0, 100,
                    () -> GameSettings.musicVolume(ctx), v -> GameSettings.setMusicVolume(ctx, v)),
            slider("EFFECTS VOLUME", "Sound effects.", 0, 100,
                    () -> GameSettings.effectsVolume(ctx), v -> GameSettings.setEffectsVolume(ctx, v)),
        };
    }

    private Row[] buildGameplayRows() {
        return new Row[] {
            header("CAMERA"),
            toggle("FIRST PERSON MODEL", "Play from a first-person view.",
                    () -> GameSettings.firstPersonEnabled(ctx), v -> GameSettings.setFirstPersonEnabled(ctx, v)),
            toggle("THIRD PERSON MODEL", "Play from a third-person view. No third-person player model exists yet.",
                    () -> GameSettings.thirdPersonEnabled(ctx), v -> GameSettings.setThirdPersonEnabled(ctx, v)),
        };
    }

    private String stepDisplay(int index) {
        int v = GameSettings.stepIndexToDisplayValue(index);
        return v == 0 ? "UNLIMITED" : String.valueOf(v);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float scale = 0.92f + 0.08f * popAnim;
        int alpha = (int) (255 * popAnim);

        c.save();
        c.scale(scale, scale, w / 2f, h / 2f);

        p.setColor(0xb0000000 & (alpha << 24 | 0x00ffffff));
        c.drawRect(0, 0, w, h, p);

        RectF panel = new RectF(w * 0.06f, h * 0.08f, w * 0.94f, h * 0.92f);
        p.setColor((alpha << 24) | 0x000b0d0f);
        c.drawRoundRect(panel, 12f, 12f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor((alpha << 24) | 0x00454d52);
        c.drawRoundRect(panel, 12f, 12f, p);
        p.setStyle(Paint.Style.FILL);

        drawTabs(c, panel, alpha);
        drawRows(c, panel, alpha);
        drawFooterButtons(c, panel, alpha);

        c.restore();

        if (popAnim < 1f) postInvalidateOnAnimation();
    }

    private RectF tabRect(RectF panel, int index) {
        float tabW = panel.width() / tabs.length;
        float x0 = panel.left + index * tabW;
        return new RectF(x0 + 8, panel.top + 10, x0 + tabW - 8, panel.top + panel.height() * 0.09f);
    }

    private void drawTabs(Canvas c, RectF panel, int alpha) {
        for (int i = 0; i < tabs.length; i++) {
            RectF r = tabRect(panel, i);
            boolean active = tabs[i] == activeTab;
            p.setTextSize(r.height() * 0.5f);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, active ? Typeface.BOLD : Typeface.NORMAL));
            p.setColor((alpha << 24) | (active ? 0x00e8e9ec : 0x007f8085));
            c.drawText(tabs[i].name(), r.left, r.bottom - r.height() * 0.25f, p);
            if (active) {
                p.setColor((alpha << 24) | 0x00d71925);
                c.drawRect(r.left, r.bottom, r.left + r.width() * 0.6f, r.bottom + 3f, p);
            }
        }
    }

    private void drawRows(Canvas c, RectF panel, int alpha) {
        float rowH = panel.height() * 0.085f;
        float y = panel.top + panel.height() * 0.16f;
        float leftX = panel.left + panel.width() * 0.04f;
        float valueX = panel.left + panel.width() * 0.58f;
        float rowW = panel.width() * 0.60f;

        for (Row row : currentRows) {
            row.top = y;
            row.bottom = y + rowH;

            if (row.type == RowType.HEADER) {
                p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                p.setTextSize(rowH * 0.42f);
                p.setColor((alpha << 24) | 0x00d71925);
                c.drawText(row.label, leftX, y + rowH * 0.55f, p);
                y += rowH * 0.85f;
                continue;
            }

            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
            p.setTextSize(rowH * 0.34f);
            p.setColor((alpha << 24) | 0x00bfc0c4);
            c.drawText(row.label, leftX, y + rowH * 0.62f, p);

            switch (row.type) {
                case DROPDOWN: {
                    int idx = row.dropdownGet.get();
                    String val = row.options[Math.max(0, Math.min(row.options.length - 1, idx))];
                    p.setColor((alpha << 24) | 0x00ffffff);
                    c.drawText("<  " + val + "  >", valueX, y + rowH * 0.62f, p);
                    break;
                }
                case TOGGLE: {
                    boolean on = row.toggleGet.get();
                    p.setColor((alpha << 24) | (on ? 0x0043c463 : 0x00707478));
                    c.drawText(on ? "ON" : "OFF", valueX, y + rowH * 0.62f, p);
                    break;
                }
                case SLIDER: {
                    drawSliderTrack(c, row, valueX, rowW * 0.7f, y + rowH * 0.5f, alpha);
                    break;
                }
                default:
                    break;
            }
            y += rowH;
        }
    }

    private void drawSliderTrack(Canvas c, Row row, float x, float trackW, float centerY, int alpha) {
        int value = row.sliderGet.get();
        float t = row.sliderMax > row.sliderMin
                ? (value - row.sliderMin) / (float) (row.sliderMax - row.sliderMin) : 0f;

        p.setStrokeWidth(4f);
        p.setColor((alpha << 24) | 0x00454d52);
        c.drawLine(x, centerY, x + trackW, centerY, p);
        p.setColor((alpha << 24) | 0x00d71925);
        c.drawLine(x, centerY, x + trackW * t, centerY, p);
        c.drawCircle(x + trackW * t, centerY, 10f, p);

        String display;
        if (row.sliderIsStepIndex) {
            display = stepDisplay(value);
        } else if (row.unlimitedAtMax && value >= row.sliderMax) {
            display = "UNLIMITED";
        } else {
            display = String.valueOf(value);
        }
        p.setColor((alpha << 24) | 0x00ffffff);
        p.setTextSize(getHeight() * 0.028f);
        c.drawText(display, x + trackW + 24f, centerY + 10f, p);
    }

    private void drawFooterButtons(Canvas c, RectF panel, int alpha) {
        float btnH = panel.height() * 0.07f;
        float btnY = panel.bottom - btnH - 16f;
        float gap = 16f;
        float btnW = (panel.width() - 4 * gap) / 3f;

        String[] labels = {"BACK", "APPLY", "RESET DEFAULT"};
        for (int i = 0; i < 3; i++) {
            float x = panel.left + gap + i * (btnW + gap);
            RectF btn = new RectF(x, btnY, x + btnW, btnY + btnH);
            p.setColor((alpha << 24) | 0x00202428);
            c.drawRoundRect(btn, 8f, 8f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor((alpha << 24) | 0x004c555a);
            c.drawRoundRect(btn, 8f, 8f, p);
            p.setStyle(Paint.Style.FILL);

            p.setTextSize(btnH * 0.34f);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            p.setColor((alpha << 24) | 0x00e8e9ec);
            float textW = p.measureText(labels[i]);
            c.drawText(labels[i], btn.centerX() - textW / 2f, btn.centerY() + btnH * 0.12f, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth();
        float h = getHeight();
        RectF panel = new RectF(w * 0.06f, h * 0.08f, w * 0.94f, h * 0.92f);

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            for (Row row : currentRows) {
                if (row.type == RowType.SLIDER && e.getY() >= row.top && e.getY() <= row.bottom) {
                    draggingSlider = row;
                    updateSliderFromTouch(row, panel, e.getX());
                    invalidate();
                    return true;
                }
            }
        } else if (e.getAction() == MotionEvent.ACTION_MOVE && draggingSlider != null) {
            updateSliderFromTouch(draggingSlider, panel, e.getX());
            invalidate();
            return true;
        } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            if (draggingSlider != null) {
                draggingSlider = null;
                return true;
            }
            handleTap(e.getX(), e.getY(), panel);
            return true;
        }
        return true;
    }

    private void updateSliderFromTouch(Row row, RectF panel, float touchX) {
        float valueX = panel.left + panel.width() * 0.58f;
        float trackW = panel.width() * 0.60f * 0.7f;
        float t = (touchX - valueX) / trackW;
        t = Math.max(0f, Math.min(1f, t));
        int value = row.sliderMin + Math.round(t * (row.sliderMax - row.sliderMin));
        row.sliderSet.set(value);
    }

    private void handleTap(float x, float y, RectF panel) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabRect(panel, i).contains(x, y)) {
                activeTab = tabs[i];
                rebuildRows();
                invalidate();
                return;
            }
        }

        for (Row row : currentRows) {
            if (y < row.top || y > row.bottom) continue;
            if (row.type == RowType.DROPDOWN) {
                int idx = row.dropdownGet.get();
                idx = (idx + 1) % row.options.length;
                row.dropdownSet.set(idx);
                invalidate();
            } else if (row.type == RowType.TOGGLE) {
                row.toggleSet.set(!row.toggleGet.get());
                invalidate();
            }
        }

        float btnH = panel.height() * 0.07f;
        float btnY = panel.bottom - btnH - 16f;
        float gap = 16f;
        float btnW = (panel.width() - 4 * gap) / 3f;
        for (int i = 0; i < 3; i++) {
            float bx = panel.left + gap + i * (btnW + gap);
            RectF btn = new RectF(bx, btnY, bx + btnW, btnY + btnH);
            if (btn.contains(x, y)) {
                if (i == 2) {
                    GameSettings.resetToDefaults(ctx);
                    rebuildRows();
                    invalidate();
                } else {
                    if (listener != null) listener.onClose();
                }
                return;
            }
        }
    }
}
