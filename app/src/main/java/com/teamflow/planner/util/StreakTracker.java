package com.teamflow.planner.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Persists a simple "consecutive days with at least one completion" streak in SharedPreferences.
 */
public final class StreakTracker {

    private static final String PREFS = "teamflow_streak";
    private static final String KEY_STREAK = "streak";
    private static final String KEY_LAST_DAY = "last_streak_day";

    private StreakTracker() {
    }

    /**
     * Call when a task transitions to {@code COMPLETED}. First completion of a calendar day updates the streak.
     */
    public static void onTaskCompleted(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = dayKey(System.currentTimeMillis());
        String last = prefs.getString(KEY_LAST_DAY, null);
        int streak = prefs.getInt(KEY_STREAK, 0);

        if (today.equals(last)) {
            return;
        }

        if (last == null) {
            streak = 1;
        } else {
            String yesterday = dayKey(minusOneDay(System.currentTimeMillis()));
            if (yesterday.equals(last)) {
                streak = streak + 1;
            } else {
                streak = 1;
            }
        }

        prefs.edit().putInt(KEY_STREAK, streak).putString(KEY_LAST_DAY, today).apply();
    }

    public static int getCurrentStreak(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_STREAK, 0);
    }

    private static String dayKey(long millis) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getDefault());
        return fmt.format(new Date(millis));
    }

    private static long minusOneDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.add(Calendar.DAY_OF_YEAR, -1);
        return c.getTimeInMillis();
    }
}
