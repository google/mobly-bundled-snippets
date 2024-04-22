package com.google.android.mobly.snippet.bundled.wifidiret;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

public class GoNetSnippet implements Snippet {
    private static final String TAG = "GoNetSnippet";

    private final IntentFilter mIntentFilter = new IntentFilter();
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final WifiP2pManager mP2pMgr = (WifiP2pManager) mContext.getSystemService(Context.WIFI_P2P_SERVICE);
    private final Channel mChannel = mP2pMgr.initialize(mContext, mContext.getMainLooper(), null);
    private final WifiP2pBroadcastReceiver mReceiver = new WifiP2pBroadcastReceiver();

    public GoNetSnippet() {
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, mIntentFilter);
    }

    @Rpc(description = "Triggers Wi-Fi P2P discovery to locate nearby devices and initiate connectivity.")
    public void discoverPeers() throws SecurityException {
        if (mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                mContext.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission denied: cannot discover peers.");
        }
        mP2pMgr.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Peer discovery started successfully.");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to start peer discovery: " + reason);
                throw new RuntimeException("Peer discovery failed with error code " + reason);
            }
        });
    }

    @Override
    public void shutdown() {
        try {
            mContext.unregisterReceiver(mReceiver);
            if (mP2pMgr != null) {
                mP2pMgr.clearLocalServices(mChannel, null);
                mP2pMgr.clearServiceRequests(mChannel, null);
            }
            if (mChannel != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                mChannel.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during shutdown", e);
            throw new RuntimeException("Error during shutdown", e);
        }
    }

    private class WifiP2pBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                synchronized (this) {
                    WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                    if (p2pInfo != null && p2pInfo.groupFormed && !p2pInfo.isGroupOwner) {
                        mP2pMgr.removeGroup(mChannel, null);
                    } else if (p2pInfo != null && !p2pInfo.groupFormed) {
                        discoverPeers();
                    }
                }
            }
        }
    }
}
