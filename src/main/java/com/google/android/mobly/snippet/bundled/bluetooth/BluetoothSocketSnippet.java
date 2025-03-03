package com.google.android.mobly.snippet.bundled.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BluetoothSocketSnippet implements Snippet {

    private static class BluetoothSocketSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothSocketSnippetException(String msg) {
            super(msg);
        }

    }

    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mBluetoothSocket;
    private final EventCache mEventCache;
    private final Context mContent;

    public BluetoothSocketSnippet() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mEventCache = EventCache.getInstance();
        mContent = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Rpc(description = "Returns true if BluetoothSocket is connected, false otherwise.")
    public boolean btIsSocketConnected() {
        return mBluetoothSocket != null && mBluetoothSocket.isConnected();
    }

    @SuppressLint("MissingPermission")
    @Rpc(description = "Create an RFCOMM BluetoothSocket and start a secure outgoing connection " +
            "to the remote device.")
    public void btConnectSocket(String deviceAddress, String uuid)
            throws BluetoothSocketSnippetException, IOException {
        BluetoothDevice device = getPairedDeviceByAddress(deviceAddress);
        mBluetoothSocket =
                device.createRfcommSocketToServiceRecord(java.util.UUID.fromString(uuid));
        mBluetoothSocket.connect();

    }

    @Rpc(description = "Close the BluetoothSocket.")
    public void btCloseSocket() throws IOException {
        if (mBluetoothSocket != null) {
            mBluetoothSocket.close();
            mBluetoothSocket = null;
        }
    }

    @AsyncRpc(description = "Send file to the connected device.")
    public void btSendFile(String callbackId, String filePath)
            throws BluetoothSocketSnippetException, IOException {
        if (!new File(filePath).exists()) {
            throw new BluetoothSocketSnippetException("File " + filePath + " not found.");
        }
        checkBluetoothSocked();
        if (!btIsSocketConnected()) {
            throw new BluetoothSocketSnippetException("Not connected to any device.");
        }
        new Thread(() -> {
            try {
                sendFile(mBluetoothSocket, filePath, callbackId);

            } catch (IOException e) {
                try {
                    mBluetoothSocket.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                mBluetoothSocket = null;
            }
        }).start();
    }

    private void checkBluetoothSocked() throws BluetoothSocketSnippetException {
        if (mBluetoothSocket == null) {
            throw new BluetoothSocketSnippetException(
                    "`BluetoothSocket not init, Please call " + "`btConnectSocket` first.");

        }
    }

    private void sendFile(BluetoothSocket socket, String filePath, String callbackId)
            throws IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        FileInputStream fis = new FileInputStream(filePath);
        File file = new java.io.File(filePath);
        dos.writeLong(file.length());
        byte[] buffer = new byte[102400];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            dos.write(buffer, 0, bytesRead);
        }
        dos.flush();
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        String ack = dis.readUTF();
        if (!"ACK".equals(ack)) {
            throw new IOException("File transfer not acknowledged.");
        }
        SnippetEvent event = new SnippetEvent(callbackId, "onFileSent");
        event.getData().putString("filePath", filePath);
        event.getData().putLong("fileSize", file.length());
        mEventCache.postEvent(event);
    }

    @SuppressLint("MissingPermission")
    private BluetoothDevice getPairedDeviceByAddress(String deviceAddress)
            throws BluetoothSocketSnippetException {
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getAddress().equalsIgnoreCase(deviceAddress)) {
                return device;
            }
        }
        throw new BluetoothSocketSnippetException(
                "Failed to find device with address: " + deviceAddress +
                        "Please make sure the device is paired.");
    }

    @Override
    public void shutdown() throws IOException {
        btCloseSocket();
    }
}