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

import android.os.Build;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class exposing Android build information. */
public class AndroidBuildSnippet implements Snippet {

    @Rpc(description = "Get the int representation of the build's SDK version.")
    public int buildVersionSdkInt() {
        return Build.VERSION.SDK_INT;
    }

    @Override
    public void shutdown() {}
}
