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
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcDefault;

/** Snippet class for telephony RPCs. */
public class TelephonySnippet implements Snippet {

    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;

    public TelephonySnippet() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mSubscriptionManager =
                (SubscriptionManager)
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    }

    @Rpc(
            description =
                    "Gets the line 1 phone number, or optionally get phone number for the "
                            + "simSlot (slot# start from 0, only valid for API level > 32)")
    public String getLine1Number(@RpcDefault("0") Integer simSlot) {
        String thisNumber = "";

        if (Build.VERSION.SDK_INT < 33) {
            thisNumber = mTelephonyManager.getLine1Number();
        } else {
            SubscriptionInfo mSubscriptionInfo =
                    mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                            simSlot.intValue());
            if (mSubscriptionInfo != null) {
                thisNumber =
                        mSubscriptionManager.getPhoneNumber(mSubscriptionInfo.getSubscriptionId());
            }
        }

        return thisNumber;
    }

    @Rpc(description = "Returns the unique subscriber ID, for example, the IMSI for a GSM phone.")
    public String getSubscriberId() {
        return mTelephonyManager.getSubscriberId();
    }

    @Rpc(
            description =
                    "Gets the call state for the default subscription or optionally get the call"
                            + " state for the simSlot (slot# start from 0, only valid for API"
                            + " level > 30). Call state values are 0: IDLE, 1: RINGING, 2: OFFHOOK")
    public int getTelephonyCallState(@RpcDefault("0") Integer simSlot) {
        int thisState = -1;

        if (Build.VERSION.SDK_INT < 31) {
            return mTelephonyManager.getCallState();
        } else {
            SubscriptionInfo mSubscriptionInfo =
                    mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                            simSlot.intValue());
            if (mSubscriptionInfo != null) {
                thisState =
                        mTelephonyManager
                                .createForSubscriptionId(mSubscriptionInfo.getSubscriptionId())
                                .getCallStateForSubscription();
            }
        }

        return thisState;
    }

    @Rpc(
            description =
                    "Returns a constant indicating the radio technology (network type) currently"
                            + "in use on the device for data transmission.")
    public int getDataNetworkType() {
        if (Build.VERSION.SDK_INT < 30) {
            return mTelephonyManager.getNetworkType();
        } else {
            return mTelephonyManager.getDataNetworkType();
        }
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
