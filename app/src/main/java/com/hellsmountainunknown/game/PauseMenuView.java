package com.hellsmountainunknown.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Simple canvas-drawn pause overlay shown over the game surface. Three
 * top-level options (Save Game, Options, Exit); Save Game opens a 7-slot
 * picker. The back button (wired in GameEngineActivity) closes whichever
 * panel is open, or resumes the game if none is - standard mobile pause
 * menu behavior, so there's no separate "Resume" button to tap.
 */
public final class PauseMenuView extends View {

    public interface Listener {
        void onSaveSlotChosen(int slot);
        void onExitToMenu();
        void onResumeGame();
    }

    private static final String[] TOP_BUTTONS = {"SAVE GAME", "OPTIONS", "EXIT"};
    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean showingSaveSlots = false;
    private int graphicsTier = 1;
    private final String[] graphicsNames = {"FAST", "BALANCED", "HIGH"};

    public PauseMenuView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setClickable(true);
        setFocusable(true);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();

        p.setColor(0xd0000000);
        c.drawRect(0, 0, w, h, p);

        p.setColor(0xff0a0c0e);
        RectF panel = new RectF(w * 0.30f, h * 0.14f, w * 0.70f, h * 0.86f);
        c.drawRoundRect(panel, 10f, 10f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xff4c555a);
        c.drawRoundRect(panel, 10f, 10f, p);
        p.setStyle(Paint.Style.FILL);

        if (showingSaveSlots) {
            drawSaveSlots(c, w, h);
        } else {
            drawTopMenu(c, w, h);
        }
    }

    private void drawTopMenu(Canvas c, float w, float h) {
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(h * 0.05f);
        p.setColor(0xffd71925);
        c.drawText("PAUSED", w * 0.36f, h * 0.24f, p);

        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        p.setTextSize(h * 0.04f);
        float startY = h * 0.40f;
        float gap = h * 0.13f;
        for (int i = 0; i < TOP_BUTTONS.length; i++) {
            p.setColor(0xffbfc0c4);
            c.drawText(TOP_BUTTONS[i], w * 0.36f, startY + i * gap, p);
        }

        p.setTextSize(h * 0.028f);
        p.setColor(0xff7f8085);
        c.drawText("BACK BUTTON: RESUME", w * 0.36f, h * 0.80f, p);
    }

    private void drawSaveSlots(Canvas c, float w, float h) {
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(h * 0.045f);
        p.setColor(0xffd71925);
        c.drawText("SAVE GAME - CHOOSE SLOT", w * 0.33f, h * 0.22f, p);

        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(h * 0.035f);
        float startY = h * 0.34f;
        float gap = h * 0.07f;
        for (int i = 0; i < SaveGameSlots.SLOT_COUNT; i++) {
            p.setColor(0xffbfc0c4);
            c.drawText("SLOT " + (i + 1), w * 0.36f, startY + i * gap, p);
        }

        p.setTextSize(h * 0.028f);
        p.setColor(0xff7f8085);
        c.drawText("BACK BUTTON: CANCEL", w * 0.36f, h * 0.80f, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth();
        float h = getHeight();

        if (showingSaveSlots) {
            float startY = h * 0.34f;
            float gap = h * 0.07f;
            for (int i = 0; i < SaveGameSlots.SLOT_COUNT; i++) {
                float y = startY + i * gap;
                if (e.getY() >= y - h * 0.05f && e.getY() <= y + h * 0.02f) {
                    listener.onSaveSlotChosen(i + 1);
                    return true;
                }
            }
            return true;
        }

        float startY = h * 0.40f;
        float gap = h * 0.13f;
        for (int i = 0; i < TOP_BUTTONS.length; i++) {
            float y = startY + i * gap;
            if (e.getY() >= y - h * 0.06f && e.getY() <= y + h * 0.02f) {
                switch (i) {
                    case 0: // SAVE GAME
                        showingSaveSlots = true;
                        invalidate();
                        return true;
                    case 1: // OPTIONS
                        graphicsTier = (graphicsTier + 1) % 3;
                        invalidate();
                        return true;
                    case 2: // EXIT
                        listener.onExitToMenu();
                        return true;
                    default:
                        break;
                }
            }
        }
        return true;
    }

    /** Called by GameEngineActivity's back-button handler. */
    public boolean handleBack() {
        if (showingSaveSlots) {
            showingSaveSlots = false;
            invalidate();
            return true; // consumed - stay open on the top menu
        }
        return false; // let the caller close/resume
    }
}
