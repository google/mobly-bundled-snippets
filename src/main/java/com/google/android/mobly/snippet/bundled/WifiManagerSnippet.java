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
import android.support.annotation.Nullable;
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
        private static final long serialVersionUID = 1;

        public WifiManagerSnippetException(String msg) {
            super(msg);
        }
    }

    private final WifiManager mWifiManager;
    private final Context mContext;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
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
        if (!Utils.waitUntil(
                () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED, 30)) {
            throw new WifiManagerSnippetException("Failed to enable Wi-Fi after 30s, timeout!");
        }
    }

    @Rpc(description = "Turns off Wi-Fi with a 30s timeout.")
    public void wifiDisable() throws InterruptedException, WifiManagerSnippetException {
        if (!mWifiManager.setWifiEnabled(false)) {
            throw new WifiManagerSnippetException("Failed to initiate disabling Wi-Fi.");
        }
        if (!Utils.waitUntil(
                () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED, 30)) {
            throw new WifiManagerSnippetException("Failed to disable Wi-Fi after 30s, timeout!");
        }
    }

    @Rpc(description = "Trigger Wi-Fi scan.")
    public void wifiStartScan() throws WifiManagerSnippetException {
        if (!mWifiManager.startScan()) {
            throw new WifiManagerSnippetException("Failed to initiate Wi-Fi scan.");
        }
    }

    @Rpc(
        description =
                "Get Wi-Fi scan results, which is a list of serialized WifiScanResult objects."
    )
    public JSONArray wifiGetCachedScanResults() throws JSONException {
        JSONArray results = new JSONArray();
        for (ScanResult result : mWifiManager.getScanResults()) {
            results.put(mJsonSerializer.toJson(result));
        }
        return results;
    }

    @Rpc(
        description =
                "Start scan, wait for scan to complete, and return results, which is a list of "
                        + "serialized WifiScanResult objects."
    )
    public JSONArray wifiScanAndGetResults()
            throws InterruptedException, JSONException, WifiManagerSnippetException {
        mContext.registerReceiver(
                new WifiScanReceiver(),
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiStartScan();
        mIsScanResultAvailable = false;
        if (!Utils.waitUntil(() -> mIsScanResultAvailable, 2 * 60)) {
            throw new WifiManagerSnippetException(
                    "Failed to get scan results after 2min, timeout!");
        }
        return wifiGetCachedScanResults();
    }

    @Rpc(
        description =
                "Connects to a Wi-Fi network. This covers the common network types like open and "
                        + "WPA2."
    )
    public void wifiConnectSimple(String ssid, @Nullable String password)
            throws InterruptedException, JSONException, WifiManagerSnippetException {
        JSONObject config = new JSONObject();
        config.put("SSID", ssid);
        if (password != null) {
            config.put("password", password);
        }
        wifiConnect(config);
    }

    /**
     * Gets the {@link WifiConfiguration} of a Wi-Fi network that has already been configured.
     *
     * <p>If the network has not been configured, returns null.
     *
     * <p>A network is configured if a WifiConfiguration was created for it and added with {@link
     * WifiManager#addNetwork(WifiConfiguration)}.
     */
    private WifiConfiguration getExistingConfiguredNetwork(String ssid) {
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            if (config.SSID.equals(ssid)) {
                return config;
            }
        }
        return null;
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
        // Return directly if network is already connected.
        String connectedSsid = mWifiManager.getConnectionInfo().getSSID();
        if (connectedSsid.equals(wifiConfig.SSID)) {
            Log.d("Network " + connectedSsid + " is already connected.");
            return;
        }
        // If the network is already added but not connected, update the configuration first.
        WifiConfiguration existingConfig = getExistingConfiguredNetwork(wifiConfig.SSID);
        if (existingConfig != null) {
            Log.d("Update the configuration of network " + existingConfig.SSID + ".");
            mWifiManager.removeNetwork(existingConfig.networkId);
        }
        int networkId = mWifiManager.addNetwork(wifiConfig);
        mWifiManager.disconnect();
        if (!mWifiManager.enableNetwork(networkId, true)) {
            throw new WifiManagerSnippetException(
                    "Failed to enable Wi-Fi network of ID: " + networkId);
        }
        if (!mWifiManager.reconnect()) {
            throw new WifiManagerSnippetException(
                    "Failed to reconnect to Wi-Fi network of ID: " + networkId);
        }
        if (!Utils.waitUntil(
                () -> mWifiManager.getConnectionInfo().getSSID().equals(wifiConfig.SSID), 90)) {
            throw new WifiManagerSnippetException(
                    "Failed to connect to Wi-Fi network "
                            + wifiNetworkConfig.toString()
                            + ", timeout!");
        }
        Log.d(
                "Connected to network '"
                        + mWifiManager.getConnectionInfo().getSSID()
                        + "' with ID "
                        + mWifiManager.getConnectionInfo().getNetworkId());
    }

    @Rpc(
        description =
                "Forget a configured Wi-Fi network by its network ID, which is part of the"
                        + " WifiConfiguration."
    )
    public void wifiRemoveNetwork(Integer networkId) throws WifiManagerSnippetException {
        if (!mWifiManager.removeNetwork(networkId)) {
            throw new WifiManagerSnippetException("Failed to remove network of ID: " + networkId);
        }
    }

    @Rpc(
        description =
                "Get the list of configured Wi-Fi networks, each is a serialized "
                        + "WifiConfiguration object."
    )
    public ArrayList<JSONObject> wifiGetConfiguredNetworks() throws JSONException {
        ArrayList<JSONObject> networks = new ArrayList<>();
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            networks.add(mJsonSerializer.toJson(config));
        }
        return networks;
    }

    @Rpc(
        description =
                "Get the information about the active Wi-Fi connection, which is a serialized "
                        + "WifiInfo object."
    )
    public JSONObject wifiGetConnectionInfo() throws JSONException {
        return mJsonSerializer.toJson(mWifiManager.getConnectionInfo());
    }

    @Rpc(
        description =
                "Get the info from last successful DHCP request, which is a serialized DhcpInfo "
                        + "object."
    )
    public JSONObject wifiGetDhcpInfo() throws JSONException {
        return mJsonSerializer.toJson(mWifiManager.getDhcpInfo());
    }

    @Rpc(description = "Check whether Wi-Fi Soft AP (hotspot) is enabled.")
    public boolean wifiIsApEnabled() throws Throwable {
        return (boolean) Utils.invokeByReflection(mWifiManager, "isWifiApEnabled");
    }

    /**
     * Enable Wi-Fi Soft AP (hotspot).
     *
     * @param configuration The same format as the param wifiNetworkConfig param for wifiConnect.
     * @throws Throwable
     */
    @Rpc(description = "Enable Wi-Fi Soft AP (hotspot).")
    public void wifiEnableSoftAp(@Nullable JSONObject configuration) throws Throwable {
        // If no configuration is provided, the existing configuration would be used.
        WifiConfiguration wifiConfiguration = null;
        if (configuration != null) {
            wifiConfiguration = JsonDeserializer.jsonToWifiConfig(configuration);
            // Have to trim off the extra quotation marks since Soft AP logic interprets
            // WifiConfiguration.SSID literally, unlike the WifiManager connection logic.
            wifiConfiguration.SSID = JsonSerializer.trimQuotationMarks(wifiConfiguration.SSID);
        }
        if (!(boolean)
                Utils.invokeByReflection(
                        mWifiManager, "setWifiApEnabled", wifiConfiguration, true)) {
            throw new WifiManagerSnippetException("Failed to initiate turning on Wi-Fi Soft AP.");
        }
        if (!Utils.waitUntil(() -> wifiIsApEnabled() == true, 60)) {
            throw new WifiManagerSnippetException(
                    "Timed out after 60s waiting for Wi-Fi Soft AP state to turn on with configuration: "
                            + configuration);
        }
    }

    /** Disables Wi-Fi Soft AP (hotspot). */
    @Rpc(description = "Disable Wi-Fi Soft AP (hotspot).")
    public void wifiDisableSoftAp() throws Throwable {
        if (!(boolean)
                Utils.invokeByReflection(
                        mWifiManager,
                        "setWifiApEnabled",
                        null /* No configuration needed for disabling */,
                        false)) {
            throw new WifiManagerSnippetException("Failed to initiate turning off Wi-Fi Soft AP.");
        }
        if (!Utils.waitUntil(() -> wifiIsApEnabled() == false, 60)) {
            throw new WifiManagerSnippetException(
                    "Timed out after 60s waiting for Wi-Fi Soft AP state to turn off.");
        }
    }

    @Override
    public void shutdown() {}

    private class WifiScanReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                mIsScanResultAvailable = true;
            }
        }
    }
}
