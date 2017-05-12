package com.google.android.mobly.snippet.bundled.enums;

import android.annotation.TargetApi;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import com.google.android.mobly.snippet.bundled.utils.RpcEnum;

/** Class for {@link RpcEnum} objects that represent classes introduced in Android API 21. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class Api21Enums {

    public static RpcEnum bleAdvertiseTxPowerEnums =
            new RpcEnum.Builder()
                    .add(
                            "ADVERTISE_TX_POWER_ULTRA_LOW",
                            AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                    .add("ADVERTISE_TX_POWER_LOW", AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                    .add("ADVERTISE_TX_POWER_MEDIUM", AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .add("ADVERTISE_TX_POWER_HIGH", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build();
    public static RpcEnum bleAdvertiseModeEnums =
            new RpcEnum.Builder()
                    .add("ADVERTISE_MODE_BALANCED", AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .add("ADVERTISE_MODE_LOW_LATENCY", AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .add("ADVERTISE_MODE_LOW_POWER", AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                    .build();
    public static RpcEnum bleScanFailedErrorCodeEnums =
            new RpcEnum.Builder()
                    .add("SCAN_FAILED_ALREADY_STARTED", ScanCallback.SCAN_FAILED_ALREADY_STARTED)
                    .add(
                            "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED",
                            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)
                    .add(
                            "SCAN_FAILED_FEATURE_UNSUPPORTED",
                            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED)
                    .add("SCAN_FAILED_INTERNAL_ERROR", ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
                    .build();
    public static RpcEnum bleScanResultCallbackTypeEnums =
            new RpcEnum.Builder()
                    .add("CALLBACK_TYPE_ALL_MATCHES", ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .add("CALLBACK_TYPE_FIRST_MATCH", ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                    .add("CALLBACK_TYPE_MATCH_LOST", ScanSettings.CALLBACK_TYPE_MATCH_LOST)
                    .build();
}
