package com.hellsmountainunknown.game;

import android.app.Application;

/** Minimal application startup: no third-party native crash/loading libraries. */
public final class UnsolvedCaseApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
    }
}
