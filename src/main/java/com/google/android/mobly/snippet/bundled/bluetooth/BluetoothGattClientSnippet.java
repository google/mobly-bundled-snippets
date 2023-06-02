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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.MbsEnums;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import com.google.android.mobly.snippet.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import org.json.JSONException;

/** Snippet class exposing Android APIs in BluetoothGatt. */
public class BluetoothGattClientSnippet implements Snippet {
    private static class BluetoothGattClientSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothGattClientSnippetException(String msg) {
            super(msg);
        }
    }

    private final Context context;
    private final EventCache eventCache;
    private final HashMap<String, HashMap<String, BluetoothGattCharacteristic>>
            characteristicHashMap;

    private BluetoothGatt bluetoothGattClient;

    private long connectionStartTime = 0;
    private long connectionEndTime = 0;

    public BluetoothGattClientSnippet() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        eventCache = EventCache.getInstance();
        characteristicHashMap = new HashMap<>();
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @AsyncRpc(description = "Start BLE client.")
    public void bleConnectGatt(String callbackId, String deviceAddress) throws JSONException {
        BluetoothDevice remoteDevice =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
        BluetoothGattCallback gattCallback = new DefaultBluetoothGattCallback(callbackId);
        connectionStartTime = System.currentTimeMillis();
        bluetoothGattClient = remoteDevice.connectGatt(context, false, gattCallback);
        Log.d("Connection start time is " + connectionStartTime);
        connectionEndTime = 0;
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Start BLE service discovery")
    public long bleDiscoverServices() throws BluetoothGattClientSnippetException {
        if (bluetoothGattClient == null) {
            throw new BluetoothGattClientSnippetException("BLE client is not initialized.");
        }
        long discoverServicesStartTime = SystemClock.elapsedRealtimeNanos();
        Log.d("Discover services start time is " + discoverServicesStartTime);
        boolean result = bluetoothGattClient.discoverServices();
        if (!result) {
            throw new BluetoothGattClientSnippetException("Discover services returned false.");
        }
        return discoverServicesStartTime;
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "Stop BLE client.")
    public void bleDisconnect() throws BluetoothGattClientSnippetException {
        if (bluetoothGattClient == null) {
            throw new BluetoothGattClientSnippetException("BLE client is not initialized.");
        }
        bluetoothGattClient.disconnect();
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "BLE read operation.")
    public boolean bleReadOperation(String serviceUuid, String characteristicUuid)
            throws JSONException, BluetoothGattClientSnippetException {
        if (bluetoothGattClient == null) {
            throw new BluetoothGattClientSnippetException("BLE client is not initialized.");
        }
        boolean result =
                bluetoothGattClient.readCharacteristic(
                        characteristicHashMap.get(serviceUuid).get(characteristicUuid));
        Log.d("Read operation returned result " + result);
        return result;
    }

    @RpcMinSdk(VERSION_CODES.LOLLIPOP)
    @Rpc(description = "BLE write operation.")
    public boolean bleWriteOperation(String serviceUuid, String characteristicUuid, String data)
            throws JSONException, BluetoothGattClientSnippetException {
        if (bluetoothGattClient == null) {
            throw new BluetoothGattClientSnippetException("BLE client is not initialized.");
        }
        BluetoothGattCharacteristic characteristic =
                characteristicHashMap.get(serviceUuid).get(characteristicUuid);
        characteristic.setValue(Base64.decode(data, Base64.NO_WRAP));
        boolean result = bluetoothGattClient.writeCharacteristic(characteristic);
        Log.d("Write operation returned result " + result);
        return result;
    }

    private class DefaultBluetoothGattCallback extends BluetoothGattCallback {
        private final String callbackId;

        DefaultBluetoothGattCallback(String callbackId) {
            this.callbackId = callbackId;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            SnippetEvent event = new SnippetEvent(callbackId, "onConnectionStateChange");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionEndTime = System.currentTimeMillis();
                event.getData().putLong(
                        "gattConnectionTimeMs", connectionEndTime - connectionStartTime);
                Log.d("Connection end time is " + connectionEndTime);
            }
            event.getData().putString("status", MbsEnums.BLE_STATUS_TYPE.getString(status));
            event.getData().putString("newState", MbsEnums.BLE_CONNECT_STATUS.getString(newState));
            event.getData().putBundle("gatt", JsonSerializer.serializeBluetoothGatt(gatt));
            eventCache.postEvent(event);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            long discoverServicesEndTime = SystemClock.elapsedRealtimeNanos();
            Log.d("Discover services end time is " + discoverServicesEndTime);
            SnippetEvent event = new SnippetEvent(callbackId, "onServiceDiscovered");
            event.getData().putString("status", MbsEnums.BLE_STATUS_TYPE.getString(status));
            ArrayList<Bundle> services = new ArrayList<>();
            for (BluetoothGattService service : gatt.getServices()) {
                HashMap<String, BluetoothGattCharacteristic> characteristics = new HashMap<>();
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    characteristics.put(characteristic.getUuid().toString(), characteristic);
                }
                characteristicHashMap.put(service.getUuid().toString(), characteristics);
                services.add(JsonSerializer.serializeBluetoothGattService(service));
            }
            // TODO(66740428): Should not return services directly
            event.getData().putParcelableArrayList("Services", services);
            event.getData().putBundle("gatt", JsonSerializer.serializeBluetoothGatt(gatt));
            event.getData().putLong("discoveryServicesEndTime", discoverServicesEndTime);
            eventCache.postEvent(event);
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            SnippetEvent event = new SnippetEvent(callbackId, "onCharacteristicRead");
            event.getData().putString("status", MbsEnums.BLE_STATUS_TYPE.getString(status));
            // TODO(66740428): Should return the characteristic instead of value
            event.getData()
                    .putString("Data",
                            Base64.encodeToString(characteristic.getValue(), Base64.NO_WRAP));
            event.getData().putBundle("gatt", JsonSerializer.serializeBluetoothGatt(gatt));
            eventCache.postEvent(event);
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            SnippetEvent event = new SnippetEvent(callbackId, "onCharacteristicWrite");
            event.getData().putString("status", MbsEnums.BLE_STATUS_TYPE.getString(status));
            // TODO(66740428): Should return the characteristic instead of value
            event.getData().putBundle("gatt", JsonSerializer.serializeBluetoothGatt(gatt));
            eventCache.postEvent(event);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            SnippetEvent event = new SnippetEvent(callbackId, "onReliableWriteCompleted");
            event.getData().putString("status", MbsEnums.BLE_STATUS_TYPE.getString(status));
            event.getData().putBundle("gatt", JsonSerializer.serializeBluetoothGatt(gatt));
            eventCache.postEvent(event);
        }
    }

    @Override
    public void shutdown() {
        if (bluetoothGattClient != null) {
            bluetoothGattClient.close();
        }
    }
}
