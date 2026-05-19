package com.teamflow.planner.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Persists user theme choice (Material DayNight).
 */
public final class NightModeHelper {

    private static final String PREFS = "teamflow_prefs";
    private static final String KEY_MODE = "night_mode";

    private NightModeHelper() {
    }

    public static void applyFromPrefs(Context context) {
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static void saveAndApply(Context context, int appCompatNightMode) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MODE, appCompatNightMode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(appCompatNightMode);
    }

    public static int getSavedMode(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
