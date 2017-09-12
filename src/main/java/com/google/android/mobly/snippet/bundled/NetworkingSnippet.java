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
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;

/** Snippet class for networking RPCs. */
public class NetworkingSnippet implements Snippet {

    @Rpc(description = "Check if a host and port are connectable using a TCP connection attempt.")
    public boolean isTcpConnectable(String host, int port) {
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

    @Override
    public void shutdown() {}
}
