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
import android.os.Handler;
import android.support.test.InstrumentationRegistry;
import android.widget.Toast;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class exposing Android APIs related to creating notification on screen. */
public class NotificationSnippet implements Snippet {

    private final Context mContext;
    /**
     * Since the APIs here deal with UI, most of them have to be called in a thread that has called
     * looper.
     */
    private final Handler mHandler;

    public NotificationSnippet() {
        mContext = InstrumentationRegistry.getContext();
        mHandler = new Handler(mContext.getMainLooper());
    }

    @Rpc(description = "Make a toast on screen.")
    public void makeToast(String message) {
        mHandler.post(() -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void shutdown() {}
}
