/*
 * Copyright (C) 2025 Google Inc.
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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonSerializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import java.util.ArrayList;

/** Snippet class exposing Bluetooth LE Audio profile. */
public class BluetoothLeAudioSnippet implements Snippet {

    private static boolean sIsLeAudioProfileReady = false;
    private static BluetoothLeAudio sLeAudioProfile;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();

    public BluetoothLeAudioSnippet() {
        Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.getProfileProxy(
                mContext, new LeAudioServiceListener(), BluetoothProfile.LE_AUDIO);
        Utils.waitUntil(() -> sIsLeAudioProfileReady, 60);
    }

    /** Service Listener for {@link BluetoothLeAudio}. */
    private static class LeAudioServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profileType, BluetoothProfile profile) {
            sLeAudioProfile = (BluetoothLeAudio) profile;
            sIsLeAudioProfileReady = true;
        }

        @Override
        public void onServiceDisconnected(int profileType) {
            sIsLeAudioProfileReady = false;
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @RpcMinSdk(Build.VERSION_CODES.TIRAMISU)
    @Rpc(description = "Gets all the devices currently connected via LE Audio profile.")
    public ArrayList<Bundle> btLeAudioGetConnectedDevices() {
        return mJsonSerializer.serializeBluetoothDeviceList(sLeAudioProfile.getConnectedDevices());
    }
}
