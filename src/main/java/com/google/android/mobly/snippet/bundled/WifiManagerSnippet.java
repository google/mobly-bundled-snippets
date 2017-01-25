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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.support.test.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Snippet class exposing Android APIs in WifiManager. */
public class WifiManagerSnippet implements Snippet {
    private static class WifiManagerSnippetException extends Exception {
        public WifiManagerSnippetException(String msg) {
            super(msg);
        }
    }

    private final WifiManager mWifiManager;
    private final Context mContext;
    private static final String TAG = "WifiManagerSnippet";
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private volatile boolean mIsScanning = false;
    private volatile boolean mIsScanResultAvailable = false;

    public WifiManagerSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    @Rpc(description = "Turns on Wi-Fi with a 30s timeout.")
    public void wifiEnable() throws InterruptedException, WifiManagerSnippetException {
        if (!mWifiManager.setWifiEnabled(true)) {
            throw new WifiManagerSnippetException("Failed to initiate enabling Wi-Fi.");
        }
        Utils.Predicate expectedState =
                () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED;
        if (!Utils.waitUntil(expectedState, 30)) {
            throw new WifiManagerSnippetException("Failed to enable Wi-Fi after 30s, timeout!");
        }
    }

    @Rpc(description = "Turns on Wi-Fi with a 30s timeout.")
    public void wifiDisable() throws InterruptedException, WifiManagerSnippetException {
        if (!mWifiManager.setWifiEnabled(false)) {
            throw new WifiManagerSnippetException("Failed to initiate disabling Wi-Fi.");
        }
        Utils.Predicate expectedState =
                () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED;
        if (!Utils.waitUntil(expectedState, 30)) {
            throw new WifiManagerSnippetException("Failed to disable Wi-Fi after 30s, timeout!");
        }
    }

    @Rpc(description = "Trigger Wi-Fi scan.")
    public void wifiStartScan() throws WifiManagerSnippetException {
        if (!mWifiManager.startScan()) {
            throw new WifiManagerSnippetException("Failed to initiate Wi-Fi scan.");
        }
    }

    @Rpc(description = "Get Wi-Fi scan results.")
    public JSONArray wifiGetScanResults() throws JSONException {
        JSONArray results = new JSONArray();
        for (ScanResult result : mWifiManager.getScanResults()) {
            results.put(mJsonSerializer.toJson(result));
        }
        return results;
    }

    @Rpc(description = "Start scan, wait for scan to complete, and return results.")
    public JSONArray wifiScanAndGetResults()
            throws InterruptedException, JSONException, WifiManagerSnippetException {
        mContext.registerReceiver(
                new WifiScanReceiver(),
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiStartScan();
        mIsScanResultAvailable = false;
        mIsScanning = true;
        Utils.Predicate expectedState = () -> mIsScanResultAvailable;
        if (!Utils.waitUntil(expectedState, 2 * 60)) {
            throw new WifiManagerSnippetException(
                    "Failed to get scan results after 2min, timeout!");
        }
        return wifiGetScanResults();
    }

    /**
     * Connect to a Wi-Fi network.
     *
     * @param wifiNetworkConfig A JSON object that contains the info required to connect to a Wi-Fi
     *     network. It follows the fields of WifiConfiguration type, e.g. {"SSID": "myWifi",
     *     "password": "12345678"}.
     * @throws InterruptedException
     * @throws JSONException
     * @throws WifiManagerSnippetException
     */
    @Rpc(description = "Connects to a Wi-Fi network.")
    public void wifiConnect(JSONObject wifiNetworkConfig)
            throws InterruptedException, JSONException, WifiManagerSnippetException {
        Log.d("Got network config: " + wifiNetworkConfig);
        WifiConfiguration wifiConfig = JsonDeserializer.jsonToWifiConfig(wifiNetworkConfig);
        int networkId = mWifiManager.addNetwork(wifiConfig);
        Log.d("Added network '" + wifiConfig.SSID + "' with ID " + networkId);
        if (networkId < 0) {
            throw new WifiManagerSnippetException("Got negative network Id.");
        }
        mWifiManager.disconnect();
        if (!mWifiManager.enableNetwork(networkId, true)) {
            throw new WifiManagerSnippetException(
                    "Failed to enable Wi-Fi network of ID: " + networkId);
        }
        if (!mWifiManager.reconnect()) {
            throw new WifiManagerSnippetException(
                    "Failed to reconnect to Wi-Fi network of ID: " + networkId);
        }
        Utils.Predicate expectedState =
                () -> mWifiManager.getConnectionInfo().getSSID().equals(wifiConfig.SSID);
        if (!Utils.waitUntil(expectedState, 90)) {
            throw new WifiManagerSnippetException(
                    "Failed to connect to Wi-Fi network "
                            + wifiNetworkConfig.toString()
                            + ", timeout!");
        }
    }

    @Rpc(description = "Disconnect current Wi-Fi connection.")
    public void wifiDisconnect() throws InterruptedException, WifiManagerSnippetException {
        if (!mWifiManager.disconnect()) {
            throw new WifiManagerSnippetException("Failed to initiate disconnecting Wi-Fi.");
        }
        Utils.Predicate expectedState = () -> mWifiManager.getConnectionInfo().getSSID().isEmpty();
        if (!Utils.waitUntil(expectedState, 30)) {
            throw new WifiManagerSnippetException("Failed to disconnect Wi-Fi after 30s, timeout!");
        }
    }

    @Rpc(description = "Forget a configured Wi-Fi network by its network ID.")
    public void wifiRemoveNetwork(Integer networkId) throws WifiManagerSnippetException {
        if (!mWifiManager.removeNetwork(networkId)) {
            throw new WifiManagerSnippetException("Failed to remove network of ID: " + networkId);
        }
    }

    @Rpc(description = "Return a list of all the configured wifi networks.")
    public ArrayList<JSONObject> wifiGetConfiguredNetworks() throws JSONException {
        ArrayList<JSONObject> networks = new ArrayList<>();
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            networks.add(mJsonSerializer.toJson(config));
        }
        return networks;
    }

    @Rpc(description = "Get the information about the active Wi-Fi connection.")
    public JSONObject wifiGetConnectionInfo() throws JSONException {
        return mJsonSerializer.toJson(mWifiManager.getConnectionInfo());
    }

    @Rpc(description = "Get the info from last successful DHCP request.")
    public JSONObject wifiGetDhcpInfo() throws JSONException {
        return mJsonSerializer.toJson(mWifiManager.getDhcpInfo());
    }

    @Override
    public void shutdown() {}

    private class WifiScanReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                mIsScanning = false;
                mIsScanResultAvailable = true;
            }
        }
    }
}
