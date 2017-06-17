package com.google.android.mobly.snippet.bundled.utils;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanSettings;
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
}
