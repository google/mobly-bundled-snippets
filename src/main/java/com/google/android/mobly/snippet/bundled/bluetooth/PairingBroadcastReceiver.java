package com.google.android.mobly.snippet.bundled.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import com.google.android.mobly.snippet.util.Log;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class PairingBroadcastReceiver extends BroadcastReceiver {
    private final Context mContext;
    public static IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);

    public PairingBroadcastReceiver(Context context) {
        mContext = context;
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d("Confirming pairing with device: " + device.getAddress());
            device.setPairingConfirmation(true);
            mContext.unregisterReceiver(this);
        }
    }
}
