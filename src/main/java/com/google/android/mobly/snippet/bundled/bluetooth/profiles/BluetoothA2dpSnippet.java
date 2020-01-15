package com.google.android.mobly.snippet.bundled.bluetooth.profiles;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.bluetooth.BluetoothAdapterSnippet;
import com.google.android.mobly.snippet.bundled.bluetooth.PairingBroadcastReceiver;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import java.util.ArrayList;

public class BluetoothA2dpSnippet implements Snippet {
    private static class BluetoothA2dpSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        BluetoothA2dpSnippetException(String msg) {
            super(msg);
        }
    }

    private Context mContext;
    private static boolean sIsA2dpProfileReady = false;
    private static BluetoothA2dp sA2dpProfile;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();

    public BluetoothA2dpSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.getProfileProxy(
                mContext, new A2dpServiceListener(), BluetoothProfile.A2DP);
        Utils.waitUntil(() -> sIsA2dpProfileReady, 60);
    }

    private static class A2dpServiceListener implements BluetoothProfile.ServiceListener {
        public void onServiceConnected(int var1, BluetoothProfile profile) {
            sA2dpProfile = (BluetoothA2dp) profile;
            sIsA2dpProfileReady = true;
        }

        public void onServiceDisconnected(int var1) {
            sIsA2dpProfileReady = false;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @RpcMinSdk(Build.VERSION_CODES.KITKAT)
    @Rpc(
            description =
                    "Connects to a paired or discovered device with A2DP profile."
                            + "If a device has been discovered but not paired, this will pair it.")
    public void btA2dpConnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = BluetoothAdapterSnippet.getKnownDeviceByAddress(deviceAddress);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        mContext.registerReceiver(new PairingBroadcastReceiver(mContext), filter);
        Utils.invokeByReflection(sA2dpProfile, "connect", device);
        if (!Utils.waitUntil(
                () -> sA2dpProfile.getConnectionState(device) == BluetoothA2dp.STATE_CONNECTED,
                120)) {
            throw new BluetoothA2dpSnippetException(
                    "Failed to connect to device "
                            + device.getName()
                            + "|"
                            + device.getAddress()
                            + " with A2DP profile within 2min.");
        }
    }

    @Rpc(description = "Disconnects a device from A2DP profile.")
    public void btA2dpDisconnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        Utils.invokeByReflection(sA2dpProfile, "disconnect", device);
        if (!Utils.waitUntil(
                () -> sA2dpProfile.getConnectionState(device) == BluetoothA2dp.STATE_DISCONNECTED,
                120)) {
            throw new BluetoothA2dpSnippetException(
                    "Failed to disconnect device "
                            + device.getName()
                            + "|"
                            + device.getAddress()
                            + " from A2DP profile within 2min.");
        }
    }

    @Rpc(description = "Gets all the devices currently connected via A2DP profile.")
    public ArrayList<Bundle> btA2dpGetConnectedDevices() {
        return mJsonSerializer.serializeBluetoothDeviceList(sA2dpProfile.getConnectedDevices());
    }

    @Rpc(description = "Checks if a device is streaming audio via A2DP profile.")
    public boolean btIsA2dpPlaying(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        return sA2dpProfile.isA2dpPlaying(device);
    }

    private BluetoothDevice getConnectedBluetoothDevice(String deviceAddress)
            throws BluetoothA2dpSnippetException {
        for (BluetoothDevice device : sA2dpProfile.getConnectedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return device;
            }
        }
        throw new BluetoothA2dpSnippetException(
                "No device with address " + deviceAddress + " is connected via A2DP.");
    }

    @Override
    public void shutdown() {}
}
