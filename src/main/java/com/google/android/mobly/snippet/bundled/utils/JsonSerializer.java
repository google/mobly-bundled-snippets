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

package com.google.android.mobly.snippet.bundled.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanRecord;
import android.net.DhcpInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Base64;
import android.util.SparseArray;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A collection of methods used to serialize data types defined in Android API into JSON strings.
 */
public class JsonSerializer {
    private static final Gson gson =
        new GsonBuilder()
            .serializeNulls()
            .excludeFieldsWithModifiers(Modifier.STATIC)
            .enableComplexMapKeySerialization()
            .disableInnerClassSerialization()
            .create();

    /**
     * Remove the extra quotation marks from the beginning and the end of a string.
     *
     * <p>This is useful for strings like the SSID field of Android's Wi-Fi configuration.
     *
     * @param originalString
     */
    public static String trimQuotationMarks(String originalString) {
        String result = originalString;
        if (originalString.length() > 2
                && originalString.charAt(0) == '"'
                && originalString.charAt(originalString.length() - 1) == '"') {
            result = originalString.substring(1, originalString.length() - 1);
        }
        return result;
    }

    public JSONObject toJson(Object object) throws JSONException {
        if (object instanceof DhcpInfo) {
            return serializeDhcpInfo((DhcpInfo) object);
        } else if (object instanceof WifiConfiguration) {
            return serializeWifiConfiguration((WifiConfiguration) object);
        } else if (object instanceof WifiInfo) {
            return serializeWifiInfo((WifiInfo) object);
        }
        return defaultSerialization(object);
    }

    /**
     * By default, we rely on Gson to do the right job.
     *
     * @param data An object to serialize
     * @return A JSONObject that has the info of the serialized data object.
     * @throws JSONException
     */
    private JSONObject defaultSerialization(Object data) throws JSONException {
        return new JSONObject(gson.toJson(data));
    }

    private JSONObject serializeDhcpInfo(DhcpInfo data) throws JSONException {
        JSONObject result = new JSONObject(gson.toJson(data));
        int ipAddress = data.ipAddress;
        byte[] addressBytes = {
            (byte) (0xff & ipAddress),
            (byte) (0xff & (ipAddress >> 8)),
            (byte) (0xff & (ipAddress >> 16)),
            (byte) (0xff & (ipAddress >> 24))
        };
        try {
            String addressString = InetAddress.getByAddress(addressBytes).toString();
            result.put("IpAddress", addressString);
        } catch (UnknownHostException e) {
            result.put("IpAddress", ipAddress);
        }
        return result;
    }

    private JSONObject serializeWifiConfiguration(WifiConfiguration data) throws JSONException {
        JSONObject result = new JSONObject(gson.toJson(data));
        result.put("Status", WifiConfiguration.Status.strings[data.status]);
        result.put("SSID", trimQuotationMarks(data.SSID));
        return result;
    }

    private JSONObject serializeWifiInfo(WifiInfo data) throws JSONException {
        JSONObject result = new JSONObject(gson.toJson(data));
        result.put("SSID", trimQuotationMarks(data.getSSID()));
        for (SupplicantState state : SupplicantState.values()) {
            if (data.getSupplicantState().equals(state)) {
                result.put("SupplicantState", state.name());
            }
        }
        return result;
    }

    public static Bundle serializeBluetoothDevice(BluetoothDevice data) {
        Bundle result = new Bundle();
        result.putString("Address", data.getAddress());
        final String bondState =
                MbsEnums.BLUETOOTH_DEVICE_BOND_STATE.getString(data.getBondState());
        result.putString("BondState", bondState);
        result.putString("Name", data.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            String deviceType = MbsEnums.BLUETOOTH_DEVICE_TYPE.getString(data.getType());
            result.putString("DeviceType", deviceType);
            ParcelUuid[] parcelUuids = data.getUuids();
            if (parcelUuids != null) {
                ArrayList<String> uuidStrings = new ArrayList<>(parcelUuids.length);
                for (ParcelUuid parcelUuid : parcelUuids) {
                    uuidStrings.add(parcelUuid.getUuid().toString());
                }
                result.putStringArrayList("UUIDs", uuidStrings);
            }
        }
        return result;
    }

    public ArrayList<Bundle> serializeBluetoothDeviceList(
            Collection<BluetoothDevice> bluetoothDevices) {
        ArrayList<Bundle> results = new ArrayList<>();
        for (BluetoothDevice device : bluetoothDevices) {
            results.add(serializeBluetoothDevice(device));
        }
        return results;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Bundle serializeBleScanResult(android.bluetooth.le.ScanResult scanResult) {
        Bundle result = new Bundle();
        result.putBundle("Device", serializeBluetoothDevice(scanResult.getDevice()));
        result.putInt("Rssi", scanResult.getRssi());
        result.putBundle("ScanRecord", serializeBleScanRecord(scanResult.getScanRecord()));
        result.putLong("TimestampNanos", scanResult.getTimestampNanos());
        return result;
    }

    /**
     * Serialize ScanRecord for Bluetooth LE.
     *
     * <p>Not all fields are serialized here. Will add more as we need.
     *
     * <pre>The returned {@link Bundle} has the following info:
     *          "DeviceName", String
     *          "TxPowerLevel", String
     * </pre>
     *
     * @param record A {@link ScanRecord} object.
     * @return A {@link Bundle} object.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Bundle serializeBleScanRecord(ScanRecord record) {
        Bundle result = new Bundle();
        result.putString("DeviceName", record.getDeviceName());
        result.putInt("TxPowerLevel", record.getTxPowerLevel());
        result.putParcelableArrayList("Services", serializeBleScanServices(record));
        result.putBundle(
            "manufacturerSpecificData", serializeBleScanManufacturerSpecificData(record));
        return result;
    }

    /** Serialize manufacturer specific data from ScanRecord for Bluetooth LE. */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private ArrayList<Bundle> serializeBleScanServices(ScanRecord record) {
        ArrayList<Bundle> result = new ArrayList<>();
        if (record.getServiceUuids() != null) {
            for (ParcelUuid uuid : record.getServiceUuids()) {
                Bundle service = new Bundle();
                service.putString("UUID", uuid.getUuid().toString());
                if (record.getServiceData(uuid) != null) {
                    service.putString(
                            "Data",
                            new String(Base64.encode(record.getServiceData(uuid), Base64.NO_WRAP),
                                      UTF_8));
                } else {
                    service.putString("Data", "");
                }
                result.add(service);
            }
        }
        return result;
    }

    /** Serialize manufacturer specific data from ScanRecord for Bluetooth LE. */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Bundle serializeBleScanManufacturerSpecificData(ScanRecord record) {
        Bundle result = new Bundle();
        SparseArray<byte[]> sparseArray = record.getManufacturerSpecificData();
        for (int i = 0; i < sparseArray.size(); i++) {
            int key = sparseArray.keyAt(i);
            result.putByteArray(String.valueOf(key), sparseArray.get(key));
        }
        return result;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Bundle serializeBleAdvertisingSettings(AdvertiseSettings advertiseSettings) {
        Bundle result = new Bundle();
        result.putString(
                "TxPowerLevel",
                MbsEnums.BLE_ADVERTISE_TX_POWER.getString(advertiseSettings.getTxPowerLevel()));
        result.putString(
                "Mode", MbsEnums.BLE_ADVERTISE_MODE.getString(advertiseSettings.getMode()));
        result.putInt("Timeout", advertiseSettings.getTimeout());
        result.putBoolean("IsConnectable", advertiseSettings.isConnectable());
        return result;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Bundle serializeBluetoothGatt(BluetoothGatt gatt) {
        Bundle result = new Bundle();
        ArrayList<Bundle> services = new ArrayList<>();
        for (BluetoothGattService service : gatt.getServices()) {
            services.add(JsonSerializer.serializeBluetoothGattService(service));
        }
        result.putParcelableArrayList("Services", services);
        result.putBundle("Device", JsonSerializer.serializeBluetoothDevice(gatt.getDevice()));
        return result;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Bundle serializeBluetoothGattService(BluetoothGattService service) {
        Bundle result = new Bundle();
        result.putString("UUID", service.getUuid().toString());
        result.putString("Type", MbsEnums.BLE_SERVICE_TYPE.getString(service.getType()));
        ArrayList<Bundle> characteristics = new ArrayList<>();
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            characteristics.add(serializeBluetoothGattCharacteristic(characteristic));
        }
        result.putParcelableArrayList("Characteristics", characteristics);
        return result;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Bundle serializeBluetoothGattCharacteristic(
            BluetoothGattCharacteristic characteristic) {
        Bundle result = new Bundle();
        result.putString("UUID", characteristic.getUuid().toString());
        result.putString(
                "Property", MbsEnums.BLE_PROPERTY_TYPE.getString(characteristic.getProperties()));
        result.putString(
                "Permission",
                MbsEnums.BLE_PERMISSION_TYPE.getString(characteristic.getPermissions()));
        return result;
    }
}
