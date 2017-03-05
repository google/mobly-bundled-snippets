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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.support.test.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

/** Snippet class exposing Android APIs in BluetoothAdapter. */
public class BluetoothAdapterSnippet implements Snippet {
    private static class BluetoothAdapterSnippetException extends Exception {
        public BluetoothAdapterSnippetException(String msg) {
            super(msg);
        }
    }

    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private ArrayList<BluetoothDevice> mDiscoveryResults = new ArrayList<>();
    private volatile boolean mIsScanning = false;
    private volatile boolean mIsScanResultAvailable = false;

    public BluetoothAdapterSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Rpc(description = "Enable bluetooth with a 30s timeout.")
    public void bluetoothEnable() throws BluetoothAdapterSnippetException, InterruptedException {
        if (!mBluetoothAdapter.enable()) {
            throw new BluetoothAdapterSnippetException("Failed to start enabling bluetooth");
        }
        if (!Utils.waitUntil(() -> mBluetoothAdapter.isEnabled(), 30)) {
            throw new BluetoothAdapterSnippetException("Bluetooth did not turn on within 30s.");
        }
    }

    @Rpc(description = "Disable bluetooth with a 30s timeout.")
    public void bluetoothDisable() throws BluetoothAdapterSnippetException, InterruptedException {
        if (!mBluetoothAdapter.disable()) {
            throw new BluetoothAdapterSnippetException("Failed to start disabling bluetooth");
        }
        if (!Utils.waitUntil(() -> !mBluetoothAdapter.isEnabled(), 30)) {
            throw new BluetoothAdapterSnippetException("Bluetooth did not turn off within 30s.");
        }
    }

    @Rpc(description = "Trigger bluetooth discovery.")
    public void bluetoothStartDiscovery() throws BluetoothAdapterSnippetException {
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        if (!mBluetoothAdapter.startDiscovery()) {
            throw new BluetoothAdapterSnippetException("Failed to initiate Bluetooth Discovery.");
        }
    }

    @Rpc(
        description =
                "Get bluetooth discovery results, which is a list of serialized BluetoothDevice objects."
    )
    public JSONArray bluetoothGetCachedScanResults() throws JSONException {
        JSONArray results = new JSONArray();
        for (BluetoothDevice result : mDiscoveryResults) {
            results.put(mJsonSerializer.toJson(result));
        }
        return results;
    }

    @Rpc(
        description =
                "Start discovery, wait for discovery to complete, and return results, which is a list of "
                        + "serialized BluetoothDevice objects."
    )
    public JSONArray bluetoothDiscoveryAndGetResults()
            throws InterruptedException, JSONException, BluetoothAdapterSnippetException {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        BroadcastReceiver mReceiver = new BluetoothScanReceiver();
        mContext.registerReceiver(mReceiver, filter);
        mDiscoveryResults.clear();
        bluetoothStartDiscovery();
        mIsScanResultAvailable = false;
        mIsScanning = true;
        if (!Utils.waitUntil(() -> mIsScanResultAvailable, 60)) {
            throw new BluetoothAdapterSnippetException(
                    "Failed to get discovery results after 1 min, timeout!");
        }
        mContext.unregisterReceiver(mReceiver);
        return bluetoothGetCachedScanResults();
    }

    @Rpc(description = "Get the list of paired bluetooth devices")
    public JSONArray bluetoothGetPairedDevices() throws BluetoothAdapterSnippetException, InterruptedException, JSONException {
        JSONArray pairedDevices = new JSONArray();
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices())
            pairedDevices.put(mJsonSerializer.toJson(device));
        return pairedDevices;
    }

    @Override
    public void shutdown() {}

    private class BluetoothScanReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mIsScanning = false;
                mIsScanResultAvailable = true;
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mDiscoveryResults.add(device);
            }
        }
    }
}
