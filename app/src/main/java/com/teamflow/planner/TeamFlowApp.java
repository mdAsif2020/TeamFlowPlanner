package com.teamflow.planner;

import android.app.Application;
import com.teamflow.planner.util.NightModeHelper;
import com.teamflow.planner.sync.NetworkMonitor;
import com.teamflow.planner.sync.SyncScheduler;

/**
 * Application entry: connectivity monitoring and background sync scheduling.
 */
public class TeamFlowApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NightModeHelper.applyFromPrefs(this);

        // Supabase initialization can be added here if needed

        NetworkMonitor.start(this);
        SyncScheduler.schedulePeriodic(this);
    }
}
