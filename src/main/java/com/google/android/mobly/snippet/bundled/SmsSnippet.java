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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony.Sms.Intents;
import android.support.test.InstrumentationRegistry;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;

import java.util.ArrayList;

/** Snippet class for SMS RPCs. */
public class SmsSnippet implements Snippet {

    private static final int MAX_CHAR_COUNT_PER_SMS  = 160;
    private static final String SMS_SENT_ACTION = ".SMS_SENT";

    private final Context mContext;
    private final SmsManager mSmsManager;

    public SmsSnippet() {
        this.mContext = InstrumentationRegistry.getContext();
        this.mSmsManager = SmsManager.getDefault();
    }

    @AsyncRpc(description = "Async send SMS to a specified phone number.")
    public void asyncSendSms(String callbackId, String phoneNumber, String message)
            throws InterruptedException {
        OutboundSmsReceiver receiver = new OutboundSmsReceiver(mContext, callbackId);

        if (message.length() > MAX_CHAR_COUNT_PER_SMS) {
            ArrayList<String> parts = mSmsManager.divideMessage(message);
            ArrayList<PendingIntent> sIntents = new ArrayList<>();
            for (String part : parts) {
                sIntents.add(PendingIntent.getBroadcast(
                        mContext, 0, new Intent(SMS_SENT_ACTION), 0));
            }
            receiver.setExpectedMessageCount(parts.size());
            mContext.registerReceiver(receiver, new IntentFilter(SMS_SENT_ACTION));
            mSmsManager.sendMultipartTextMessage(phoneNumber, null, parts, sIntents, null);
        } else {
            PendingIntent sentIntent = PendingIntent.getBroadcast(
                    mContext, 0, new Intent(SMS_SENT_ACTION), 0);
            receiver.setExpectedMessageCount(1);
            mContext.registerReceiver(receiver, new IntentFilter(SMS_SENT_ACTION));
            mSmsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null);
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @AsyncRpc(description = "Async wait for incoming SMS message.")
    public void asyncWaitForSms(String callbackId) {
        SmsReceiver receiver = new SmsReceiver(mContext, callbackId);
        mContext.registerReceiver(receiver, new IntentFilter(Intents.SMS_RECEIVED_ACTION));
    }

    @Override
    public void shutdown() {}

    private class OutboundSmsReceiver extends BroadcastReceiver {
        private final String mCallbackId;
        private Context mContext;
        private final EventCache mEventCache;
        private int mExpectedMessageCount;

        public OutboundSmsReceiver(Context context, String callbackId) {
            this.mCallbackId = callbackId;
            this.mContext = context;
            this.mEventCache = EventCache.getInstance();
            mExpectedMessageCount = 0;
        }

        public void setExpectedMessageCount(int count) { mExpectedMessageCount = count; }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (SMS_SENT_ACTION.equals(action)) {
                SnippetEvent event = new SnippetEvent(mCallbackId, "SentSms");
                switch(getResultCode()) {
                    case Activity.RESULT_OK:
                        if (mExpectedMessageCount == 1) {
                            event.getData().putBoolean("sent", true);
                            mEventCache.postEvent(event);
                            mContext.unregisterReceiver(this);
                        }

                        if (mExpectedMessageCount > 0 ) {
                            mExpectedMessageCount--;
                        }
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        event.getData().putBoolean("sent", false);
                        event.getData().putInt("error_code", getResultCode());
                        mEventCache.postEvent(event);
                        mContext.unregisterReceiver(this);
                        break;
                }
            }
        }
    }

    private class SmsReceiver extends BroadcastReceiver {

        private final String mCallbackId;
        private Context mContext;
        private final EventCache mEventCache;

        public SmsReceiver(Context context, String callbackId) {
            this.mCallbackId = callbackId;
            this.mContext = context;
            this.mEventCache = EventCache.getInstance();
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public void onReceive(Context receivedContext, Intent intent) {
            if (Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                SnippetEvent event = new SnippetEvent(mCallbackId, "ReceivedSms");
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    SmsMessage[] msgs = Intents.getMessagesFromIntent(intent);
                    StringBuilder smsMsg = new StringBuilder();

                    SmsMessage sms = msgs[0];
                    String sender = sms.getOriginatingAddress();
                    event.getData().putString("sender", sender);

                    for (SmsMessage msg : msgs) {
                        smsMsg.append(msg.getMessageBody());
                    }
                    event.getData().putString("message", smsMsg.toString());
                    mEventCache.postEvent(event);
                    mContext.unregisterReceiver(this);
                }
            }
        }
    }
}
