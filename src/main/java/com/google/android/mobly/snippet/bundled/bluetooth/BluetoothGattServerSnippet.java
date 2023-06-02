/*
 * Copyright (C) 2023 Google Inc.
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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.DeadObjectException;
import android.os.SystemClock;
import android.util.Base64;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.DataHolder;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.MbsEnums;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import com.google.android.mobly.snippet.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Snippet class exposing Android APIs in BluetoothGattServer. */
public class BluetoothGattServerSnippet implements Snippet {
    private static class BluetoothGattServerSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothGattServerSnippetException(String msg) {
            super(msg);
        }
    }

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final DataHolder dataHolder;
    private final EventCache eventCache;

    private BluetoothGattServer bluetoothGattServer;

    public BluetoothGattServerSnippet() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        dataHolder = new DataHolder();
        eventCache = EventCache.getInstance();
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @AsyncRpc(description = "Start BLE server.")
    public void bleStartServer(String callbackId, JSONArray services)
            throws JSONException, DeadObjectException {
        BluetoothGattServerCallback gattServerCallback =
                new DefaultBluetoothGattServerCallback(callbackId);
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        addServiceToGattServer(services);
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @AsyncRpc(description = "Start BLE server with workaround.")
    public void bleStartServerWithWorkaround(String callbackId, JSONArray services)
            throws JSONException, DeadObjectException {
        BluetoothGattServerCallback gattServerCallback =
                new DefaultBluetoothGattServerCallback(callbackId);
        boolean isGattServerStarted = false;
        int count = 0;
        while (!isGattServerStarted && count < 5) {
            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback);
            if (bluetoothGattServer != null) {
                addServiceToGattServer(services);
                isGattServerStarted = true;
            } else {
                SystemClock.sleep(1000);
                count++;
            }
        }
    }

    private void addServiceToGattServer(JSONArray services) throws JSONException {
        for (int i = 0; i < services.length(); i++) {
            JSONObject service = services.getJSONObject(i);
            BluetoothGattService bluetoothGattService =
                    JsonDeserializer.jsonToBluetoothGattService(dataHolder, service);
            bluetoothGattServer.addService(bluetoothGattService);
        }
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Stop BLE server.")
    public void bleStopServer() throws BluetoothGattServerSnippetException {
        if (bluetoothGattServer == null) {
            throw new BluetoothGattServerSnippetException("BLE server is not initialized.");
        }
        bluetoothGattServer.close();
    }

    private class DefaultBluetoothGattServerCallback extends BluetoothGattServerCallback {
        private final String callbackId;

        DefaultBluetoothGattServerCallback(String callbackId) {
            this.callbackId = callbackId;
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            SnippetEvent event = new SnippetEvent(callbackId, "onConnectionStateChange");
            event.getData().putBundle("device", JsonSerializer.serializeBluetoothDevice(device));
            event.getData().putString("status", MbsEnums.BLE_STATUS_TYPE.getString(status));
            event.getData().putString("newState", MbsEnums.BLE_CONNECT_STATUS.getString(newState));
            eventCache.postEvent(event);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d("Bluetooth Gatt Server service added with status " + status);
            SnippetEvent event = new SnippetEvent(callbackId, "onServiceAdded");
            event.getData().putString("status", MbsEnums.BLE_STATUS_TYPE.getString(status));
            event.getData()
                    .putParcelable("Service",
                                  JsonSerializer.serializeBluetoothGattService(service));
            eventCache.postEvent(event);
        }

        @Override
        public void onCharacteristicReadRequest(
                BluetoothDevice device,
                int requestId,
                int offset,
                BluetoothGattCharacteristic characteristic) {
            Log.d("Bluetooth Gatt Server received a read request");
            if (dataHolder.get(characteristic) != null) {
                bluetoothGattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        Base64.decode(dataHolder.get(characteristic), Base64.NO_WRAP));
            } else {
                bluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(
                BluetoothDevice device,
                int requestId,
                BluetoothGattCharacteristic characteristic,
                boolean preparedWrite,
                boolean responseNeeded,
                int offset,
                byte[] value) {
            Log.d("Bluetooth Gatt Server received a write request");
            bluetoothGattServer.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            SnippetEvent event = new SnippetEvent(callbackId, "onCharacteristicWriteRequest");
            event.getData().putString("Data", Base64.encodeToString(value, Base64.NO_WRAP));
            eventCache.postEvent(event);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            Log.d("Bluetooth Gatt Server received an execute write request");
            bluetoothGattServer.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
        }
    }

    @Override
    public void shutdown() {}
}
