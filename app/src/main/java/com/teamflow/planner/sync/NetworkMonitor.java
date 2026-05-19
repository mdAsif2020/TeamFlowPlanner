package com.teamflow.planner.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.teamflow.planner.supabase.SupabaseService;

/**
 * Tracks validated internet connectivity and kicks a one-shot sync when Supabase is enabled.
 */
public final class NetworkMonitor {

    private NetworkMonitor() {
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public static void start(Context app) {
        ConnectivityManager cm = (ConnectivityManager) app.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                apply(app);
            }

            @Override
            public void onLost(Network network) {
                if (!SupabaseService.isConfigured()) {
                    SyncState.post(SyncState.Mode.LOCAL_ONLY);
                } else {
                    SyncState.post(SyncState.Mode.OFFLINE);
                }
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                apply(app);
            }
        });
        apply(app);
    }

    private static void apply(Context app) {
        if (!SupabaseService.isConfigured()) {
            SyncState.post(SyncState.Mode.LOCAL_ONLY);
            return;
        }
        if (isOnline(app)) {
            SyncWorker.enqueueOneTime(app.getApplicationContext());
        } else {
            SyncState.post(SyncState.Mode.OFFLINE);
        }
    }
}
