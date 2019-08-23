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
import com.google.common.base.Ascii;
import java.util.ArrayList;

public class BluetoothHearingAidSnippet implements Snippet {
    private static class BluetoothHearingAidSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        BluetoothHearingAidSnippetException(String msg) {
            super(msg);
        }
    }

    private static final int TIMEOUT_SEC = 60;

    private final Context context;
    private static boolean isHearingAidProfileReady = false;
    private static BluetoothHearingAid hearingAidProfile;
    private final JsonSerializer jsonSerializer = new JsonSerializer();

    @TargetApi(Build.VERSION_CODES.Q)
    public BluetoothHearingAidSnippet() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.getProfileProxy(
                context, new HearingAidServiceListener(), BluetoothProfile.HEARING_AID);
        Utils.waitUntil(() -> isHearingAidProfileReady, TIMEOUT_SEC);
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private static class HearingAidServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int var1, BluetoothProfile profile) {
            hearingAidProfile = (BluetoothHearingAid) profile;
            isHearingAidProfileReady = true;
        }

        @Override
        public void onServiceDisconnected(int var1) {
            isHearingAidProfileReady = false;
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    @RpcMinSdk(Build.VERSION_CODES.Q)
    @Rpc(description = "Connects to a paired or discovered device with HA profile.")
    public void btHearingAidConnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = BluetoothAdapterSnippet.getKnownDeviceByAddress(deviceAddress);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        context.registerReceiver(new PairingBroadcastReceiver(context), filter);
        Utils.invokeByReflection(hearingAidProfile, "connect", device);
        if (!Utils.waitUntil(
                () ->
                        hearingAidProfile.getConnectionState(device)
                                == BluetoothHearingAid.STATE_CONNECTED,
                TIMEOUT_SEC)) {
            throw new BluetoothHearingAidSnippetException(
                    String.format(
                            "Failed to connect to device %s|%s with HA profile within %d sec.",
                            device.getName(), device.getAddress(), TIMEOUT_SEC));
        }
    }

    @Rpc(description = "Disconnects a device from HA profile.")
    public void btHearingAidDisconnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        Utils.invokeByReflection(hearingAidProfile, "disconnect", device);
        if (!Utils.waitUntil(
                () ->
                        hearingAidProfile.getConnectionState(device)
                                == BluetoothHearingAid.STATE_DISCONNECTED,
                TIMEOUT_SEC)) {
            throw new BluetoothHearingAidSnippetException(
                    String.format(
                            "Failed to disconnect to device %s|%s with HA profile within %d sec.",
                            device.getName(), device.getAddress(), TIMEOUT_SEC));
        }
    }

    @Rpc(description = "Gets all the devices currently connected via HA profile.")
    public ArrayList<Bundle> btHearingAidGetConnectedDevices() {
        return jsonSerializer.serializeBluetoothDeviceList(hearingAidProfile.getConnectedDevices());
    }

    private static BluetoothDevice getConnectedBluetoothDevice(String deviceAddress)
            throws BluetoothHearingAidSnippetException {
        for (BluetoothDevice device : hearingAidProfile.getConnectedDevices()) {
            if (Ascii.equalsIgnoreCase(device.getAddress(), deviceAddress)) {
                return device;
            }
        }
        throw new BluetoothHearingAidSnippetException(String.format(
                "No device with address %s is connected via HA Profile.", deviceAddress));
    }

    @Override
    public void shutdown() {}
}
