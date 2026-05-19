package com.teamflow.planner.sync;

import android.content.Context;

import androidx.work.ListenableWorker;

import com.teamflow.planner.supabase.SupabaseService;

/**
 * Minimal cloud sync hook.
 *
 * This project already persists data locally (Room). Supabase realtime listeners can keep
 * the UI updated, while this worker can be expanded later to do true bidirectional sync.
 */
public final class SupabaseSyncRepository {

    private SupabaseSyncRepository() {
    }

    public static ListenableWorker.Result runBlockingSync(Context context) {
        if (!SupabaseService.isConfigured()) {
            SyncState.post(SyncState.Mode.LOCAL_ONLY);
            return ListenableWorker.Result.success();
        }
        try {
            SyncState.post(SyncState.Mode.SYNCING);
            // TODO: Implement push/pull sync (projects/tasks) using PostgREST.
            SyncState.post(SyncState.Mode.SYNCED);
            return ListenableWorker.Result.success();
        } catch (Exception e) {
            SyncState.post(SyncState.Mode.SYNC_ERROR);
            return ListenableWorker.Result.retry();
        }
    }
}

