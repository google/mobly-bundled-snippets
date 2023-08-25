package com.google.android.mobly.snippet.bundled.bluetooth.profiles;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
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

/** */
public class BluetoothHeadsetSnippet implements Snippet {

    private static class BluetoothHeadsetSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        BluetoothHeadsetSnippetException(String message) {
            super(message);
        }
    }

    private Context mContext;
    private static boolean sIsHeadsetProfileReady = false;
    private static BluetoothHeadset sHeadsetProfile;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();

    public BluetoothHeadsetSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.getProfileProxy(
                mContext, new HeadsetServiceListener(), BluetoothProfile.HEADSET);
        Utils.waitUntil(() -> sIsHeadsetProfileReady, 60);
    }

    private static class HeadsetServiceListener implements BluetoothProfile.ServiceListener {

        public void onServiceConnected(int var1, BluetoothProfile profile) {
            sHeadsetProfile = (BluetoothHeadset) profile;
            sIsHeadsetProfileReady = true;
        }

        public void onServiceDisconnected(int var1) {
            sIsHeadsetProfileReady = false;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @RpcMinSdk(Build.VERSION_CODES.KITKAT)
    @Rpc(
            description =
                    "Connects to a paired or discovered device with Headset profile."
                            + "If a device has been discovered but not paired, this will pair it.")
    public void btHeadsetConnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = BluetoothAdapterSnippet.getKnownDeviceByAddress(deviceAddress);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        mContext.registerReceiver(new PairingBroadcastReceiver(mContext), filter);
        Utils.invokeByReflection(sHeadsetProfile, "connect", device);
        if (!Utils.waitUntil(
                () -> sHeadsetProfile.getConnectionState(device) == BluetoothHeadset.STATE_CONNECTED,
                120)) {
            throw new BluetoothHeadsetSnippetException(
                    "Failed to connect to device "
                            + device.getName()
                            + "|"
                            + device.getAddress()
                            + " with Headset profile within 2min.");
        }
    }

    @Rpc(description = "Disconnects a device from Headset profile.")
    public void btHeadsetDisconnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        Utils.invokeByReflection(sHeadsetProfile, "disconnect", device);
        if (!Utils.waitUntil(
                () -> sHeadsetProfile.getConnectionState(device) == BluetoothHeadset.STATE_DISCONNECTED,
                120)) {
            throw new BluetoothHeadsetSnippetException(
                    "Failed to disconnect device "
                            + device.getName()
                            + "|"
                            + device.getAddress()
                            + " from A2DP profile within 2min.");
        }
    }

    @Rpc(description = "Gets all the devices currently connected via Headset profile.")
    public ArrayList<Bundle> btHeadsetGetConnectedDevices() {
        return mJsonSerializer.serializeBluetoothDeviceList(sHeadsetProfile.getConnectedDevices());
    }

    @Rpc(description = "Checks if a device supports noise reduction.")
    public boolean btHeadsetIsNoiseReductionSupported(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        return sHeadsetProfile.isNoiseReductionSupported(device);
    }

    @Rpc(description = "Checks if a device supports voice recognition.")
    public boolean btHeadsetIsVoiceRecognitionSupported(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        return sHeadsetProfile.isVoiceRecognitionSupported(device);
    }

    private BluetoothDevice getConnectedBluetoothDevice(String deviceAddress)
            throws BluetoothHeadsetSnippetException {
        for (BluetoothDevice device : sHeadsetProfile.getConnectedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return device;
            }
        }
        throw new BluetoothHeadsetSnippetException(
                "No device with address " + deviceAddress + " is connected via Headset.");
    }

    @Override
    public void shutdown() {}
}


