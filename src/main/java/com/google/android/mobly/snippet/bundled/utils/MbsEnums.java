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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanSettings;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.os.Build;

/** Mobly Bundled Snippets (MBS)'s {@link RpcEnum} objects representing enums in Android APIs. */
public class MbsEnums {
    static final RpcEnum BLE_ADVERTISE_MODE = buildBleAdvertiseModeEnum();
    static final RpcEnum BLE_ADVERTISE_TX_POWER = buildBleAdvertiseTxPowerEnum();
    public static final RpcEnum BLE_SCAN_FAILED_ERROR_CODE = buildBleScanFailedErrorCodeEnum();
    public static final RpcEnum BLE_SCAN_RESULT_CALLBACK_TYPE =
            buildBleScanResultCallbackTypeEnum();
    static final RpcEnum BLUETOOTH_DEVICE_BOND_STATE = buildBluetoothDeviceBondState();
    static final RpcEnum BLUETOOTH_DEVICE_TYPE = buildBluetoothDeviceTypeEnum();
    static final RpcEnum BLE_SERVICE_TYPE = buildServiceTypeEnum();
    public static final RpcEnum BLE_STATUS_TYPE = buildStatusTypeEnum();
    public static final RpcEnum BLE_CONNECT_STATUS = buildConnectStatusEnum();
    static final RpcEnum BLE_PROPERTY_TYPE = buildPropertyTypeEnum();
    static final RpcEnum BLE_PERMISSION_TYPE = buildPermissionTypeEnum();
    static final RpcEnum BLE_SCAN_MODE = buildBleScanModeEnum();
    public static final RpcEnum LOCAL_HOTSPOT_FAIL_REASON = buildLocalHotspotFailedReason();
    public static final RpcEnum ADVERTISE_FAILURE_ERROR_CODE =
            new RpcEnum.Builder().add("ADVERTISE_FAILED_ALREADY_STARTED",
                                     AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED)
                    .add("ADVERTISE_FAILED_DATA_TOO_LARGE",
                        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE)
                    .add(
                        "ADVERTISE_FAILED_FEATURE_UNSUPPORTED",
                        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED)
                    .add("ADVERTISE_FAILED_INTERNAL_ERROR",
                        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
                    .add(
                        "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS",
                        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS)
                    .build();

    private static RpcEnum buildBluetoothDeviceBondState() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        return builder.add("BOND_NONE", BluetoothDevice.BOND_NONE)
                .add("BOND_BONDING", BluetoothDevice.BOND_BONDING)
                .add("BOND_BONDED", BluetoothDevice.BOND_BONDED)
                .build();
    }

    private static RpcEnum buildBluetoothDeviceTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return builder.build();
        }
        return builder.add("DEVICE_TYPE_CLASSIC", BluetoothDevice.DEVICE_TYPE_CLASSIC)
                .add("DEVICE_TYPE_LE", BluetoothDevice.DEVICE_TYPE_LE)
                .add("DEVICE_TYPE_DUAL", BluetoothDevice.DEVICE_TYPE_DUAL)
                .add("DEVICE_TYPE_UNKNOWN", BluetoothDevice.DEVICE_TYPE_UNKNOWN)
                .build();
    }

    private static RpcEnum buildBleAdvertiseTxPowerEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        return builder.add(
                        "ADVERTISE_TX_POWER_ULTRA_LOW",
                        AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                .add("ADVERTISE_TX_POWER_LOW", AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .add("ADVERTISE_TX_POWER_MEDIUM", AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .add("ADVERTISE_TX_POWER_HIGH", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();
    }

    private static RpcEnum buildBleAdvertiseModeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        return builder.add("ADVERTISE_MODE_BALANCED", AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .add("ADVERTISE_MODE_LOW_LATENCY", AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .add("ADVERTISE_MODE_LOW_POWER", AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .build();
    }

    private static RpcEnum buildBleScanFailedErrorCodeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        return builder.add("SCAN_FAILED_ALREADY_STARTED", ScanCallback.SCAN_FAILED_ALREADY_STARTED)
                .add(
                        "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED",
                        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)
                .add(
                        "SCAN_FAILED_FEATURE_UNSUPPORTED",
                        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED)
                .add("SCAN_FAILED_INTERNAL_ERROR", ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
                .build();
    }

    private static RpcEnum buildBleScanResultCallbackTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder.add("CALLBACK_TYPE_ALL_MATCHES", ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.add("CALLBACK_TYPE_FIRST_MATCH", ScanSettings.CALLBACK_TYPE_FIRST_MATCH);
            builder.add("CALLBACK_TYPE_MATCH_LOST", ScanSettings.CALLBACK_TYPE_MATCH_LOST);
        }
        return builder.build();
    }

    private static RpcEnum buildServiceTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder.add("SERVICE_TYPE_PRIMARY", BluetoothGattService.SERVICE_TYPE_PRIMARY);
        builder.add("SERVICE_TYPE_SECONDARY", BluetoothGattService.SERVICE_TYPE_SECONDARY);
        return builder.build();
    }

    private static RpcEnum buildStatusTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder.add("GATT_SUCCESS", BluetoothGatt.GATT_SUCCESS)
                .add("GATT_CONNECTION_CONGESTED", BluetoothGatt.GATT_CONNECTION_CONGESTED)
                .add("GATT_FAILURE", BluetoothGatt.GATT_FAILURE)
                .add("GATT_INSUFFICIENT_AUTHENTICATION",
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION)
                .add("GATT_INSUFFICIENT_ENCRYPTION", BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION)
                .add("GATT_INVALID_ATTRIBUTE_LENGTH", BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH)
                .add("GATT_INVALID_OFFSET", BluetoothGatt.GATT_INVALID_OFFSET)
                .add("GATT_READ_NOT_PERMITTED", BluetoothGatt.GATT_READ_NOT_PERMITTED)
                .add("GATT_REQUEST_NOT_SUPPORTED", BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED)
                .add("GATT_WRITE_NOT_PERMITTED", BluetoothGatt.GATT_WRITE_NOT_PERMITTED)
                .add("BLE_HCI_REMOTE_USER_TERMINATED_CONNECTION", 0x13)
                .add("BLE_HCI_LOCAL_HOST_TERMINATED_CONNECTION", 0x12)
                .add("BLE_HCI_STATUS_CODE_LMP_RESPONSE_TIMEOUT", 0x22)
                .add("BLE_HCI_CONN_FAILED_TO_BE_ESTABLISHED", 0x3e)
                .add("UNEXPECTED_DISCONNECT_NO_ERROR_CODE", 134)
                .add("DID_NOT_FIND_OFFLINEP2P_SERVICE", 135)
                .add("MISSING_CHARACTERISTIC", 137)
                .add("CONNECTION_TIMEOUT", 138)
                .add("READ_MALFORMED_VERSION", 139)
                .add("READ_WRITE_VERSION_NONSPECIFIC_ERROR", 140)
                .add("GATT_0C_err", 0X0C)
                .add("GATT_16", 0x16)
                .add("GATT_INTERNAL_ERROR", 129)
                .add("BLE_HCI_CONNECTION_TIMEOUT", 0x08)
                .add("GATT_ERROR", 133);
        return builder.build();
    }

    private static RpcEnum buildConnectStatusEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder.add("STATE_CONNECTED", BluetoothProfile.STATE_CONNECTED)
                .add("STATE_CONNECTING", BluetoothProfile.STATE_CONNECTING)
                .add("STATE_DISCONNECTED", BluetoothProfile.STATE_DISCONNECTED)
                .add("STATE_DISCONNECTING", BluetoothProfile.STATE_DISCONNECTING);
        return builder.build();
    }

    private static RpcEnum buildPropertyTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder
                .add("PROPERTY_NONE", 0)
                .add("PROPERTY_BROADCAST", BluetoothGattCharacteristic.PROPERTY_BROADCAST)
                .add("PROPERTY_EXTENDED_PROPS", BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS)
                .add("PROPERTY_INDICATE", BluetoothGattCharacteristic.PROPERTY_INDICATE)
                .add("PROPERTY_NOTIFY", BluetoothGattCharacteristic.PROPERTY_NOTIFY)
                .add("PROPERTY_READ", BluetoothGattCharacteristic.PROPERTY_READ)
                .add("PROPERTY_SIGNED_WRITE", BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE)
                .add("PROPERTY_WRITE", BluetoothGattCharacteristic.PROPERTY_WRITE)
                .add("PROPERTY_WRITE_NO_RESPONSE",
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE);
        return builder.build();
    }

    private static RpcEnum buildPermissionTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder.add("PERMISSION_NONE", 0)
              .add("PERMISSION_READ", BluetoothGattCharacteristic.PERMISSION_READ)
              .add("PERMISSION_READ_ENCRYPTED",
                  BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED)
              .add("PERMISSION_READ_ENCRYPTED_MITM",
                  BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM)
              .add("PERMISSION_WRITE", BluetoothGattCharacteristic.PERMISSION_WRITE)
              .add("PERMISSION_WRITE_ENCRYPTED",
                  BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED)
              .add("PERMISSION_WRITE_ENCRYPTED_MITM",
                  BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM)
              .add("PERMISSION_WRITE_SIGNED", BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED)
              .add("PERMISSION_WRITE_SIGNED_MITM",
                  BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM);
        return builder.build();
    }

    private static RpcEnum buildBleScanModeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder.add("SCAN_MODE_LOW_POWER", ScanSettings.SCAN_MODE_LOW_POWER)
                .add("SCAN_MODE_BALANCED", ScanSettings.SCAN_MODE_BALANCED)
                .add("SCAN_MODE_LOW_LATENCY", ScanSettings.SCAN_MODE_LOW_LATENCY);
        return builder.build();
    }

    private static RpcEnum buildLocalHotspotFailedReason() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return builder.build();
        }
        builder.add("ERROR_TETHERING_DISALLOWED",
                   LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED)
                .add("ERROR_INCOMPATIBLE_MODE", LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE)
                .add("ERROR_NO_CHANNEL", LocalOnlyHotspotCallback.ERROR_NO_CHANNEL)
                .add("ERROR_GENERIC", LocalOnlyHotspotCallback.ERROR_GENERIC);
        return builder.build();
    }
}
