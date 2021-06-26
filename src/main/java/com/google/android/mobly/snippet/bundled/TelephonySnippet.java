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
import android.telephony.TelephonyManager;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for telephony RPCs. */
public class TelephonySnippet implements Snippet {

    private final TelephonyManager mTelephonyManager;

    public TelephonySnippet() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Rpc(description = "Gets the line 1 phone number.")
    public String getLine1Number() {
        return mTelephonyManager.getLine1Number();
    }

    @Rpc(description = "Returns the unique subscriber ID, for example, the IMSI for a GSM phone.")
    public String getSubscriberId() {
        return mTelephonyManager.getSubscriberId();
    }

    @Rpc(
            description =
                    "Gets the call state for the default subscription. Call state values are"
                            + "0: IDLE, 1: RINGING, 2: OFFHOOK")
    public int getTelephonyCallState() {
        return mTelephonyManager.getCallState();
    }

    @Rpc(
            description =
                    "Returns a constant indicating the radio technology (network type) currently"
                            + "in use on the device for data transmission.")
    public int getDataNetworkType() {
      return mTelephonyManager.getDataNetworkType();
    }

    @Rpc(
            description =
                    "Returns a constant indicating the radio technology (network type) currently"
                            + "in use on the device for voice transmission.")
    public int getVoiceNetworkType() {
      return mTelephonyManager.getVoiceNetworkType();
    }
    @Override
    public void shutdown() {}
}
