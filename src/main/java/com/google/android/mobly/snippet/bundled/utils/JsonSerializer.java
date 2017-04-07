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

import android.bluetooth.BluetoothDevice;
import android.net.DhcpInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
        if (object instanceof BluetoothDevice) {
            return serializeBluetoothDevice((BluetoothDevice) object);
        } else if (object instanceof DhcpInfo) {
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

    private JSONObject serializeBluetoothDevice(BluetoothDevice data) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("Address", data.getAddress());
        switch (data.getBondState()) {
            case BluetoothDevice.BOND_NONE:
                result.put("BondState", "NONE");
                break;
            case BluetoothDevice.BOND_BONDING:
                result.put("BondState", "BONDING");
                break;
            case BluetoothDevice.BOND_BONDED:
                result.put("BondState", "BONDED");
        }
        result.put("Name", data.getName());
        return result;
    }
}
