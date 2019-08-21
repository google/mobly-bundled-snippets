package com.google.android.mobly.snippet.bundled.bluetooth.profiles;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
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

public class BluetoothHearingAidSnippet implements Snippet {
    private static class BluetoothHearingAidSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        BluetoothHearingAidSnippetException(String msg) {
            super(msg);
        }
    }

    private Context context;
    private static boolean sIsHearingAidProfileReady = false;
    private static BluetoothHearingAid sHearingAidProfile;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();

    @TargetApi(Build.VERSION_CODES.Q)
    public BluetoothHearingAidSnippet() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.getProfileProxy(
                context, new HearingAidServiceListener(), BluetoothProfile.HEARING_AID);
        Utils.waitUntil(() -> sIsHearingAidProfileReady, 60);
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private static class HearingAidServiceListener implements BluetoothProfile.ServiceListener {
        public void onServiceConnected(int var1, BluetoothProfile profile) {
            sHearingAidProfile = (BluetoothHearingAid) profile;
            sIsHearingAidProfileReady = true;
        }

        public void onServiceDisconnected(int var1) {
            sIsHearingAidProfileReady = false;
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    @RpcMinSdk(Build.VERSION_CODES.Q)
    @Rpc(description = "Connects to a paired or discovered device with HA profile.")
    public void btHearingAidConnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = BluetoothAdapterSnippet.getKnownDeviceByAddress(deviceAddress);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        context.registerReceiver(new PairingBroadcastReceiver(context), filter);
        Utils.invokeByReflection(sHearingAidProfile, "connect", device);
        if (!Utils.waitUntil(
                () ->
                        sHearingAidProfile.getConnectionState(device)
                                == BluetoothHearingAid.STATE_CONNECTED,
                120)) {
            throw new BluetoothHearingAidSnippetException(
                    "Failed to connect to device "
                            + device.getName()
                            + "|"
                            + device.getAddress()
                            + " with HA profile within 2min.");
        }
    }

    @Rpc(description = "Disconnects a device from HA profile.")
    public void btHearingAidDisconnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        Utils.invokeByReflection(sHearingAidProfile, "disconnect", device);
        if (!Utils.waitUntil(
                () ->
                        sHearingAidProfile.getConnectionState(device)
                                == BluetoothHearingAid.STATE_DISCONNECTED,
                120)) {
            throw new BluetoothHearingAidSnippetException(
                    "Failed to disconnect device "
                            + device.getName()
                            + "|"
                            + device.getAddress()
                            + " from HA profile within 2min.");
        }
    }

    @Rpc(description = "Gets all the devices currently connected via HA profile.")
    public ArrayList<Bundle> btHearingAidGetConnectedDevices() {
        return mJsonSerializer.serializeBluetoothDeviceList(
                sHearingAidProfile.getConnectedDevices());
    }

    private BluetoothDevice getConnectedBluetoothDevice(String deviceAddress)
            throws BluetoothHearingAidSnippetException {
        for (BluetoothDevice device : sHearingAidProfile.getConnectedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return device;
            }
        }
        throw new BluetoothHearingAidSnippetException(
                "No device with address " + deviceAddress + " is connected via HA Profile.");
    }

    @Override
    public void shutdown() {}
}
