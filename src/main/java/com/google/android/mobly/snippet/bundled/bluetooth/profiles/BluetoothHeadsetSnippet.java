/*
 * Copyright (C) 2024 Google Inc.
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
import java.util.Set;

/**
 * Custom exception class for handling exceptions within the BluetoothHeadsetSnippet.
 * This exception is meant to encapsulate and convey specific error information related to
 * BluetoothHeadsetSnippet operations.
 */
public class BluetoothHeadsetSnippet implements Snippet {

    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private static class BluetoothHeadsetSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        /**
         * Constructs a BluetoothHeadsetSnippetException with the specified detail message.
         *
         * @param msg The detail message providing information about the exception.
         */
        BluetoothHeadsetSnippetException(String msg) {
            super(msg);
        }
    }

    private BluetoothHeadset mBluetoothHeadset;
    private static final int HEADSET = 1;

    private final BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int var1, BluetoothProfile profile) {
            if (var1 == HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset)profile;
            }
        }
        @Override
        public void onServiceDisconnected(int var1) {
            if (var1 == HEADSET) {
                mBluetoothHeadset = null;
            }
        }
    };

    public BluetoothHeadsetSnippet() throws Throwable {
        IntentFilter filter = new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.HEADSET);
        Utils.waitUntil(() -> mBluetoothHeadset != null, 60);
        mContext.registerReceiver(new PairingBroadcastReceiver(mContext), filter);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @RpcMinSdk(Build.VERSION_CODES.KITKAT)
    @Rpc(
        description =
            "Connects to a paired or discovered device with HEADSET profile."
                + "If a device has been discovered but not paired, this will pair it.")
    public void btHfpConnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = BluetoothAdapterSnippet.getKnownDeviceByAddress(deviceAddress);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        mContext.registerReceiver(new PairingBroadcastReceiver(mContext), filter);
        Utils.invokeByReflection(mBluetoothHeadset, "connect", device);
        if (!Utils.waitUntil(
            () -> mBluetoothHeadset.getConnectionState(device) == BluetoothHeadset.STATE_CONNECTED,
            120)) {
            throw new BluetoothHeadsetSnippetException(
                "Failed to connect to device "
                    + device.getName()
                    + "|"
                    + device.getAddress()
                    + " with HEADSET profile within 2min.");
        }
    }

    @Rpc(description = "Disconnects a device from HEADSET profile.")
    public void btHfpDisconnect(String deviceAddress) throws Throwable {
        BluetoothDevice device = getConnectedBluetoothDevice(deviceAddress);
        Utils.invokeByReflection(mBluetoothHeadset, "disconnect", device);
        if (!Utils.waitUntil(
            () -> mBluetoothHeadset.getConnectionState(device) == BluetoothHeadset.STATE_DISCONNECTED,
            120)) {
            throw new BluetoothHeadsetSnippetException(
                "Failed to disconnect device "
                    + device.getName()
                    + "|"
                    + device.getAddress()
                    + " from HEADSET profile within 2min.");
        }
    }

    /**
     * Returns the connection state for a Bluetooth device with the specified name.
     *
     * @param deviceAddress The address of the Bluetooth device.
     * @return The connection state for the specified device.
     * @throws BluetoothHeadsetSnippetException If no device with the specified name is connected via HEADSET.
     */
    @Rpc(description = "Returns connection state.")
    public int btHfpGetConnectionState(String deviceAddress) throws BluetoothHeadsetSnippetException {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return mBluetoothHeadset.getConnectionState(device);
            }
        }
        throw new BluetoothHeadsetSnippetException("No device with name " + deviceAddress +" is connected via HEADSET.");
    }

    /**
     * Starts voice recognition for the Bluetooth device with the specified name.
     *
     * @param deviceAddress The address of the Bluetooth device.
     * @return True if voice recognition is successfully started; false otherwise.
     * @throws BluetoothHeadsetSnippetException If no device with the specified name is found or if an error
     *         occurs during the startVoiceRecognition operation.
     */
    @Rpc(description = "Starts voice recognition.")
    public boolean btHfpStartVoiceRecognition(String deviceAddress) throws BluetoothHeadsetSnippetException{
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return mBluetoothHeadset.startVoiceRecognition(device);
            }
        }
        throw new BluetoothHeadsetSnippetException("No device with name " + deviceAddress +" is connected via HEADSET.");
    }


    /**
     * Stops voice recognition for the Bluetooth device with the specified name.
     *
     * @param deviceAddress The address of the Bluetooth device.
     * @return True if voice recognition is successfully started; false otherwise.
     * @throws BluetoothHeadsetSnippetException If no device with the specified name is found or if an error
     *         occurs during the startVoiceRecognition operation.
     */
    @Rpc(description = "Stops voice recognition.")
    public boolean btHfpStopVoiceRecognition(String deviceAddress) throws BluetoothHeadsetSnippetException {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return mBluetoothHeadset.stopVoiceRecognition(device);
            }
        }
        throw new BluetoothHeadsetSnippetException("No device with name " + deviceAddress +" is connected via HEADSET.");
    }

    @Rpc(description = "Checks whether the headset supports voice recognition;")
    public boolean btHfpIsVoiceRecognitionSupported(String deviceAddress) throws BluetoothHeadsetSnippetException {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return mBluetoothHeadset.isVoiceRecognitionSupported(device);
            }
        }
        throw new BluetoothHeadsetSnippetException("No device with name " + deviceAddress +" is connected via HEADSET.");
    }
    @Rpc(description = "Gets all the devices currently connected via HFP profile.")
    public ArrayList<Bundle> btHfpGetConnectedDevices() {
        return mJsonSerializer.serializeBluetoothDeviceList(mBluetoothHeadset.getConnectedDevices());
    }

    private BluetoothDevice getConnectedBluetoothDevice(String deviceAddress)
        throws BluetoothHeadsetSnippetException {
        for (BluetoothDevice device : mBluetoothHeadset.getConnectedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return device;
            }
        }
        throw new BluetoothHeadsetSnippetException(
            "No device with address " + deviceAddress + " is connected via HEADSET.");
    }

    @Override
    public void shutdown() { }
}
