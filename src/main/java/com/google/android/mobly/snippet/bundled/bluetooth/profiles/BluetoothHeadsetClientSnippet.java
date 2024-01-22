package com.google.android.mobly.snippet.bundled.bluetooth.profiles;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.bosch.giftdroid.snippet.bundled.utils.JsonSerializer;
import com.bosch.giftdroid.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcMinSdk;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import android.util.Log;

public class BluetoothHeadsetClientSnippet implements Snippet {
    private static class BluetoothHeadsetClientSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        BluetoothHeadsetClientSnippetException(String msg) {
            super(msg);
        }
    }

    private final String TAG = "BluetoothHeadsetClientSnippet";
    private static boolean sIsHFPProfileReady = false;
    private Context mContext;
    private BluetoothHeadsetClient mBluetoothHeadsetClient;
    private BluetoothProfile.ServiceListener mServiceListenner;
    private static final int HEADSET_CLIENT = 16;
    private final JsonSerializer mJsonSerializer = new JsonSerializer();
    private static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothHeadsetClientCall m_CurrentCall = null;
    private boolean isAudioStateChanged = false;
    private boolean isCallStateChanged = false;
    private int bluetoothHeadsetAudioState = BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED;
    private int m_CurrentCallState = BluetoothHeadsetClientCall.CALL_STATE_TERMINATED;
    private BluetoothDevice mBluetoothConnectedDevice;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver onReceive");
            String action = intent.getAction();
            android.util.Log.d(TAG, action);
            if (BluetoothHeadsetClient.ACTION_CALL_CHANGED.equals(action)) {
                android.util.Log.d(TAG, action);
                m_CurrentCall = intent.getParcelableExtra(BluetoothHeadsetClient.EXTRA_CALL);
                android.util.Log.d(TAG, m_CurrentCall.toString());
            }
            if (BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                android.util.Log.d(TAG, action);
            }
        }
    };
    private final BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int var1, BluetoothProfile profile) {
            Log.d(TAG, "onServiceConnected" + var1);
            if (var1 == HEADSET_CLIENT) {
                mBluetoothHeadsetClient = (BluetoothHeadsetClient) profile;
                sIsHFPProfileReady = true;
            }
        }

        public void onServiceDisconnected(int var1) {
            Log.d(TAG, "onServiceDisconnected" + var1);
            if (var1 == HEADSET_CLIENT) {
                sIsHFPProfileReady = false;
                mBluetoothHeadsetClient = null;
            }
        }
    };

    public BluetoothHeadsetClientSnippet() {
        Log.d(TAG, "BluetoothHeadsetClientSnippet");
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        IntentFilter filter = new IntentFilter(BluetoothHeadsetClient.ACTION_CALL_CHANGED);
        filter.addAction(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadsetClient.ACTION_AUDIO_STATE_CHANGED);
        mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, HEADSET_CLIENT);
        Utils.waitUntil(() -> sIsHFPProfileReady, 60);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Rpc(description = "Set connection policy for HFP profile.")
    public boolean btheadsetClientset(String Name, int connectionPolicy) {
        Log.d(TAG, "btheadsetClientset");
        if (mBluetoothHeadsetClient == null) {
            android.util.Log.d(TAG, "btheadsetClientset mBluetoothHeadsetClient is NULL");
            return false;
        } else {
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equalsIgnoreCase(Name)) {
                    boolean status = mBluetoothHeadsetClient.setConnectionPolicy(device, connectionPolicy);
                    Log.d(TAG, "btheadsetClientset status:" + status);
                    return status;
                }
            }
        }
        return false;
    }

    @Rpc(description = "Connects to remote device.")
    public boolean btHfpcconnect(String Name) {
        Log.d(TAG, "btHfpcconnect");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(Name)) {
                boolean status = mBluetoothHeadsetClient.connect(device);
                Log.d(TAG, "btHfpcconnect status:" + status);
                return status;
            }
        }
        return false;
    }

    @Rpc(description = "disConnects to remote device.")
    public boolean btHfpcdisconnect(String Name) {
        Log.d(TAG, "btHfpcdisconnect");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(Name)) {
                boolean status = mBluetoothHeadsetClient.disconnect(device);
                Log.d(TAG, "btHfpcdisconnect status:" + status);
                return status;
            }
        }
        return false;
    }

    @Rpc(description = " Returns connection state.")
    public int btHfpgetConnectionState(String Name) {
        Log.d(TAG, "btHfpgetConnectionState");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(Name)) {
                Log.d(TAG, "btHfpgetConnectionState");
                return mBluetoothHeadsetClient.getConnectionState(device);
            }
        }
        return 0;
    }

    @Rpc(description = "Starts voice recognition.")
    public boolean btHfpstartVoiceRecognition(String Name) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(Name)) {
                return mBluetoothHeadsetClient.startVoiceRecognition(device);
            }
        }
        return false;
    }

    @Rpc(description = "Stops voice recognition.")
    public boolean btHfpstopVoiceRecognition(String Name) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equalsIgnoreCase(Name)) {
                return mBluetoothHeadsetClient.stopVoiceRecognition(device);
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        Log.d(TAG, "shutdown");
    }
}
