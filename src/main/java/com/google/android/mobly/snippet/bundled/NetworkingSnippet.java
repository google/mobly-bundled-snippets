/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.mobly.snippet.bundled;

import java.net.InetAddress;
import java.net.Socket;
import java.io.IOException;
import java.net.UnknownHostException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.net.Uri;
import android.os.Environment;
import android.app.DownloadManager;
import android.support.test.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;

/** Snippet class for networking RPCs. */
public class NetworkingSnippet implements Snippet {

    private final DownloadManager mDownloadManager;
    private final Context mContext;
    private volatile boolean mIsDownloadComplete = false;

    public NetworkingSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    @Rpc(description = "Check if a host and port are connectable using a TCP connection attempt.")
    public boolean networkIsTcpConnectable(String host, int port) {
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException uherr) {
            Log.d("Host name lookup failure: " + uherr.getMessage());
            return false;
        }

        try {
            Socket sock = new Socket(addr, port);
            sock.close();
        } catch (IOException ioerr) {
            Log.d("Did not make connection to host: " + ioerr.getMessage());
            return false;
        }
        return true;
    }

    @Rpc(description = "Download a file using HTTP.")
    public String networkHTTPDownload(String url, String destination) {
        long reqid = 0;
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destination);
        mIsDownloadComplete = false;
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        BroadcastReceiver receiver = new DownloadReceiver();
        mContext.registerReceiver(receiver, filter);
        try {
            reqid = mDownloadManager.enqueue(request);
            Log.d(String.format("networkHTTPDownload download of %s with id %d", url, reqid));
            if (!Utils.waitUntil(() -> mIsDownloadComplete, 240)) {
                return "";
            }
        } finally {
            mContext.unregisterReceiver(receiver);
        }

        Uri resp = mDownloadManager.getUriForDownloadedFile(reqid);
        if (resp != null) {
            Log.d(String.format("networkHTTPDownload completed to %s", resp.toString()));
            return resp.toString();
        } else {
            return "";
        }
    }

    private class DownloadReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                mIsDownloadComplete = true;
            }
        }
    }

    @Override
    public void shutdown() {}
}
