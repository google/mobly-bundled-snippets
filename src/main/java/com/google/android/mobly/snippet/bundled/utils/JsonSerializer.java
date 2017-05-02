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
import android.os.Build;
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
        guaranteedPut(result, "SSID", trimQuotationMarks(data.SSID));
        return result;
    }

    private JSONObject serializeWifiInfo(WifiInfo data) throws JSONException {
        JSONObject result = new JSONObject(mGson.toJson(data));
        guaranteedPut(result, "SSID", trimQuotationMarks(data.getSSID()));
        for (SupplicantState state : SupplicantState.values()) {
            if (data.getSupplicantState().equals(state)) {
                result.put("SupplicantState", state.name());
            }
        }
        return result;
    }

    private JSONObject serializeBluetoothDevice(BluetoothDevice data) throws JSONException {
        JSONObject result = new JSONObject();
        guaranteedPut(result, "Address", data.getAddress());
        final String bondStateFieldName = "BondState";
        switch (data.getBondState()) {
            case BluetoothDevice.BOND_NONE:
                result.put(bondStateFieldName, "BOND_NONE");
                break;
            case BluetoothDevice.BOND_BONDING:
                result.put(bondStateFieldName, "BOND_BONDING");
                break;
            case BluetoothDevice.BOND_BONDED:
                result.put(bondStateFieldName, "BOND_BONDED");
                break;
        }
        guaranteedPut(result, "Name", data.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            final String deviceTypeFieldName = "DeviceType";
            switch (data.getType()) {
                case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                    result.put(deviceTypeFieldName, "DEVICE_TYPE_CLASSIC");
                    break;
                case BluetoothDevice.DEVICE_TYPE_LE:
                    result.put(deviceTypeFieldName, "DEVICE_TYPE_LE");
                    break;
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    result.put(deviceTypeFieldName, "DEVICE_TYPE_DUAL");
                    break;
                case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                    result.put(deviceTypeFieldName, "DEVICE_TYPE_UNKNOWN");
                    break;
            }
            ParcelUuid[] parcelUuids = data.getUuids();
            if (parcelUuids != null) {
                ArrayList<String> uuidStrings = new ArrayList<>(parcelUuids.length);
                for (ParcelUuid parcelUuid : parcelUuids) {
                    uuidStrings.add(parcelUuid.getUuid().toString());
                }
                result.put("UUIDs", uuidStrings);
            }
        }
        return result;
    }

    /**
     * Guarantees a field is put into a JSONObject even if it's null.
     *
     * <p>By default, if the object of {@link JSONObject#put(String, Object)} is null, the `put`
     * method would either remove the field or do nothing, causing serialized objects to have
     * inconsistent fields.
     *
     * <p>Use this method to put objects that may be null into the serialized JSONObject so the
     * serialized objects have a consistent set of critical fields, like the SSID field in
     * serialized WifiConfiguration objects.
     *
     * @param data
     * @param name
     * @param object
     * @throws JSONException
     */
    private void guaranteedPut(JSONObject data, String name, Object object) throws JSONException {
        if (object == null) {
            data.put(name, JSONObject.NULL);
        } else {
            data.put(name, object);
        }
    }
}
