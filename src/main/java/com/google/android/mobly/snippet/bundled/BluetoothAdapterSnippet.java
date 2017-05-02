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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;

/** Snippet class exposing Android APIs in BluetoothAdapter. */
public class BluetoothAdapterSnippet implements Snippet {
    private static class BluetoothAdapterSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothAdapterSnippetException(String msg) {
            super(msg);
        }
    }

    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private final ArrayList<BluetoothDevice> mDiscoveryResults = new ArrayList<>();
    private volatile boolean mIsScanResultAvailable = false;

    public BluetoothAdapterSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Rpc(description = "Enable bluetooth with a 30s timeout.")
    public void btEnable() throws BluetoothAdapterSnippetException, InterruptedException {
        if (!mBluetoothAdapter.enable()) {
            throw new BluetoothAdapterSnippetException("Failed to start enabling bluetooth");
        }
        if (!Utils.waitUntil(() -> mBluetoothAdapter.isEnabled(), 30)) {
            throw new BluetoothAdapterSnippetException("Bluetooth did not turn on within 30s.");
        }
    }

    @Rpc(description = "Disable bluetooth with a 30s timeout.")
    public void btDisable() throws BluetoothAdapterSnippetException, InterruptedException {
        if (!mBluetoothAdapter.disable()) {
            throw new BluetoothAdapterSnippetException("Failed to start disabling bluetooth");
        }
        if (!Utils.waitUntil(() -> !mBluetoothAdapter.isEnabled(), 30)) {
            throw new BluetoothAdapterSnippetException("Bluetooth did not turn off within 30s.");
        }
    }

    @Rpc(
        description =
                "Get bluetooth discovery results, which is a list of serialized BluetoothDevice objects."
    )
    public JSONArray btGetCachedScanResults() throws JSONException {
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
    public JSONArray btDiscoveryAndGetResults()
            throws InterruptedException, JSONException, BluetoothAdapterSnippetException {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mDiscoveryResults.clear();
        mIsScanResultAvailable = false;
        BroadcastReceiver receiver = new BluetoothScanReceiver();
        mContext.registerReceiver(receiver, filter);
        try {
            if (!mBluetoothAdapter.startDiscovery()) {
                throw new BluetoothAdapterSnippetException(
                        "Failed to initiate Bluetooth Discovery.");
            }
            if (!Utils.waitUntil(() -> mIsScanResultAvailable, 60)) {
                throw new BluetoothAdapterSnippetException(
                        "Failed to get discovery results after 1 min, timeout!");
            }
        } finally {
            mContext.unregisterReceiver(receiver);
        }
        return btGetCachedScanResults();
    }

    @Rpc(description = "Get the list of paired bluetooth devices.")
    public JSONArray btGetPairedDevices()
            throws BluetoothAdapterSnippetException, InterruptedException, JSONException {
        JSONArray pairedDevices = new JSONArray();
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            pairedDevices.put(mJsonSerializer.toJson(device));
        }
        return pairedDevices;
    }

    /**
     * Enable Bluetooth HCI snoop log collection.
     *
     * <p>The file can be pulled from `/sdcard/btsnoop_hci.log`.
     *
     * @throws Throwable
     */
    @RpcMinSdk(Build.VERSION_CODES.KITKAT)
    @Rpc(description = "Enable Bluetooth HCI snoop log for debugging.")
    public void btEnableHciSnoopLog() throws Throwable {
        if (!(boolean) Utils.invokeByReflection(mBluetoothAdapter, "configHciSnoopLog", true)) {
            throw new BluetoothAdapterSnippetException("Failed to enable HCI snoop log.");
        }
    }

    @RpcMinSdk(Build.VERSION_CODES.KITKAT)
    @Rpc(description = "Disable Bluetooth HCI snoop log.")
    public void btDisableHciSnoopLog() throws Throwable {
        if (!(boolean) Utils.invokeByReflection(mBluetoothAdapter, "configHciSnoopLog", false)) {
            throw new BluetoothAdapterSnippetException("Failed to disable HCI snoop log.");
        }
    }

    @Override
    public void shutdown() {}

    private class BluetoothScanReceiver extends BroadcastReceiver {

        /**
         * The receiver gets an ACTION_FOUND intent whenever a new device is found.
         * ACTION_DISCOVERY_FINISHED intent is received when the discovery process ends.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mIsScanResultAvailable = true;
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device =
                        (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mDiscoveryResults.add(device);
            }
        }
    }
}
