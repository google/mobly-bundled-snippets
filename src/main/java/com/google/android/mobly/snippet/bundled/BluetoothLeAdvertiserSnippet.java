/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.mobly.snippet.bundled;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;

/** Snippet class exposing Android APIs in WifiManager. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class BluetoothLeAdvertiserSnippet implements Snippet {
    private static class BluetoothLeAdvertiserSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothLeAdvertiserSnippetException(String msg) {
            super(msg);
        }
    }

    private final BluetoothManager mBluetoothManager;
    private final BluetoothLeAdvertiser mAdvertiser;
    private final Context mContext;
    private final EventCache mEventCache = EventCache.getInstance();
    private final HashMap<String, AdvertiseCallback> mAdvertiseCallbacks = new HashMap<>();

    public BluetoothLeAdvertiserSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mAdvertiser = mBluetoothManager.getAdapter().getBluetoothLeAdvertiser();
    }

    /**
     * Start Bluetooth LE advertising.
     *
     * <p>This can be called multiple times, and each call is associated with a {@link
     * AdvertiseCallback} object, which is used to stop the advertising.
     *
     * @param callbackId
     * @param advertiseSettings A JSONObject with the info on the settings for the advertising. For
     *     example: {"AdvertiseMode": {@link AdvertiseSettings#ADVERTISE_MODE_BALANCED}, "Timeout":
     *     60000, "Connectable": False, "TxPowerLevel": {@link
     *     AdvertiseSettings#ADVERTISE_TX_POWER_LOW} }
     * @param advertiseData A JSONObject representing the data to include in advertising beacon. For
     *     example: {"IncludeDeviceName": true, "ServiceData": [A Base64 encoded string],
     *     "ServiceUuid": [A string representation of the UUID]}
     * @throws JSONException
     */
    @AsyncRpc(description = "Start BLE advertising.")
    public void bleStartAdvertising(
            String callbackId, JSONObject advertiseSettings, JSONObject advertiseData)
            throws JSONException {
        AdvertiseSettings settings = JsonDeserializer.jsonToBleAdvertiseSettings(advertiseSettings);
        AdvertiseData data = JsonDeserializer.jsonToBleAdvertiseData(advertiseData);
        AdvertiseCallback advertiseCallback = new DefaultAdvertiseCallback(callbackId);
        mAdvertiser.startAdvertising(settings, data, advertiseCallback);
        mAdvertiseCallbacks.put(callbackId, advertiseCallback);
    }

    @Rpc(description = "Stop BLE advertising.")
    public void bleStopAdvertitsing(String id) throws BluetoothLeAdvertiserSnippetException {
        if (!mAdvertiseCallbacks.containsKey(id)) {
            throw new BluetoothLeAdvertiserSnippetException(
                    "No advertising session found for ID " + id);
        }
        mAdvertiser.stopAdvertising(mAdvertiseCallbacks.remove(id));
    }

    private class DefaultAdvertiseCallback extends AdvertiseCallback {
        private final String mCallbackId;

        public DefaultAdvertiseCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onStartSuccess");
            final String nameTxPowerLevel = "TxPowerLevel";
            switch (settingsInEffect.getTxPowerLevel()) {
                case AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW:
                    event.getData().putString(nameTxPowerLevel, "ADVERTISE_TX_POWER_ULTRA_LOW");
                    break;
                case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
                    event.getData().putString(nameTxPowerLevel, "ADVERTISE_TX_POWER_LOW");
                    break;
                case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
                    event.getData().putString(nameTxPowerLevel, "ADVERTISE_TX_POWER_MEDIUM");
                    break;
                case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH:
                    event.getData().putString(nameTxPowerLevel, "ADVERTISE_TX_POWER_HIGH");
                    break;
                default:
                    event.getData().putString(nameTxPowerLevel, "UNKNOWN");
                    break;
            }
            final String nameMode = "Mode";
            switch (settingsInEffect.getMode()) {
                case AdvertiseSettings.ADVERTISE_MODE_BALANCED:
                    event.getData().putString(nameMode, "ADVERTISE_MODE_BALANCED");
                    break;
                case AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY:
                    event.getData().putString(nameMode, "ADVERTISE_MODE_LOW_LATENCY");
                    break;
                case AdvertiseSettings.ADVERTISE_MODE_LOW_POWER:
                    event.getData().putString(nameMode, "ADVERTISE_MODE_LOW_POWER");
                    break;
                default:
                    event.getData().putString(nameMode, "UNKNOWN");
                    break;
            }
            event.getData().putInt("Timeout", settingsInEffect.getTimeout());
            event.getData().putBoolean("IsConnectable", settingsInEffect.isConnectable());
            mEventCache.postEvent(event);
        }

        public void onStartFailure(int errorCode) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onStartFailure");
            final String nameErrorCode = "ErrorCode";
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    event.getData().putString(nameErrorCode, "ADVERTISE_FAILED_ALREADY_STARTED");
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    event.getData().putString(nameErrorCode, "ADVERTISE_FAILED_DATA_TOO_LARGE");
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    event.getData().putString(nameErrorCode, "ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    event.getData().putString(nameErrorCode, "ADVERTISE_FAILED_INTERNAL_ERROR");
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    event.getData().putString(nameErrorCode, "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                    break;
                default:
                    event.getData().putString(nameErrorCode, "UNKNOWN");
                    break;
            }
            mEventCache.postEvent(event);
        }
    }

    @Override
    public void shutdown() {
        for(String id : mAdvertiseCallbacks.keySet()) {
            mAdvertiser.stopAdvertising(mAdvertiseCallbacks.get(id));
        }
        mAdvertiseCallbacks.clear();
    }
}
