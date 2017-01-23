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
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Snippet class exposing Android APIs in WifiManager.
 */
public class WifiManagerSnippet implements Snippet {
    private final Lock lock = new ReentrantLock();
    final Condition scanResultsAvailable = lock.newCondition();
    private WifiManager mWifiManager;
    private Context mContext;
    private WifiManager.WifiLock mWifiLock;
    private String TAG = "WifiManagerSnippet";
    private JsonBuilder mJsonBuilder = new JsonBuilder();
    private boolean isScanning = false;

    public WifiManagerSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    public static WifiConfiguration jsonToWifiConfig(JSONObject j) throws JSONException {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + j.getString("SSID") + "\"";
        config.hiddenSSID = j.optBoolean("hiddenSSID", false);
        if (j.has("password")) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.preSharedKey = "\"" + j.getString("password") + "\"";
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        return config;
    }

    @Rpc(description = "Turns on Wi-Fi with a 30s timeout.")
    public void wifiEnable() throws InterruptedException, WifiManagerSnippetException {
        if (!mWifiManager.setWifiEnabled(true)) {
            throw new WifiManagerSnippetException(
                    "wifiEnable", "Failed to initiate enabling Wi-Fi.");
        }
        Utils.Predicate waitCondition =
                () -> mWifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED;
        if (!Utils.waitAndCheck(waitCondition, 30)) {
            throw new WifiManagerSnippetException(
                    "wifiEnable", "Failed to enable Wi-Fi after 30s, timeout!");
        }
    }

    @Rpc(description = "Turns on Wi-Fi with a 30s timeout.")
    public void wifiDisable() throws InterruptedException, WifiManagerSnippetException {
        if (!mWifiManager.setWifiEnabled(false)) {
            throw new WifiManagerSnippetException(
                    "wifiDisable", "Failed to initiate disabling Wi-Fi.");
        }
        Utils.Predicate waitCondition =
                () -> mWifiManager.getWifiState() != WifiManager.WIFI_STATE_DISABLED;
        if (!Utils.waitAndCheck(waitCondition, 30)) {
            throw new WifiManagerSnippetException(
                    "wifiEnable", "Failed to disable Wi-Fi after 30s, timeout!");
        }
    }

    @Rpc(description = "Trigger Wi-Fi scan.")
    public void wifiStartScan() throws WifiManagerSnippetException {
        if (!mWifiManager.startScan()) {
            throw new WifiManagerSnippetException(
                    "wifiStartScan", "Failed to initiate Wi-Fi scan.");
        }
    }

    @Rpc(description = "Get Wi-Fi scan results.")
    public JSONArray wifiGetScanResults() throws JSONException {
        JSONArray results = new JSONArray();
        for (ScanResult result : mWifiManager.getScanResults()) {
            results.put(mJsonBuilder.buildScanResult(result));
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
        isScanning = true;
        boolean isTimeout = false;
        while (isScanning) {
            isTimeout = !scanResultsAvailable.await(2, TimeUnit.MINUTES);
        }
        if (isTimeout) {
            throw new WifiManagerSnippetException(
                    "wifiScanAndGetResults", "Failed to get scan results after 2min, timeout!");
        }
        return wifiGetScanResults();
    }

    @Rpc(description = "Connects to a Wi-Fi network.")
    public void wifiConnect(JSONObject wifiNetworkConfig)
            throws InterruptedException, JSONException, WifiManagerSnippetException {
        Log.d("Got network config: " + wifiNetworkConfig);
        WifiConfiguration wifiConfig = jsonToWifiConfig(wifiNetworkConfig);
        int networkId = mWifiManager.addNetwork(wifiConfig);
        Log.d("Added network '" + wifiConfig.SSID + "' with ID " + networkId);
        if (networkId < 0) {
            throw new WifiManagerSnippetException("wifiConnect", "Got negative network Id.");
        }
        mWifiManager.disconnect();
        if (!mWifiManager.enableNetwork(networkId, true)) {
            throw new WifiManagerSnippetException(
                    "wifiConnect", "Failed to enable Wi-Fi network of ID: " + networkId);
        }
        if (!mWifiManager.reconnect()) {
            throw new WifiManagerSnippetException(
                    "wifiConnect", "Failed to reconnect to Wi-Fi network of ID: " + networkId);
        }
        Utils.Predicate waitCondition =
                () -> !mWifiManager.getConnectionInfo().getSSID().equals(wifiConfig.SSID);
        if (!Utils.waitAndCheck(waitCondition, 90)) {
            throw new WifiManagerSnippetException(
                    "wifiConnect",
                    "Failed to connect to Wi-Fi network "
                            + wifiNetworkConfig.toString()
                            + ", timeout!");
        }
    }

    @Rpc(description = "Forget a configured Wi-Fi network by its network ID.")
    public void wifiRemoveNetwork(Integer networkId) throws WifiManagerSnippetException {
        if (!mWifiManager.removeNetwork(networkId)) {
            throw new WifiManagerSnippetException(
                    "wifiRemoveNetwork", "Failed to remove network of ID: " + networkId);
        }
    }

    @Rpc(description = "Return a list of all the configured wifi networks.")
    public ArrayList<JSONObject> wifiGetConfiguredNetworks() throws JSONException {
        ArrayList<JSONObject> networks = new ArrayList<>();
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            networks.add(mJsonBuilder.buildWifiConfiguration(config));
        }
        return networks;
    }

    @Rpc(description = "Acquires the Wi-Fi lock in full mode.")
    public void wifiLockAcquireFull() {
        acquireWifiLock(WifiManager.WIFI_MODE_FULL);
    }

    @Rpc(description = "Acquires the  Wi-Fi lock in scan-only mode.")
    public void wifiLockAcquireScanOnly() {
        acquireWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY);
    }

    @Rpc(description = "Releases the Wi-Fi lock.")
    public void wifiLockRelease() {
        if (mWifiLock != null) {
            mWifiLock.release();
            mWifiLock = null;
        }
    }

    @Rpc(description = "Get the information about the active Wi-Fi connection.")
    public JSONObject wifiGetConnectionInfo() throws JSONException {
        return mJsonBuilder.buildWifiInfo(mWifiManager.getConnectionInfo());
    }

    @Rpc(description = "Get the info from last successful DHCP request.")
    public JSONObject wifiGetDhcpInfo() throws JSONException {
        return mJsonBuilder.buildDhcpInfo(mWifiManager.getDhcpInfo());
    }

    private void acquireWifiLock(int wifiMode) {
        wifiLockRelease();
        if (mWifiLock == null) {
            mWifiLock = mWifiManager.createWifiLock(wifiMode, TAG);
            mWifiLock.acquire();
        }
    }

    @Override
    public void shutdown() {
        wifiLockRelease();
    }

    private static class WifiManagerSnippetException extends Exception {
        public final String rpcMethod;

        public WifiManagerSnippetException(String msg) {
            super(msg);
            this.rpcMethod = null;
        }

        public WifiManagerSnippetException(String rpcMethod, String msg) {
            super(msg);
            this.rpcMethod = rpcMethod;
        }

        @Override
        public String toString() {
            if (this.rpcMethod != null) {
                StringBuilder strBuilder = new StringBuilder();
                strBuilder
                        .append("Exception in ")
                        .append(this.rpcMethod)
                        .append(": ")
                        .append(this.getMessage());
                return strBuilder.toString();
            } else {
                return super.toString();
            }
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                isScanning = false;
                scanResultsAvailable.signal();
            }
        }
    }
}
