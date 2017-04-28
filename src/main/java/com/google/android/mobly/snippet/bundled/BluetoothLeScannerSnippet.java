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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.support.test.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

/** Snippet class exposing Android APIs in WifiManager. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class BluetoothLeScannerSnippet implements Snippet {
    private static class BluetoothLeAdvertiserSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothLeAdvertiserSnippetException(String msg) {
            super(msg);
        }
    }

    private final BluetoothManager mBluetoothManager;
    private final BluetoothLeScanner mScanner;
    private final Context mContext;
    private final EventCache mEventCache = EventCache.getInstance();
    private final HashMap<String, AdvertiseCallback> mAdvertiseCallbacks = new HashMap<>();

    public BluetoothLeScannerSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mScanner = mBluetoothManager.getAdapter().getBluetoothLeScanner();
    }

    @AsyncRpc(description = "Start BLE advertising.")
    public void bleStartScan(String callbackId) {
        DefaultScanCallback callback = new DefaultScanCallback((callbackId));
        mScanner.startScan(callback);
    }

    @Override
    public void shutdown() {}

    private class DefaultScanCallback extends ScanCallback {
        private final String mCallbackId;
        public DefaultScanCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        public void onScanResult(int callbackType, ScanResult result) {
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
        }

        /**
         * Callback when batch results are delivered.
         *
         * @param results List of scan results that are previously scanned.
         */
        public void onBatchScanResults(List<ScanResult> results) {
        }

        /**
         * Callback when scan could not be started.
         *
         * @param errorCode Error code (one of SCAN_FAILED_*) for scan failure.
         */
        public void onScanFailed(int errorCode) {
        }
    }
}
