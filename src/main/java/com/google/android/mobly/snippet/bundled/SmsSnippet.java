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
import android.content.IntentFilter;
import android.provider.Telephony.Sms.Intents;
import android.support.test.InstrumentationRegistry;
import android.telephony.SmsManager;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for SMS RPCs. */
public class SmsSnippet implements Snippet {

    private static final int MAX_TEXT_MESSAGE_SIZE = 160;

    private final Context mContext;
    private final SmsManager mSmsManager;

    public SmsSnippet() {
        this.mContext = InstrumentationRegistry.getContext();
        this.mSmsManager = SmsManager.getDefault();
    }

    @Rpc(description = "Sends SMS message to a specified phone number.")
    public void sendSms(String phoneNumber, String message) throws InterruptedException {
        if (message.length() > MAX_TEXT_MESSAGE_SIZE) {
            mSmsManager.sendMultipartTextMessage(
                    phoneNumber, null, mSmsManager.divideMessage(message), null, null);
        } else {
            mSmsManager.sendTextMessage(phoneNumber, null, message, null, null);
        }
    }

    @AsyncRpc(description = "Wait for incoming SMS message.")
    public void asyncWaitForSms(String callbackId) {
        IncomingSmsBroadcastReceiver receiver = new IncomingSmsBroadcastReceiver(mContext, callbackId);
        mContext.registerReceiver(receiver, new IntentFilter(Intents.SMS_RECEIVED_ACTION));
    }

    @Override
    public void shutdown() {}
}