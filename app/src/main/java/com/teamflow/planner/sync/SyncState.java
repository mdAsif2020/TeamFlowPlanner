package com.teamflow.planner.sync;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Observed by the dashboard for sync / connectivity UI.
 */
public final class SyncState {

    public enum Mode {
        /** Cloud sync not configured; data stays on device only. */
        LOCAL_ONLY,
        /** No usable network connection. */
        OFFLINE,
        /** Cloud push/pull in progress. */
        SYNCING,
        /** Last sync finished successfully. */
        SYNCED,
        /** Last sync attempt failed (will retry in background). */
        SYNC_ERROR
    }

    private static final MutableLiveData<Mode> LIVE = new MutableLiveData<>(Mode.LOCAL_ONLY);

    private SyncState() {
    }

    public static LiveData<Mode> getMode() {
        return LIVE;
    }

    public static void post(Mode mode) {
        LIVE.postValue(mode);
    }

    public static Mode getCurrent() {
        Mode m = LIVE.getValue();
        return m != null ? m : Mode.LOCAL_ONLY;
    }
}
