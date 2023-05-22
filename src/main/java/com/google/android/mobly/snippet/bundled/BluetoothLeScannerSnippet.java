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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Bundle;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.MbsEnums;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Snippet class exposing Android APIs in WifiManager. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class BluetoothLeScannerSnippet implements Snippet {
    private static class BluetoothLeScanSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothLeScanSnippetException(String msg) {
            super(msg);
        }
    }

    private final BluetoothLeScanner mScanner;
    private final EventCache mEventCache = EventCache.getInstance();
    private final HashMap<String, ScanCallback> mScanCallbacks = new HashMap<>();
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private long bleScanStartTime = 0;

    public BluetoothLeScannerSnippet() {
        mScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    }

    /**
     * Start a BLE scan.
     *
     * @param callbackId
     * @param scanFilters A JSONArray representing a list of {@link ScanFilter} object for finding
     *     exact BLE devices. E.g.
     *     <pre>
     *          [
     *            {
     *              "ServiceUuid": (A string representation of {@link ParcelUuid}),
     *            },
     *          ]
     *     </pre>
     *
     * @param scanSettings A JSONObject representing a {@link ScanSettings} object which is the
     *     Settings for the scan. E.g.
     *     <pre>
     *          {
     *            'ScanMode': 'SCAN_MODE_LOW_LATENCY',
     *          }
     *     </pre>
     *
     * @throws BluetoothLeScanSnippetException
     */
    @RpcMinSdk(Build.VERSION_CODES.LOLLIPOP_MR1)
    @AsyncRpc(description = "Start BLE scan.")
    public void bleStartScan(
            String callbackId,
            @RpcOptional JSONArray scanFilters,
            @RpcOptional JSONObject scanSettings)
            throws BluetoothLeScanSnippetException, JSONException {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            throw new BluetoothLeScanSnippetException(
                    "Bluetooth is disabled, cannot start BLE scan.");
        }
        DefaultScanCallback callback = new DefaultScanCallback(callbackId);
        if (scanFilters == null && scanSettings == null) {
            mScanner.startScan(callback);
        } else {
            ArrayList<ScanFilter> filters = new ArrayList<>();
            for (int i = 0; i < scanFilters.length(); i++) {
                filters.add(JsonDeserializer.jsonToScanFilter(scanFilters.getJSONObject(i)));
            }
            ScanSettings settings = JsonDeserializer.jsonToScanSettings(scanSettings);
            mScanner.startScan(filters, settings, callback);
        }
        bleScanStartTime = System.currentTimeMillis();
        mScanCallbacks.put(callbackId, callback);
    }

    /**
     * Stop a BLE scan.
     *
     * @param callbackId The callbackId corresponding to the {@link
     *     BluetoothLeScannerSnippet#bleStartScan} call that started the scan.
     * @throws BluetoothLeScanSnippetException
     */
    @RpcMinSdk(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Rpc(description = "Stop a BLE scan.")
    public void bleStopScan(String callbackId) throws BluetoothLeScanSnippetException {
        ScanCallback callback = mScanCallbacks.remove(callbackId);
        if (callback == null) {
            throw new BluetoothLeScanSnippetException("No ongoing scan with ID: " + callbackId);
        }
        mScanner.stopScan(callback);
    }

    @Override
    public void shutdown() {
        for (ScanCallback callback : mScanCallbacks.values()) {
            mScanner.stopScan(callback);
        }
        mScanCallbacks.clear();
    }

    private class DefaultScanCallback extends ScanCallback {
        private final String mCallbackId;

        public DefaultScanCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("Got Bluetooth LE scan result.");
            long bleScanOnResultTime = System.currentTimeMillis();
            SnippetEvent event = new SnippetEvent(mCallbackId, "onScanResult");
            String callbackTypeString =
                    MbsEnums.BLE_SCAN_RESULT_CALLBACK_TYPE.getString(callbackType);
            event.getData().putString("CallbackType", callbackTypeString);
            event.getData().putBundle("result", mJsonSerializer.serializeBleScanResult(result));
            event.getData()
                    .putLong("StartToResultTimeDeltaMs", bleScanOnResultTime - bleScanStartTime);
            mEventCache.postEvent(event);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i("Got Bluetooth LE batch scan results.");
            SnippetEvent event = new SnippetEvent(mCallbackId, "onBatchScanResult");
            ArrayList<Bundle> resultList = new ArrayList<>(results.size());
            for (ScanResult result : results) {
                resultList.add(mJsonSerializer.serializeBleScanResult(result));
            }
            event.getData().putParcelableArrayList("results", resultList);
            mEventCache.postEvent(event);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Bluetooth LE scan failed with error code: " + errorCode);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onScanFailed");
            String errorCodeString = MbsEnums.BLE_SCAN_FAILED_ERROR_CODE.getString(errorCode);
            event.getData().putString("ErrorCode", errorCodeString);
            mEventCache.postEvent(event);
        }
    }
}
