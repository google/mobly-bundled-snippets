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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanRecord;
import android.net.DhcpInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A collection of methods used to serialize data types defined in Android API into JSON strings.
 */
public class JsonSerializer {
    private static Gson mGson;

    public JsonSerializer() {
        GsonBuilder builder = new GsonBuilder();
        mGson =
                builder.serializeNulls()
                        .excludeFieldsWithModifiers(Modifier.STATIC)
                        .enableComplexMapKeySerialization()
                        .disableInnerClassSerialization()
                        .create();
    }

    /**
     * Remove the extra quotation marks from the beginning and the end of a string.
     *
     * <p>This is useful for strings like the SSID field of Android's Wi-Fi configuration.
     *
     * @param originalString
     */
    public static String trimQuotationMarks(String originalString) {
        String result = originalString;
        if (originalString.charAt(0) == '"'
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
        return new JSONObject(mGson.toJson(data));
    }

    private JSONObject serializeDhcpInfo(DhcpInfo data) throws JSONException {
        JSONObject result = new JSONObject(mGson.toJson(data));
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
        JSONObject result = new JSONObject(mGson.toJson(data));
        result.put("Status", WifiConfiguration.Status.strings[data.status]);
        result.put("SSID", trimQuotationMarks(data.SSID));
        return result;
    }

    private JSONObject serializeWifiInfo(WifiInfo data) throws JSONException {
        JSONObject result = new JSONObject(mGson.toJson(data));
        result.put("SSID", trimQuotationMarks(data.getSSID()));
        for (SupplicantState state : SupplicantState.values()) {
            if (data.getSupplicantState().equals(state)) {
                result.put("SupplicantState", state.name());
            }
        }
        return result;
    }

    public Bundle serializeBluetoothDevice(BluetoothDevice data) {
        Bundle result = new Bundle();
        result.putString("Address", data.getAddress());
        final String bondState = Enums.bluetoothDeviceBondStateEnum.getString(data.getBondState());
        result.putString("BondState", bondState);
        result.putString("Name", data.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            String deviceType = Enums.bluetoothDeviceTypeEnum.getString(data.getType());
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
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
     * @param record
     * @return
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private Bundle serializeBleScanRecord(ScanRecord record) {
        Bundle result = new Bundle();
        result.putString("DeviceName", record.getDeviceName());
        result.putString(
                "TxPowerLevel", Enums.bleAdvertiseTxPowerEnum.getString(record.getTxPowerLevel()));
        return result;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    public static Bundle serializeBleAdvertisingSettings(AdvertiseSettings advertiseSettings) {
        Bundle result = new Bundle();
        result.putString(
                "TxPowerLevel",
                Enums.bleAdvertiseTxPowerEnum.getString(advertiseSettings.getTxPowerLevel()));
        result.putString("Mode", Enums.bleAdvertiseModeEnum.getString(advertiseSettings.getMode()));
        result.putInt("Timeout", advertiseSettings.getTimeout());
        result.putBoolean("IsConnectable", advertiseSettings.isConnectable());
        return result;
    }
}
