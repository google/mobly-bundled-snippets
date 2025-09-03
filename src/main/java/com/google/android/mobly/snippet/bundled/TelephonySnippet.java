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
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcDefault;

/** Snippet class for telephony RPCs. */
public class TelephonySnippet implements Snippet {

    private static final String LOG_TAG = "TelephonySnippet";

    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;

    public TelephonySnippet() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mSubscriptionManager =
                (SubscriptionManager)
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    }

    @Rpc(description = "Gets the phone number of a given SIM slot or the default subscription.")
    public String getLine1Number(@RpcDefault("0") Integer simSlot) {
        String phoneNumber = "";
        try {
            // Get the phone number for the given sim slot.
            SubscriptionInfo activeSubInfoForSlot = mSubscriptionManager
                .getActiveSubscriptionInfoForSimSlotIndex(simSlot);
            phoneNumber = getPhoneNumber(activeSubInfoForSlot);
            if (isNullOrEmpty(phoneNumber)) {
                // Fall back to the default subscription if the phone number is unavailable.
                SubscriptionInfo defaultSubInfo = getDefaultSubscriptionInfo();
                phoneNumber = getPhoneNumber(defaultSubInfo);
            }
        } catch (IllegalStateException e) {
            Log.w(LOG_TAG, "Inappropriate state for getting the device phone number.", e);
        } catch (SecurityException e) {
            Log.w(LOG_TAG, "Lacking permission for getting the device phone number.", e);
        } catch (UnsupportedOperationException e) {
            Log.w(LOG_TAG, "Unsupported operation for getting the device phone number.", e);
        }
        if (!isNullOrEmpty(phoneNumber)) {
            Log.d(LOG_TAG, "phoneNumber: " + phoneNumber + " for simSlot: " + simSlot);
        } else {
            Log.w(LOG_TAG, "Failed to get the device phone number for simSlot: " + simSlot);
        }
        return phoneNumber;
    }

    private String getPhoneNumber(SubscriptionInfo subInfo) {
        if (subInfo == null) {
            return "";
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { // 33
            return subInfo.getNumber();
        } else {
            return mSubscriptionManager.getPhoneNumber(subInfo.getSubscriptionId());
        }
    }

    private SubscriptionInfo getDefaultSubscriptionInfo() {
        int defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return mSubscriptionManager.getActiveSubscriptionInfo(defaultSubId);
        } else {
            return null;
        }
    }

    private boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
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
