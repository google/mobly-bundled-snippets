package com.google.android.mobly.snippet.bundled.bluetooth.profiles;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.test.InstrumentationRegistry;

import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;

public class BluetoothA2dpSnippet {
    private static class BluetoothA2dpSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothA2dpSnippetException(String msg) {
            super(msg);
        }
    }

    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private static boolean sIsA2dpProfileReady = false;
    private static BluetoothA2dp sA2dp;

    public BluetoothA2dpSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.getProfileProxy(mContext, new A2dpServiceListener(), BluetoothProfile.A2DP);
        Utils.waitUntil(() -> sIsA2dpProfileReady, 60);
    }

    private static class A2dpServiceListener implements BluetoothProfile.ServiceListener {
        public void onServiceConnected(int var1, BluetoothProfile profile) {
            sA2dp = (BluetoothA2dp) profile;
            sIsA2dpProfileReady = true;
        }

        public void onServiceDisconnected(int var1) {
            sIsA2dpProfileReady = false;
        }
    }

    @Rpc(description = "Connect to a device via A2dp profile.")
    public void btA2dpConnect(BluetoothDevice device) throws Throwable {
        Utils.invokeByReflection(sA2dp, "connect", device);
        if (!Utils.waitUntil(() -> sA2dp.getConnectionState(device) == BluetoothA2dp.STATE_CONNECTED, 120)) {
            throw new BluetoothA2dpSnippetException("Failed to connect to device " + device.getName() + "|" + device.getAddress() + " within 2min.");
        }
    }
}
