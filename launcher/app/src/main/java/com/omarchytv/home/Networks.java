package com.omarchytv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * Watches the active network without calling APIs the TV does not have.
 * {@code registerDefaultNetworkCallback(NetworkCallback, Handler)} is API 26, the single-argument
 * form is API 24, and {@code registerNetworkCallback} goes back to API 21. If the OEM build rejects
 * the callback, the old connectivity broadcast is used instead. Either way a failure is ignored:
 * the home screen still refreshes the Wi-Fi label on its minute tick.
 */
final class Networks {
    private Networks() {
    }

    static final class Registration {
        static final Registration NONE = new Registration(null, null, null, null);
        private final Context context;
        private final ConnectivityManager cm;
        private final ConnectivityManager.NetworkCallback callback;
        private final BroadcastReceiver receiver;

        private Registration(Context context, ConnectivityManager cm,
                             ConnectivityManager.NetworkCallback callback, BroadcastReceiver receiver) {
            this.context = context;
            this.cm = cm;
            this.callback = callback;
            this.receiver = receiver;
        }

        void close() {
            try {
                if (callback != null && cm != null) cm.unregisterNetworkCallback(callback);
                else if (receiver != null && context != null) context.unregisterReceiver(receiver);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation") // CONNECTIVITY_ACTION is the API 23 fallback when callbacks are missing
    static Registration listen(Context context, ConnectivityManager cm,
                               ConnectivityManager.NetworkCallback callback, Handler handler, Runnable fallback) {
        if (cm == null || callback == null) return Registration.NONE;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                cm.registerDefaultNetworkCallback(callback, handler != null ? handler : new Handler(Looper.getMainLooper()));
            } else if (Build.VERSION.SDK_INT >= 24) {
                cm.registerDefaultNetworkCallback(callback);
            } else {
                cm.registerNetworkCallback(new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), callback);
            }
            return new Registration(context, cm, callback, null);
        } catch (RuntimeException ignored) {
            if (context == null || fallback == null) return Registration.NONE;
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    fallback.run();
                }
            };
            try {
                context.registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                return new Registration(context, cm, null, receiver);
            } catch (RuntimeException again) {
                return Registration.NONE;
            }
        }
    }
}
