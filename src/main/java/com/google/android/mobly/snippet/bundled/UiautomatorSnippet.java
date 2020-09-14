/*
 * Copyright (C) 2020 Google Inc.
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

import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Snippet class exposing Android APIs in Uiautomator. */
public class UiautomatorSnippet implements Snippet {
    private static final String TAG = UiautomatorSnippet.class.getCanonicalName();
    private static final UiDevice device =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Rpc(description = "Dumps UI hierarchy XML and return as string.")
    public String uiautomatorDumpWindowHierarchy() throws IOException {
        String res = "";
        try {
            OutputStream outStream = new ByteArrayOutputStream();
            device.dumpWindowHierarchy(outStream);
            res = outStream.toString();

        } catch (IOException e) {
            res = "Dump error.";
            throw e;
        }
        return res;
    }

    @Override
    public void shutdown() {}
}
