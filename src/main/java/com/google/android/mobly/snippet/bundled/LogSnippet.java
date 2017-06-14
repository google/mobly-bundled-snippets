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

import android.util.Log;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class exposing Android APIs related to logging. */
public class LogSnippet implements Snippet {
    private String mTag = "MoblyTestLog";

    @Rpc(description = "Set the tag to use for logX Rpcs. Default is 'MoblyTestLog'.")
    public void logSetTag(String tag) {
        mTag = tag;
    }

    @Rpc(description = "Log at info level.")
    public void logI(String message) {
        Log.i(mTag, message);
    }

    @Rpc(description = "Log at debug level.")
    public void logD(String message) {
        Log.d(mTag, message);
    }

    @Rpc(description = "Log at error level.")
    public void logE(String message) {
        Log.e(mTag, message);
    }

    @Rpc(description = "Log at warning level.")
    public void logW(String message) {
        Log.w(mTag, message);
    }

    @Rpc(description = "Log at verbose level.")
    public void logV(String message) {
        Log.v(mTag, message);
    }

    @Rpc(description = "Log at WTF level.")
    public void logWtf(String message) {
        Log.wtf(mTag, message);
    }

    @Override
    public void shutdown() {}
}
