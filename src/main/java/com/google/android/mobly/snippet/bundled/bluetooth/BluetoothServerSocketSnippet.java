package com.google.android.mobly.snippet.bundled.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class BluetoothServerSocketSnippet implements Snippet {

    private static class BluetoothServerSocketSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public BluetoothServerSocketSnippetException(String msg) {
            super(msg);
        }

    }

    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mBluetoothSocket;
    private BluetoothServerSocket mBluetoothServerSocket;
    private final EventCache mEventCache;


    public BluetoothServerSocketSnippet() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mEventCache = EventCache.getInstance();
    }

    @SuppressLint("MissingPermission")
    @AsyncRpc(description = "Receive file from the connected device.")
    public void btReceiveFile(String callbackId, String uuid, String filePath)
            throws BluetoothServerSocketSnippetException, IOException {
        if (mBluetoothAdapter == null) {
            throw new BluetoothServerSocketSnippetException("Bluetooth not supported");
        }
        mBluetoothServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(callbackId,
                java.util.UUID.fromString(uuid));
        new Thread(() -> {
            try {
                mBluetoothSocket = mBluetoothServerSocket.accept();
                receiveFile(mBluetoothSocket, filePath, callbackId);
            } catch (IOException e) {
                try {
                    mBluetoothServerSocket.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                mBluetoothServerSocket = null;
            }
        }).start();
    }

    private void receiveFile(BluetoothSocket socket, String filePath, String callbackId)
            throws IOException {
        boolean transferSuccess = false;
        DataInputStream dis = null;
        FileOutputStream fos = null;
        try {
            dis = new DataInputStream(socket.getInputStream());
            fos = new FileOutputStream(filePath);
            long fileSize = dis.readLong();
            byte[] buffer = new byte[102400];
            long totalReceived = 0;
            int chunkSize;
            while (totalReceived < fileSize && (chunkSize = dis.read(buffer)) != -1) {
                fos.write(buffer, 0, chunkSize);
                totalReceived += chunkSize;
            }
            if (totalReceived == fileSize) {
                transferSuccess = true;
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("ACK");
                dos.flush();

                SnippetEvent event = new SnippetEvent(callbackId, "onFileReceived");
                event.getData().putString("filePath", filePath);
                event.getData().putLong("fileSize", fileSize);
                mEventCache.postEvent(event);

            } else {
                throw new IOException("File incomplete. Received: " + totalReceived);
            }
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {
                }
            }
            if (!transferSuccess) {
                closeSocket(socket);
            }
        }
    }


    @Rpc(description = "Close the server socket.")
    public void btCloseServerSocket() throws IOException {
        if (mBluetoothSocket != null) {
            mBluetoothSocket.close();
            mBluetoothSocket = null;
        }
        if (mBluetoothServerSocket != null) {
            mBluetoothServerSocket.close();
            mBluetoothServerSocket = null;
        }
    }

    private static void closeSocket(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void shutdown() throws IOException {
        btCloseServerSocket();
    }
}