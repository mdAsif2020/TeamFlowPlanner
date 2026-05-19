package com.teamflow.planner.sync;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.teamflow.planner.supabase.SupabaseService;

import java.util.concurrent.TimeUnit;

/**
 * Schedules periodic cloud sync when Supabase is configured.
 */
public final class SyncScheduler {

    private static final String PERIODIC_NAME = "teamflow_sync_periodic";

    private SyncScheduler() {
    }

    public static void schedulePeriodic(Context context) {
        if (!SupabaseService.isConfigured()) {
            return;
        }
        Constraints cons = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                15,
                TimeUnit.MINUTES)
                .setConstraints(cons)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req);
    }
}
