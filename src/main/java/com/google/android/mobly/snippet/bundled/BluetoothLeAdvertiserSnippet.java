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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.RpcEnum;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;
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

    private final BluetoothLeAdvertiser mAdvertiser;
    private static final EventCache sEventCache = EventCache.getInstance();

    private final HashMap<String, AdvertiseCallback> mAdvertiseCallbacks = new HashMap<>();

    public BluetoothLeAdvertiserSnippet() {
        mAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
    }

    /**
     * Start Bluetooth LE advertising.
     *
     * <p>This can be called multiple times, and each call is associated with a {@link
     * AdvertiseCallback} object, which is used to stop the advertising.
     *
     * @param callbackId
     * @param advertiseSettings A JSONObject representing a {@link AdvertiseSettings object}. E.g.
     *     <pre>
     *          {
     *            "AdvertiseMode": "ADVERTISE_MODE_BALANCED",
     *            "Timeout": (int, milliseconds),
     *            "Connectable": (bool),
     *            "TxPowerLevel": "ADVERTISE_TX_POWER_LOW"
     *          }
     *     </pre>
     *
     * @param advertiseData A JSONObject representing a {@link AdvertiseData} object will be
     *     broadcast if the operation succeeds. E.g.
     *     <pre>
     *          {
     *            "IncludeDeviceName": (bool),
     *            # JSON list, each element representing a set of service data, which is composed of
     *            # a UUID, and an optional string.
     *            "ServiceData": [
     *                      {
     *                        "UUID": (A string representation of {@link ParcelUuid}),
     *                        "Data": (Optional, The string representation of what you want to
     *                                 advertise, base64 encoded)
     *                        # If you want to add a UUID without data, simply omit the "Data"
     *                        # field.
     *                      }
     *                ]
     *          }
     *     </pre>
     *
     * @param scanResponse A JSONObject representing a {@link AdvertiseData} object which will
     *     response the data to the scanning device. E.g.
     *     <pre>
     *          {
     *            "IncludeDeviceName": (bool),
     *            # JSON list, each element representing a set of service data, which is composed of
     *            # a UUID, and an optional string.
     *            "ServiceData": [
     *                      {
     *                        "UUID": (A string representation of {@link ParcelUuid}),
     *                        "Data": (Optional, The string representation of what you want to
     *                                 advertise, base64 encoded)
     *                        # If you want to add a UUID without data, simply omit the "Data"
     *                        # field.
     *                      }
     *                ]
     *          }
     *     </pre>
     *
     * @throws BluetoothLeAdvertiserSnippetException
     * @throws JSONException
     */
    @RpcMinSdk(Build.VERSION_CODES.LOLLIPOP_MR1)
    @AsyncRpc(description = "Start BLE advertising.")
    public void bleStartAdvertising(
            String callbackId,
            JSONObject advertiseSettings,
            JSONObject advertiseData,
            @RpcOptional JSONObject scanResponse)
            throws BluetoothLeAdvertiserSnippetException, JSONException {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            throw new BluetoothLeAdvertiserSnippetException(
                    "Bluetooth is disabled, cannot start BLE advertising.");
        }
        AdvertiseSettings settings = JsonDeserializer.jsonToBleAdvertiseSettings(advertiseSettings);
        AdvertiseData data = JsonDeserializer.jsonToBleAdvertiseData(advertiseData);
        AdvertiseCallback advertiseCallback = new DefaultAdvertiseCallback(callbackId);
        if (scanResponse == null) {
            mAdvertiser.startAdvertising(settings, data, advertiseCallback);
        } else {
            AdvertiseData response = JsonDeserializer.jsonToBleAdvertiseData(scanResponse);
            mAdvertiser.startAdvertising(settings, data, response, advertiseCallback);
        }
        mAdvertiseCallbacks.put(callbackId, advertiseCallback);
    }

    /**
     * Stop a BLE advertising.
     *
     * @param callbackId The callbackId corresponding to the {@link
     *     BluetoothLeAdvertiserSnippet#bleStartAdvertising} call that started the advertising.
     * @throws BluetoothLeScannerSnippet.BluetoothLeScanSnippetException
     */
    @RpcMinSdk(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Rpc(description = "Stop BLE advertising.")
    public void bleStopAdvertising(String callbackId) throws BluetoothLeAdvertiserSnippetException {
        AdvertiseCallback callback = mAdvertiseCallbacks.remove(callbackId);
        if (callback == null) {
            throw new BluetoothLeAdvertiserSnippetException(
                    "No advertising session found for ID " + callbackId);
        }
        mAdvertiser.stopAdvertising(callback);
    }

    private static class DefaultAdvertiseCallback extends AdvertiseCallback {
        private final String mCallbackId;
        public static RpcEnum ADVERTISE_FAILURE_ERROR_CODE =
                new RpcEnum.Builder()
                        .add("ADVERTISE_FAILED_ALREADY_STARTED", ADVERTISE_FAILED_ALREADY_STARTED)
                        .add("ADVERTISE_FAILED_DATA_TOO_LARGE", ADVERTISE_FAILED_DATA_TOO_LARGE)
                        .add(
                                "ADVERTISE_FAILED_FEATURE_UNSUPPORTED",
                                ADVERTISE_FAILED_FEATURE_UNSUPPORTED)
                        .add("ADVERTISE_FAILED_INTERNAL_ERROR", ADVERTISE_FAILED_INTERNAL_ERROR)
                        .add(
                                "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS",
                                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS)
                        .build();

        public DefaultAdvertiseCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.e("Bluetooth LE advertising started with settings: " + settingsInEffect.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, "onStartSuccess");
            Bundle advertiseSettings =
                    JsonSerializer.serializeBleAdvertisingSettings(settingsInEffect);
            event.getData().putBundle("SettingsInEffect", advertiseSettings);
            sEventCache.postEvent(event);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e("Bluetooth LE advertising failed to start with error code: " + errorCode);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onStartFailure");
            final String errorCodeString = ADVERTISE_FAILURE_ERROR_CODE.getString(errorCode);
            event.getData().putString("ErrorCode", errorCodeString);
            sEventCache.postEvent(event);
        }
    }

    @Override
    public void shutdown() {
        for (AdvertiseCallback callback : mAdvertiseCallbacks.values()) {
            mAdvertiser.stopAdvertising(callback);
        }
        mAdvertiseCallbacks.clear();
    }
}
