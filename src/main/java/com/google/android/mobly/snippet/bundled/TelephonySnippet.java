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
import android.support.test.InstrumentationRegistry;
import android.telephony.TelephonyManager;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for telecom/telephony RPCs. */
public class TelephonySnippet implements Snippet {

    private final TelephonyManager telephonyManager;

    public TelephonySnippet() {
        Context context = InstrumentationRegistry.getContext();
        this.telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
    }

    @Rpc(description = "Gets the line 1 phone number.")
    public String getLine1Number() {
        return telephonyManager.getLine1Number();
    }

    @Rpc(description = "Returns the unique subscriber ID, for example, the IMSI for a GSM phone.")
    public int getSubscriberId() {
        return telephonyManager.getSubscriberId();
    }

    @Rpc(description = "Returns all observed cell information from all radios on the device" +
            " including the primary and neighboring cells.")
    public int getAllCellInfo() {
        return telephonyManager.getAllCellInfo();
    }

    @Rpc(description = "Gets the call state for the default subscription.")
    public int getTelephonyCallState() {
        return telephonyManager.getCallState();
    }

    @Override
    public void shutdown() {}

}
