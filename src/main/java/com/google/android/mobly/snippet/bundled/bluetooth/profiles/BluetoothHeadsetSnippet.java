/*
 * Copyright (C) 2024 Google Inc.
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

package com.google.android.mobly.snippet.bundled.bluetooth.profiles;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.bluetooth.BluetoothAdapterSnippet;
import com.google.android.mobly.snippet.bundled.bluetooth.PairingBroadcastReceiver;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import android.util.Log;

public class BluetoothHeadsetSnippet implements Snippet {
    private static class BluetoothHeadsetSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        BluetoothHeadsetSnippetException(String msg) {
            super(msg);
        }
    }

    private final String TAG = "BluetoothHeadsetSnippet";
    private static boolean sIsHFPProfileReady = false;
    private Context mContext;
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothProfile.ServiceListener mServiceListenner;
    private static final int HEADSET = 1;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothDevice mBluetoothConnectedDevice;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver onReceive");
            String action = intent.getAction();
            android.util.Log.d(TAG, action);
            if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                android.util.Log.d(TAG, action);
            }
        }
    };
    private final BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int var1, BluetoothProfile profile) {
            Log.d(TAG, "onServiceConnected" + var1);
            if (var1 == HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset)profile;
                sIsHFPProfileReady = true;
            }
        }

        public void onServiceDisconnected(int var1) {
            Log.d(TAG, "onServiceDisconnected" + var1);
            if (var1 == HEADSET) {
                sIsHFPProfileReady = false;
                mBluetoothHeadset = null;
            }
        }
    };

    public BluetoothHeadsetSnippet() {
        Log.d(TAG, "BluetoothHeadsetSnippet");
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        IntentFilter filter = new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, HEADSET);
        Utils.waitUntil(() -> sIsHFPProfileReady, 60);
        mContext.registerReceiver(mReceiver, filter);
    }


    @Rpc(description = " Returns connection state.")
    public int btHfpgetConnectionState(String name) {
        Log.d(TAG, "btHfpgetConnectionState");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(name)) {
                Log.d(TAG, "btHfpgetConnectionState");
                return mBluetoothHeadset.getConnectionState(device);
            }
        }
        return 0;
    }

    @Rpc(description = "Starts voice recognition.")
    public boolean btHfpstartVoiceRecognition(String name) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(name)) {
                return mBluetoothHeadset.startVoiceRecognition(device);
            }
        }
        return false;
    }

    @Rpc(description = "Stops voice recognition.")
    public boolean btHfpstopVoiceRecognition(String name) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(name)) {
                return mBluetoothHeadset.stopVoiceRecognition(device);
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        Log.d(TAG, "shutdown");
    }
}
