package com.google.android.mobly.snippet.bundled.enums;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import com.google.android.mobly.snippet.bundled.utils.RpcEnum;

/** Class for {@link RpcEnum} objects that represent classes introduced in Android API 18. */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class Api18Enums {
    public static RpcEnum bluetoothDeviceTypeEnums =
            new RpcEnum.Builder()
                    .add("DEVICE_TYPE_CLASSIC", BluetoothDevice.DEVICE_TYPE_CLASSIC)
                    .add("DEVICE_TYPE_LE", BluetoothDevice.DEVICE_TYPE_LE)
                    .add("DEVICE_TYPE_DUAL", BluetoothDevice.DEVICE_TYPE_DUAL)
                    .build();
}
