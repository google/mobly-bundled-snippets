package com.google.android.mobly.snippet.bundled.enums;

import android.bluetooth.BluetoothDevice;
import com.google.android.mobly.snippet.bundled.utils.RpcEnum;

/** Class for {@link RpcEnum} objects that represent classes introduced in Android API 5. */
public class Api5Enums {
    public static RpcEnum bluetoothDeviceBondState =
            new RpcEnum.Builder()
                    .add("BOND_NONE", BluetoothDevice.BOND_NONE)
                    .add("BOND_BONDING", BluetoothDevice.BOND_BONDING)
                    .add("BOND_BONDED", BluetoothDevice.BOND_BONDED)
                    .build();
}
