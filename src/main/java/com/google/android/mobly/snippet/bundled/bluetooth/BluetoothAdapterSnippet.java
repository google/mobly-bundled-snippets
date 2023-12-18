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

package com.google.android.mobly.snippet.bundled.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.json.JSONException;

/** Snippet class exposing Android APIs in BluetoothAdapter. */
public class BluetoothAdapterSnippet implements Snippet {

    private static class BluetoothAdapterSnippetException extends Exception {

        private static final long serialVersionUID = 1;

        public BluetoothAdapterSnippetException(String msg) {
            super(msg);
        }

        public BluetoothAdapterSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }

    // Timeout to measure consistent BT state.
    private static final int BT_MATCHING_STATE_INTERVAL_SEC = 5;
    // Default timeout in seconds.
    private static final int TIMEOUT_TOGGLE_STATE_SEC = 30;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private static final ConcurrentHashMap<String, BluetoothDevice> mDiscoveryResults =
            new ConcurrentHashMap<>();
    private volatile boolean mIsDiscoveryFinished = false;
    private final Map<String, BroadcastReceiver> mReceivers;

    public BluetoothAdapterSnippet() throws Throwable {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // Use a synchronized map to avoid racing problems
        mReceivers = Collections.synchronizedMap(new HashMap<String, BroadcastReceiver>());
        Utils.adaptShellPermissionIfRequired(mContext);
        mPackageManager = mContext.getPackageManager();
    }

    /**
     * Gets a {@link BluetoothDevice} that has either been paired or discovered.
     *
     * @param deviceAddress
     * @return
     */
    public static BluetoothDevice getKnownDeviceByAddress(String deviceAddress) {
        BluetoothDevice pairedDevice = getPairedDeviceByAddress(deviceAddress);
        if (pairedDevice != null) {
            return pairedDevice;
        }
        BluetoothDevice discoveredDevice = mDiscoveryResults.get(deviceAddress);
        if (discoveredDevice != null) {
            return discoveredDevice;
        }
        throw new NoSuchElementException(
                "No device with address "
                        + deviceAddress
                        + " is paired or has been discovered. Cannot proceed.");
    }

    private static BluetoothDevice getPairedDeviceByAddress(String deviceAddress) {
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return device;
            }
        }
        return null;
    }

    /* Gets the UiDevice instance for UI operations. */
    private static UiDevice getUiDevice() throws BluetoothAdapterSnippetException {
        try {
            return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        } catch (IllegalStateException e) {
            throw new BluetoothAdapterSnippetException("Failed to get UiDevice. Please ensure that "
                    + "no other UiAutomation service is running.", e);
        }
    }

    @Rpc(description = "Enable bluetooth with a 30s timeout.")
    public void btEnable() throws BluetoothAdapterSnippetException, InterruptedException {
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            return;
        }
        waitForStableBtState();

        if (Build.VERSION.SDK_INT >= 33) {
            // BluetoothAdapter#enable is removed from public SDK for 33 and above, so uses an
            // intent instead.
            UiDevice uiDevice = getUiDevice();
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Triggers the system UI popup to ask for explicit permission.
            mContext.startActivity(enableIntent);
            // Clicks the "ALLOW" button.
            BySelector allowButtonSelector = By.text(TEXT_PATTERN_ALLOW).clickable(true);
            uiDevice.wait(Until.findObject(allowButtonSelector), 10);
            uiDevice.findObject(allowButtonSelector).click();
        } else if (!mBluetoothAdapter.enable()) {
            throw new BluetoothAdapterSnippetException("Failed to start enabling bluetooth.");
        }
        if (!Utils.waitUntil(
                () -> mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON,
                TIMEOUT_TOGGLE_STATE_SEC)) {
            throw new BluetoothAdapterSnippetException(
                    String.format(
                            "Bluetooth did not turn on within %ss.", TIMEOUT_TOGGLE_STATE_SEC));
        }
    }

    @Rpc(description = "Disable bluetooth with a 30s timeout.")
    public void btDisable() throws BluetoothAdapterSnippetException, InterruptedException {
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            return;
        }
        waitForStableBtState();
        if (!mBluetoothAdapter.disable()) {
            throw new BluetoothAdapterSnippetException("Failed to start disabling bluetooth.");
        }
        if (!Utils.waitUntil(
                () -> mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF,
                TIMEOUT_TOGGLE_STATE_SEC)) {
            throw new BluetoothAdapterSnippetException(
                    String.format(
                            "Bluetooth did not turn off within %ss.", TIMEOUT_TOGGLE_STATE_SEC));
        }
    }

    @Rpc(description = "Return true if Bluetooth is enabled, false otherwise.")
    public boolean btIsEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    @Rpc(
            description =
                    "Get bluetooth discovery results, which is a list of serialized BluetoothDevice objects.")
    public ArrayList<Bundle> btGetCachedScanResults() {
        return mJsonSerializer.serializeBluetoothDeviceList(mDiscoveryResults.values());
    }

    @Rpc(description = "Set the friendly Bluetooth name of the local Bluetooth adapter.")
    public void btSetName(String name) throws BluetoothAdapterSnippetException {
        if (!btIsEnabled()) {
            throw new BluetoothAdapterSnippetException(
                    "Bluetooth is not enabled, cannot set Bluetooth name.");
        }
        if (!mBluetoothAdapter.setName(name)) {
            throw new BluetoothAdapterSnippetException(
                    "Failed to set local Bluetooth name to " + name);
        }
    }

    @Rpc(description = "Get the friendly Bluetooth name of the local Bluetooth adapter.")
    public String btGetName() {
        return mBluetoothAdapter.getName();
    }

    @Rpc(description = "Automatically confirm the incoming BT pairing request.")
    public void btStartAutoAcceptIncomingPairRequest() throws Throwable {
        BroadcastReceiver receiver = new PairingBroadcastReceiver(mContext);
        mContext.registerReceiver(
                receiver, PairingBroadcastReceiver.filter);
        mReceivers.put("AutoAcceptIncomingPairReceiver", receiver);
    }

    @Rpc(description = "Stop the incoming BT pairing request.")
    public void btStopAutoAcceptIncomingPairRequest() throws Throwable {
        BroadcastReceiver receiver = mReceivers.remove("AutoAcceptIncomingPairReceiver");
        mContext.unregisterReceiver(receiver);
    }

    @Rpc(description = "Returns the hardware address of the local Bluetooth adapter.")
    public String btGetAddress() {
        return mBluetoothAdapter.getAddress();
    }

    @Rpc(
            description =
                    "Start discovery, wait for discovery to complete, and return results, which is a list of "
                            + "serialized BluetoothDevice objects.")
    public List<Bundle> btDiscoverAndGetResults()
            throws InterruptedException, BluetoothAdapterSnippetException {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mDiscoveryResults.clear();
        mIsDiscoveryFinished = false;
        BroadcastReceiver receiver = new BluetoothScanReceiver();
        mContext.registerReceiver(receiver, filter);
        try {
            if (!mBluetoothAdapter.startDiscovery()) {
                throw new BluetoothAdapterSnippetException(
                        "Failed to initiate Bluetooth Discovery.");
            }
            if (!Utils.waitUntil(() -> mIsDiscoveryFinished, 120)) {
                throw new BluetoothAdapterSnippetException(
                        "Failed to get discovery results after 2 mins, timeout!");
            }
        } finally {
            mContext.unregisterReceiver(receiver);
        }
        return btGetCachedScanResults();
    }

    @Rpc(description = "Become discoverable in Bluetooth.")
    public void btBecomeDiscoverable(Integer duration) throws Throwable {
        if (!btIsEnabled()) {
            throw new BluetoothAdapterSnippetException(
                    "Bluetooth is not enabled, cannot become discoverable.");
        }
        if (Build.VERSION.SDK_INT >= 31) {
            // BluetoothAdapter#setScanMode is removed from public SDK for 31 and above, so uses an
            // intent instead.
            UiDevice uiDevice = getUiDevice();
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
            // Triggers the system UI popup to ask for explicit permission.
            mContext.startActivity(discoverableIntent);

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                // Clicks the "OK" button.
                BySelector okButtonSelector = By.desc(TEXT_PATTERN_OK).clickable(true);
                uiDevice.wait(Until.findObject(okButtonSelector), 10);
                uiDevice.findObject(okButtonSelector).click();
            } else {
                // Clicks the "ALLOW" button.
                BySelector allowButtonSelector = By.text(TEXT_PATTERN_ALLOW).clickable(true);
                uiDevice.wait(Until.findObject(allowButtonSelector), 10);
                uiDevice.findObject(allowButtonSelector).click();
            }
        } else if (Build.VERSION.SDK_INT >= 30) {
            if (!(boolean)
                    Utils.invokeByReflection(
                            mBluetoothAdapter,
                            "setScanMode",
                            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                            (long) duration * 1000)) {
                throw new BluetoothAdapterSnippetException("Failed to become discoverable.");
            } else {
                if (!(boolean)
                        Utils.invokeByReflection(
                                mBluetoothAdapter,
                                "setScanMode",
                                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                                duration)) {
                    throw new BluetoothAdapterSnippetException("Failed to become discoverable.");
                }
            }
        }
    }

    private static final Pattern TEXT_PATTERN_ALLOW =
            Pattern.compile("allow", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_PATTERN_OK =
            Pattern.compile("ok", Pattern.CASE_INSENSITIVE);

    @Rpc(description = "Cancel ongoing bluetooth discovery.")
    public void btCancelDiscovery() throws BluetoothAdapterSnippetException {
        if (!mBluetoothAdapter.isDiscovering()) {
            Log.d("No ongoing bluetooth discovery.");
            return;
        }
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mIsDiscoveryFinished = false;
        BroadcastReceiver receiver = new BluetoothScanReceiver();
        mContext.registerReceiver(receiver, filter);
        try {
            if (!mBluetoothAdapter.cancelDiscovery()) {
                throw new BluetoothAdapterSnippetException(
                        "Failed to initiate to cancel bluetooth discovery.");
            }
            if (!Utils.waitUntil(() -> mIsDiscoveryFinished, 120)) {
                throw new BluetoothAdapterSnippetException(
                        "Failed to get discovery results after 2 mins, timeout!");
            }
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Rpc(description = "Stop being discoverable in Bluetooth.")
    public void btStopBeingDiscoverable() throws Throwable {
        if (!(boolean)
                Utils.invokeByReflection(
                        mBluetoothAdapter,
                        "setScanMode",
                        BluetoothAdapter.SCAN_MODE_NONE,
                        0 /* duration is not used for this */)) {
            throw new BluetoothAdapterSnippetException("Failed to stop being discoverable.");
        }
    }

    @Rpc(description = "Get the list of paired bluetooth devices.")
    public List<Bundle> btGetPairedDevices()
            throws BluetoothAdapterSnippetException, InterruptedException, JSONException {
        ArrayList<Bundle> pairedDevices = new ArrayList<>();
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            pairedDevices.add(JsonSerializer.serializeBluetoothDevice(device));
        }
        return pairedDevices;
    }

    @Rpc(description = "Pair with a bluetooth device.")
    public void btPairDevice(String deviceAddress) throws Throwable {
        BluetoothDevice device = mDiscoveryResults.get(deviceAddress);
        if (device == null) {
            throw new NoSuchElementException(
                    "No device with address "
                            + deviceAddress
                            + " has been discovered. Cannot proceed.");
        }
        mContext.registerReceiver(
                new PairingBroadcastReceiver(mContext), PairingBroadcastReceiver.filter);
        if (!(boolean) Utils.invokeByReflection(device, "createBond")) {
            throw new BluetoothAdapterSnippetException(
                    "Failed to initiate the pairing process to device: " + deviceAddress);
        }
        if (!Utils.waitUntil(() -> device.getBondState() == BluetoothDevice.BOND_BONDED, 120)) {
            throw new BluetoothAdapterSnippetException(
                    "Failed to pair with device " + deviceAddress + " after 2min.");
        }
    }

    @Rpc(description = "Un-pair a bluetooth device.")
    public void btUnpairDevice(String deviceAddress) throws Throwable {
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                if (!(boolean) Utils.invokeByReflection(device, "removeBond")) {
                    throw new BluetoothAdapterSnippetException(
                            "Failed to initiate the un-pairing process for device: "
                                    + deviceAddress);
                }
                if (!Utils.waitUntil(
                        () -> device.getBondState() == BluetoothDevice.BOND_NONE, 30)) {
                    throw new BluetoothAdapterSnippetException(
                            "Failed to un-pair device " + deviceAddress + " after 30s.");
                }
                return;
            }
        }
        throw new NoSuchElementException("No device with address " + deviceAddress + " is paired.");
    }

    @Override
    public void shutdown() {
        for (Map.Entry<String, BroadcastReceiver> entry : mReceivers.entrySet()) {
            mContext.unregisterReceiver(entry.getValue());
        }
        mReceivers.clear();
    }

    private class BluetoothScanReceiver extends BroadcastReceiver {

        /**
         * The receiver gets an ACTION_FOUND intent whenever a new device is found.
         * ACTION_DISCOVERY_FINISHED intent is received when the discovery process ends.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mIsDiscoveryFinished = true;
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device =
                        (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mDiscoveryResults.put(device.getAddress(), device);
            }
        }
    }

    /**
     * Waits until the bluetooth adapter state has stabilized. We consider BT state stabilized if it
     * hasn't changed within 5 sec.
     */
    private static void waitForStableBtState() throws BluetoothAdapterSnippetException {
        long timeoutMs = System.currentTimeMillis() + TIMEOUT_TOGGLE_STATE_SEC * 1000;
        long continuousStateIntervalMs =
                System.currentTimeMillis() + BT_MATCHING_STATE_INTERVAL_SEC * 1000;
        int prevState = mBluetoothAdapter.getState();
        while (System.currentTimeMillis() < timeoutMs) {
            // Delay.
            Utils.waitUntil(() -> false, /* timeout= */ 1);

            int currentState = mBluetoothAdapter.getState();
            if (currentState != prevState) {
                continuousStateIntervalMs =
                        System.currentTimeMillis() + BT_MATCHING_STATE_INTERVAL_SEC * 1000;
            }
            if (continuousStateIntervalMs <= System.currentTimeMillis()) {
                return;
            }
            prevState = currentState;
        }
        throw new BluetoothAdapterSnippetException(
                String.format(
                        "Failed to reach a stable Bluetooth state within %d s",
                        TIMEOUT_TOGGLE_STATE_SEC));
    }
}
