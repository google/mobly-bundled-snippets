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

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import com.google.android.mobly.snippet.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.net.wifi.SupplicantState;
/** Snippet class exposing Android APIs in WifiManager. */
public class WifiManagerSnippet implements Snippet {
    private static class WifiManagerSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public WifiManagerSnippetException(String msg) {
            super(msg);
        }

        public WifiManagerSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }

    private static final int TIMEOUT_TOGGLE_STATE = 30;
    private final WifiManager mWifiManager;
    private final Context mContext;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private volatile boolean mIsScanResultAvailable = false;

    public WifiManagerSnippet() throws Throwable {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mWifiManager =
                (WifiManager)
                        mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        adaptShellPermissionIfRequired();
    }

    @Rpc(
            description =
                    "Clears all configured networks. This will only work if all configured "
                            + "networks were added through this MBS instance")
    public void wifiClearConfiguredNetworks() throws WifiManagerSnippetException {
        List<WifiConfiguration> unremovedConfigs = mWifiManager.getConfiguredNetworks();
        List<WifiConfiguration> failedConfigs = new ArrayList<>();
        if (unremovedConfigs == null) {
            throw new WifiManagerSnippetException(
                    "Failed to get a list of configured networks. Is wifi disabled?");
        }
        for (WifiConfiguration config : unremovedConfigs) {
            if (!mWifiManager.removeNetwork(config.networkId)) {
                failedConfigs.add(config);
            }
        }

        // If removeNetwork is called on a network with both an open and OWE config, it will remove
        // both. The subsequent call on the same network will fail. The clear operation may succeed
        // even if failures appear in the log below.
        if (!failedConfigs.isEmpty()) {
            Log.e("Encountered error while removing networks: " + failedConfigs);
        }

        // Re-check configured configs list to ensure that it is cleared
        unremovedConfigs = mWifiManager.getConfiguredNetworks();
        if (!unremovedConfigs.isEmpty()) {
            throw new WifiManagerSnippetException("Failed to remove networks: " + unremovedConfigs);
        }
    }

    @Rpc(description = "Turns on Wi-Fi with a 30s timeout.")
    public void wifiEnable() throws InterruptedException, WifiManagerSnippetException {
        if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            return;
        }
        // If Wi-Fi is trying to turn off, wait for that to complete before continuing.
        if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLING) {
            if (!Utils.waitUntil(
                    () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED,
                    TIMEOUT_TOGGLE_STATE)) {
                Log.e(String.format("Wi-Fi failed to stabilize after %ss.", TIMEOUT_TOGGLE_STATE));
            }
        }
        if (!mWifiManager.setWifiEnabled(true)) {
            throw new WifiManagerSnippetException("Failed to initiate enabling Wi-Fi.");
        }
        if (!Utils.waitUntil(
                () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED,
                TIMEOUT_TOGGLE_STATE)) {
            throw new WifiManagerSnippetException(
                    String.format(
                            "Failed to enable Wi-Fi after %ss, timeout!", TIMEOUT_TOGGLE_STATE));
        }
    }

    @Rpc(description = "Turns off Wi-Fi with a 30s timeout.")
    public void wifiDisable() throws InterruptedException, WifiManagerSnippetException {
        if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
            return;
        }
        // If Wi-Fi is trying to turn on, wait for that to complete before continuing.
        if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING) {
            if (!Utils.waitUntil(
                    () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED,
                    TIMEOUT_TOGGLE_STATE)) {
                Log.e(String.format("Wi-Fi failed to stabilize after %ss.", TIMEOUT_TOGGLE_STATE));
            }
        }
        if (!mWifiManager.setWifiEnabled(false)) {
            throw new WifiManagerSnippetException("Failed to initiate disabling Wi-Fi.");
        }
        if (!Utils.waitUntil(
                () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED,
                TIMEOUT_TOGGLE_STATE)) {
            throw new WifiManagerSnippetException(
                    String.format(
                            "Failed to disable Wi-Fi after %ss, timeout!", TIMEOUT_TOGGLE_STATE));
        }
    }

    @Rpc(description = "Checks if Wi-Fi is enabled.")
    public boolean wifiIsEnabled() {
        return mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED;
    }

    @Rpc(description = "Trigger Wi-Fi scan.")
    public void wifiStartScan() throws WifiManagerSnippetException {
        if (!mWifiManager.startScan()) {
            throw new WifiManagerSnippetException("Failed to initiate Wi-Fi scan.");
        }
    }

    @Rpc(
            description =
                    "Get Wi-Fi scan results, which is a list of serialized WifiScanResult objects.")
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
                            + "serialized WifiScanResult objects.")
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
                            + "WPA2.")
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
        List<WifiConfiguration> wifiConfigs = mWifiManager.getConfiguredNetworks();
        if (wifiConfigs == null) {
            return null;
        }
        for (WifiConfiguration config : wifiConfigs) {
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
        String SSID = wifiConfig.SSID;
        // Return directly if network is already connected.
        WifiInfo connectionInfo = mWifiManager.getConnectionInfo();
        if (connectionInfo.getNetworkId() != -1
                && connectionInfo.getSSID().equals(wifiConfig.SSID)) {
            Log.d("Network " + connectionInfo.getSSID() + " is already connected.");
            return;
        }
        int networkId;
        // If this is a network with a known SSID, connect with the existing config.
        // We have to do this because in N+, network configs can only be modified by the UID that
        // created the network. So any attempt to modify a network config that does not belong to us
        // would result in error.
        WifiConfiguration existingConfig = getExistingConfiguredNetwork(wifiConfig.SSID);
        if (existingConfig != null) {
            Log.w(
                    "Connecting to network \""
                            + existingConfig.SSID
                            + "\" with its existing configuration: "
                            + existingConfig.toString());
            wifiConfig = existingConfig;
            networkId = wifiConfig.networkId;
        } else {
            // If this is a network with a new SSID, add the network.
            networkId = mWifiManager.addNetwork(wifiConfig);
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
        if (!Utils.waitUntil(
            () ->
                mWifiManager.getConnectionInfo().getSSID().equals(SSID)
                    && mWifiManager.getConnectionInfo().getNetworkId() != -1 && mWifiManager
                    .getConnectionInfo().getSupplicantState().equals(SupplicantState.COMPLETED),
            90)) {
            throw new WifiManagerSnippetException(
                String.format(
                    "Failed to connect to '%s', timeout! Current connection: '%s'",
                    wifiNetworkConfig, mWifiManager.getConnectionInfo().getSSID()));
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
                            + " WifiConfiguration.")
    public void wifiRemoveNetwork(Integer networkId) throws WifiManagerSnippetException {
        if (!mWifiManager.removeNetwork(networkId)) {
            throw new WifiManagerSnippetException("Failed to remove network of ID: " + networkId);
        }
    }

    @Rpc(
            description =
                    "Get the list of configured Wi-Fi networks, each is a serialized "
                            + "WifiConfiguration object.")
    public List<JSONObject> wifiGetConfiguredNetworks() throws JSONException {
        List<JSONObject> networks = new ArrayList<>();
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            networks.add(mJsonSerializer.toJson(config));
        }
        return networks;
    }

    @RpcMinSdk(Build.VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Enable or disable wifi verbose logging.")
    public void wifiSetVerboseLogging(boolean enable) throws Throwable {
        Utils.invokeByReflection(mWifiManager, "enableVerboseLogging", enable ? 1 : 0);
    }

    @Rpc(
            description =
                    "Get the information about the active Wi-Fi connection, which is a serialized "
                            + "WifiInfo object.")
    public JSONObject wifiGetConnectionInfo() throws JSONException {
        return mJsonSerializer.toJson(mWifiManager.getConnectionInfo());
    }

    @Rpc(
            description =
                    "Get the info from last successful DHCP request, which is a serialized DhcpInfo "
                            + "object.")
    public JSONObject wifiGetDhcpInfo() throws JSONException {
        return mJsonSerializer.toJson(mWifiManager.getDhcpInfo());
    }

    @Rpc(description = "Check whether Wi-Fi Soft AP (hotspot) is enabled.")
    public boolean wifiIsApEnabled() throws Throwable {
        return (boolean) Utils.invokeByReflection(mWifiManager, "isWifiApEnabled");
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @RpcMinSdk(Build.VERSION_CODES.LOLLIPOP)
    @Rpc(
            description =
                    "Check whether this device supports 5 GHz band Wi-Fi. "
                            + "Turn on Wi-Fi before calling.")
    public boolean wifiIs5GHzBandSupported() {
        return mWifiManager.is5GHzBandSupported();
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

    /**
     * Elevates permission as require for proper wifi controls.
     *
     * Starting in Android Q (29), additional restrictions are added for wifi operation. See
     * below Android Q privacy changes for additional details.
     * https://developer.android.com/preview/privacy/camera-connectivity
     *
     * @throws Throwable if failed to cleanup connection with UiAutomation
     */
    private void adaptShellPermissionIfRequired() throws Throwable {
        if (mContext.getApplicationContext().getApplicationInfo().targetSdkVersion >= 29
            && Build.VERSION.SDK_INT >= 29) {
          Log.d("Elevating permission require to enable support for wifi operation in Android Q+");
          UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
          uia.adoptShellPermissionIdentity();
          try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
          } catch (NoSuchMethodException
              | IllegalAccessException
              | ClassNotFoundException
              | InvocationTargetException e) {
                  throw new WifiManagerSnippetException("Failed to cleaup Ui Automation", e);
          }
        }
    }

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
