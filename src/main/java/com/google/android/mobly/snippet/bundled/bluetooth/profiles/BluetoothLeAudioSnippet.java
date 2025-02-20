/*
 * Copyright (C) 2025 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.mobly.snippet.bundled.bluetooth.profiles;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
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

/** Snippet class exposing Bluetooth LE Audio profile. */
public class BluetoothLeAudioSnippet implements Snippet {
    private static class BluetoothLeAudioSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        /**
         * Constructs a BluetoothLeAudioSnippetException with the specified detail message.
         *
         * @param msg The detail message providing information about the exception.
         */
        BluetoothLeAudioSnippetException(String msg) {
            super(msg);
        }
    }

    private final Context mContext;
    private static boolean sIsLeAudioProfileReady = false;
    private static BluetoothLeAudio sLeAudioProfile;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();

    public BluetoothLeAudioSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.getProfileProxy(
                mContext, new LeAudioServiceListener(), BluetoothProfile.LE_AUDIO);
        Utils.waitUntil(() -> sIsLeAudioProfileReady, 60);
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @RpcMinSdk(Build.VERSION_CODES.TIRAMISU)
    @Rpc(
        description =
            "Connects to a paired or discovered device with LE Audio profile."
                + "If a device has been discovered but not paired, this will pair it.")
    public void btLeAudioConnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = BluetoothAdapterSnippet.getKnownDeviceByAddress(deviceAddress);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        mContext.registerReceiver(new PairingBroadcastReceiver(mContext), filter);
        Utils.invokeByReflection(sLeAudioProfile, "connect", device);
        if (!Utils.waitUntil(
            () -> sLeAudioProfile.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED,
            120)) {
            throw new BluetoothLeAudioSnippetException(
                "Failed to connect to device "
                    + device.getName()
                    + "|"
                    + device.getAddress()
                    + " with LE Audio profile within 2min.");
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @RpcMinSdk(Build.VERSION_CODES.TIRAMISU)
    @Rpc(description = "Disconnects a device from LE Audio profile.")
    public void btLeAudioDisconnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        Utils.invokeByReflection(sLeAudioProfile, "disconnect", device);
        if (!Utils.waitUntil(
            () -> sLeAudioProfile.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED,
            120)) {
            throw new BluetoothLeAudioSnippetException(
                "Failed to disconnect device "
                    + device.getName()
                    + "|"
                    + device.getAddress()
                    + " from LE Audio profile within 2min.");
        }
    }

    /** Service Listener for {@link BluetoothLeAudio}. */
    private static class LeAudioServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profileType, BluetoothProfile profile) {
            sLeAudioProfile = (BluetoothLeAudio) profile;
            sIsLeAudioProfileReady = true;
        }

        @Override
        public void onServiceDisconnected(int profileType) {
            sIsLeAudioProfileReady = false;
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @RpcMinSdk(Build.VERSION_CODES.TIRAMISU)
    @Rpc(description = "Gets all the devices currently connected via LE Audio profile.")
    public ArrayList<Bundle> btLeAudioGetConnectedDevices() {
        return mJsonSerializer.serializeBluetoothDeviceList(sLeAudioProfile.getConnectedDevices());
    }

    private BluetoothDevice getConnectedBluetoothDevice(String deviceAddress)
        throws BluetoothLeAudioSnippetException {
        for (BluetoothDevice device : sLeAudioProfile.getConnectedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return device;
            }
        }
        throw new BluetoothLeAudioSnippetException(
            "No device with address " + deviceAddress + " is connected via LE Audio.");
    }
}
