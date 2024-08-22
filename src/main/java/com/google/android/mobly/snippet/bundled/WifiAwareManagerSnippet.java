/*
 * Copyright (C) 2024 Google Inc.
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
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class exposing Android APIs in WifiAwareManager. */
public class WifiAwareManagerSnippet implements Snippet {

    private static class WifiAwareManagerSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        public WifiAwareManagerSnippetException(String msg) {
            super(msg);
        }
    }
    private final Context mContext;
    private boolean mIsAwareSupported;
    WifiAwareManager mWifiAwareManager;

    public WifiAwareManagerSnippet() throws Throwable {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mIsAwareSupported =
            mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
        if (mIsAwareSupported) {
            mWifiAwareManager = (WifiAwareManager) mContext.getSystemService(Context.WIFI_AWARE_SERVICE);
        }
        Utils.adaptShellPermissionIfRequired(mContext);
    }

    /** Checks if Aware is available. This could return false if WiFi or location is disabled. */
    @Rpc(description = "check if Aware is available.")
    public boolean wifiAwareIsAvailable() throws WifiAwareManagerSnippetException {
        if (!mIsAwareSupported) {
            throw new WifiAwareManagerSnippetException(
                    "WifiAware is not supported in this device");
        }
        return mWifiAwareManager.isAvailable();
    }
}
