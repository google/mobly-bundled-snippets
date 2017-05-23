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
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A collection of methods used to deserialize JSON strings into data objects defined in Android
 * API.
 */
public class JsonDeserializer {

    private JsonDeserializer() {}

    public static WifiConfiguration jsonToWifiConfig(JSONObject jsonObject) throws JSONException {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + jsonObject.getString("SSID") + "\"";
        config.hiddenSSID = jsonObject.optBoolean("hiddenSSID", false);
        if (jsonObject.has("password")) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.preSharedKey = "\"" + jsonObject.getString("password") + "\"";
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        return config;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static AdvertiseSettings jsonToBleAdvertiseSettings(JSONObject jsonObject)
            throws JSONException {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        if (jsonObject.has("AdvertiseMode")) {
            int mode = MbsEnums.BLE_ADVERTISE_MODE.getInt(jsonObject.getString("AdvertiseMode"));
            builder.setAdvertiseMode(mode);
        }
        // Timeout in milliseconds.
        if (jsonObject.has("Timeout")) {
            builder.setTimeout(jsonObject.getInt("Timeout"));
        }
        if (jsonObject.has("Connectable")) {
            builder.setConnectable(jsonObject.getBoolean("Connectable"));
        }
        if (jsonObject.has("TxPowerLevel")) {
            int txPowerLevel =
                    MbsEnums.BLE_ADVERTISE_TX_POWER.getInt(jsonObject.getString("TxPowerLevel"));
            builder.setTxPowerLevel(txPowerLevel);
        }
        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static AdvertiseData jsonToBleAdvertiseData(JSONObject jsonObject) throws JSONException {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        if (jsonObject.has("IncludeDeviceName")) {
            builder.setIncludeDeviceName(jsonObject.getBoolean("IncludeDeviceName"));
        }
        if (jsonObject.has("IncludeTxPowerLevel")) {
            builder.setIncludeTxPowerLevel(jsonObject.getBoolean("IncludeTxPowerLevel"));
        }
        if (jsonObject.has("ServiceData")) {
            JSONArray serviceData = jsonObject.getJSONArray("ServiceData");
            for (int i = 0; i < serviceData.length(); i++) {
                JSONObject dataSet = serviceData.getJSONObject(i);
                ParcelUuid parcelUuid = ParcelUuid.fromString(dataSet.getString("UUID"));
                builder.addServiceUuid(parcelUuid);
                if (dataSet.has("Data")) {
                    byte[] data = Base64.decode(dataSet.getString("Data"), Base64.DEFAULT);
                    builder.addServiceData(parcelUuid, data);
                }
            }
        }
        if (jsonObject.has("ManufacturerData")) {
            JSONObject manufacturerData = jsonObject.getJSONObject("ManufacturerData");
            int manufacturerId = manufacturerData.getInt("ManufacturerId");
            byte[] manufacturerSpecificData =
                    Base64.decode(jsonObject.getString("ManufacturerSpecificData"), Base64.DEFAULT);
            builder.addManufacturerData(manufacturerId, manufacturerSpecificData);
        }
        return builder.build();
    }
}
