package com.google.android.mobly.snippet.bundled.utils;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanSettings;
import android.os.Build;

/** Mobly Bundled Snippets (MBS)'s {@link RpcEnum} objects representing enums in Android APIs. */
public class MbsEnums {
    static RpcEnum bleAdvertiseModeEnum = buildBleAdvertiseModeEnum();
    static RpcEnum bleAdvertiseTxPowerEnum = buildBleAdvertiseTxPowerEnum();
    public static RpcEnum bleScanFailedErrorCodeEnum = buildBleScanFailedErrorCodeEnum();
    public static RpcEnum bleScanResultCallbackTypeEnum = buildBleScanResultCallbackTypeEnum();
    static RpcEnum bluetoothDeviceBondStateEnum = buildBluetoothDeviceBondState();
    static RpcEnum bluetoothDeviceTypeEnum = buildBluetoothDeviceTypeEnum();

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private static RpcEnum buildBluetoothDeviceBondState() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        return builder.add("BOND_NONE", BluetoothDevice.BOND_NONE)
                .add("BOND_BONDING", BluetoothDevice.BOND_BONDING)
                .add("BOND_BONDED", BluetoothDevice.BOND_BONDED)
                .build();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static RpcEnum buildBluetoothDeviceTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return builder.build();
        }
        return builder.setMinSdk(Build.VERSION_CODES.JELLY_BEAN_MR2)
                .add("DEVICE_TYPE_CLASSIC", BluetoothDevice.DEVICE_TYPE_CLASSIC)
                .add("DEVICE_TYPE_LE", BluetoothDevice.DEVICE_TYPE_LE)
                .add("DEVICE_TYPE_DUAL", BluetoothDevice.DEVICE_TYPE_DUAL)
                .build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static RpcEnum buildBleAdvertiseTxPowerEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        return builder.setMinSdk(Build.VERSION_CODES.LOLLIPOP)
                .add("ADVERTISE_TX_POWER_ULTRA_LOW", AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                .add("ADVERTISE_TX_POWER_LOW", AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .add("ADVERTISE_TX_POWER_MEDIUM", AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .add("ADVERTISE_TX_POWER_HIGH", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static RpcEnum buildBleAdvertiseModeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        return builder.setMinSdk(Build.VERSION_CODES.LOLLIPOP)
                .add("ADVERTISE_MODE_BALANCED", AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .add("ADVERTISE_MODE_LOW_LATENCY", AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .add("ADVERTISE_MODE_LOW_POWER", AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static RpcEnum buildBleScanFailedErrorCodeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        return builder.setMinSdk(Build.VERSION_CODES.LOLLIPOP)
                .add("SCAN_FAILED_ALREADY_STARTED", ScanCallback.SCAN_FAILED_ALREADY_STARTED)
                .add(
                        "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED",
                        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)
                .add(
                        "SCAN_FAILED_FEATURE_UNSUPPORTED",
                        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED)
                .add("SCAN_FAILED_INTERNAL_ERROR", ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
                .build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static RpcEnum buildBleScanResultCallbackTypeEnum() {
        RpcEnum.Builder builder = new RpcEnum.Builder();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return builder.build();
        }
        builder.setMinSdk(Build.VERSION_CODES.LOLLIPOP)
                .add("CALLBACK_TYPE_ALL_MATCHES", ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.add("CALLBACK_TYPE_FIRST_MATCH", ScanSettings.CALLBACK_TYPE_FIRST_MATCH);
            builder.add("CALLBACK_TYPE_MATCH_LOST", ScanSettings.CALLBACK_TYPE_MATCH_LOST);
        }
        return builder.build();
    }
}
