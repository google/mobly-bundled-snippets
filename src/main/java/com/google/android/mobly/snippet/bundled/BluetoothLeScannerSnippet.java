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
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Bundle;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

    public BluetoothLeScannerSnippet() {
        mScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    }

    @AsyncRpc(description = "Start BLE scan.")
    public void bleStartScan(String callbackId) throws BluetoothLeScanSnippetException {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            throw new BluetoothLeScanSnippetException(
                    "Bluetooth is disabled, cannot start BLE scan.");
        }
        DefaultScanCallback callback = new DefaultScanCallback(callbackId);
        mScanner.startScan(callback);
        mScanCallbacks.put(callbackId, callback);
    }

    @Rpc(description = "Stop BLE scan.")
    public void bleStopScan(String id) throws BluetoothLeScanSnippetException {
        if (!mScanCallbacks.containsKey(id)) {
            throw new BluetoothLeScanSnippetException("No ongoing scan with ID: " + id);
        }
        mScanner.stopScan(mScanCallbacks.remove(id));
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

        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("Got Bluetooth LE scan result.");
            SnippetEvent event = new SnippetEvent(mCallbackId, "onScanResult");
            final String nameCallbackType = "CallbackType";
            switch (callbackType) {
                case ScanSettings.CALLBACK_TYPE_ALL_MATCHES:
                    event.getData().putString(nameCallbackType, "CALLBACK_TYPE_ALL_MATCHES");
                    break;
                case ScanSettings.CALLBACK_TYPE_FIRST_MATCH:
                    event.getData().putString(nameCallbackType, "CALLBACK_TYPE_FIRST_MATCH");
                    break;
                case ScanSettings.CALLBACK_TYPE_MATCH_LOST:
                    event.getData().putString(nameCallbackType, "CALLBACK_TYPE_MATCH_LOST");
                    break;
            }
            event.getData().putBundle("result", mJsonSerializer.serializeBleScanResult(result));
            mEventCache.postEvent(event);
        }

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

        public void onScanFailed(int errorCode) {
            Log.e("Bluetooth LE scan failed with error code: " + errorCode);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onScanFailed");
            String errorCodeString;
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    errorCodeString = "SCAN_FAILED_ALREADY_STARTED";
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorCodeString = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorCodeString = "SCAN_FAILED_FEATURE_UNSUPPORTED";
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    errorCodeString = "SCAN_FAILED_INTERNAL_ERROR";
                    break;
                default:
                    errorCodeString = "UNKNOWN";
                    break;
            }
            event.getData().putString("ErrorCode", errorCodeString);
            mEventCache.postEvent(event);
        }
    }
}
