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

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Snippet class for File and abstract storage URI operation RPCs. */
public class FileSnippet implements Snippet {

    private final Context mContext;

    public FileSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Rpc(description = "Compute MD5 hash on a content URI. Return the MD5 has has a hex string.")
    public String fileMd5Hash(String uri) throws IOException, NoSuchAlgorithmException {
        Uri uri_ = Uri.parse(uri);
        ParcelFileDescriptor pfd = mContext.getContentResolver().openFileDescriptor(uri_, "r");
        MessageDigest md = MessageDigest.getInstance("MD5");
        int length = (int) pfd.getStatSize();
        byte[] buf = new byte[length];
        ParcelFileDescriptor.AutoCloseInputStream stream =
                new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        DigestInputStream dis = new DigestInputStream(stream, md);
        try {
            dis.read(buf, 0, length);
            return Utils.bytesToHexString(md.digest());
        } finally {
            dis.close();
            stream.close();
        }
    }

    @Rpc(description = "Remove a file pointed to by the content URI.")
    public void fileDeleteContent(String uri) {
        Uri uri_ = Uri.parse(uri);
        mContext.getContentResolver().delete(uri_, null, null);
    }

    @Override
    public void shutdown() {}
}
