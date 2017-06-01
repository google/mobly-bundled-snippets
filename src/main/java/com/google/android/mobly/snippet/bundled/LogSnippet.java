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

    @Rpc(description = "Log at info level.")
    public void logI(String tag, String message) {
        Log.i(tag, message);
    }

    @Rpc(description = "Log at debug level.")
    public void logD(String tag, String message) {
        Log.d(tag, message);
    }

    @Rpc(description = "Log at error level.")
    public void logE(String tag, String message) {
        Log.e(tag, message);
    }

    @Rpc(description = "Log at warning level.")
    public void logW(String tag, String message) {
        Log.w(tag, message);
    }

    @Rpc(description = "Log at verbose level.")
    public void logV(String tag, String message) {
        Log.v(tag, message);
    }

    @Rpc(description = "Log at WTF level.")
    public void logWTF(String tag, String message) {
        Log.wtf(tag, message);
    }

    @Override
    public void shutdown() {}
}
