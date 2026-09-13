package com.hellsmountainunknown.game;

import java.io.File;

/**
 * Save-slot constants and a lightweight existence check shared between
 * MainActivity (to enable/disable Continue) and the in-game pause menu.
 *
 * Deliberately does NOT touch native code or parse save file contents -
 * MainActivity is the safe launcher and must never load the native
 * library, so this only checks whether a slot's file exists on disk. The
 * actual save/load (reading player position etc.) happens natively in
 * savegame.cpp once gameplay is running. Keep SAVE_DIR/fileFor() in sync
 * with savegame.cpp's path convention if either changes.
 */
public final class SaveGameSlots {
    public static final int SLOT_COUNT = 7;
    private static final String SAVE_DIR = "saves";

    private SaveGameSlots() {}

    private static File fileFor(android.content.Context context, int slot) {
        return new File(new File(context.getFilesDir(), SAVE_DIR), "slot" + slot + ".sav");
    }

    public static boolean slotExists(android.content.Context context, int slot) {
        return fileFor(context, slot).isFile();
    }

    /** True if ANY of the 7 slots has save data - used to enable Continue. */
    public static boolean anySlotExists(android.content.Context context) {
        for (int i = 1; i <= SLOT_COUNT; i++) {
            if (slotExists(context, i)) return true;
        }
        return false;
    }

    /** Most recently modified slot with save data, or -1 if none. */
    public static int mostRecentSlot(android.content.Context context) {
        int best = -1;
        long bestTime = -1;
        for (int i = 1; i <= SLOT_COUNT; i++) {
            File f = fileFor(context, i);
            if (f.isFile() && f.lastModified() > bestTime) {
                bestTime = f.lastModified();
                best = i;
            }
        }
        return best;
    }
}
