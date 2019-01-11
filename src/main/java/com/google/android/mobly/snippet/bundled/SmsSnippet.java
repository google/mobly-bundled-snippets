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
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.util.ArrayList;
import org.json.JSONObject;

/** Snippet class for SMS RPCs. */
public class SmsSnippet implements Snippet {

    private static class SmsSnippetException extends Exception {
        private static final long serialVersionUID = 1L;

        SmsSnippetException(String msg) {
            super(msg);
        }
    }

    private static final int MAX_CHAR_COUNT_PER_SMS = 160;
    private static final String SMS_SENT_ACTION = ".SMS_SENT";
    private static final int DEFAULT_TIMEOUT_MILLISECOND = 60 * 1000;
    private static final String SMS_RECEIVED_EVENT_NAME = "ReceivedSms";
    private static final String SMS_SENT_EVENT_NAME = "SentSms";
    private static final String SMS_CALLBACK_ID_PREFIX = "sendSms-";

    private static int mCallbackCounter = 0;

    private final Context mContext;
    private final SmsManager mSmsManager;

    public SmsSnippet() {
        this.mContext = InstrumentationRegistry.getInstrumentation().getContext();
        this.mSmsManager = SmsManager.getDefault();
    }

    /**
     * Send SMS and return after waiting for send confirmation (with a timeout of 60 seconds).
     *
     * @param phoneNumber A String representing phone number with country code.
     * @param message A String representing the message to send.
     * @throws SmsSnippetException on SMS send error.
     */
    @Rpc(description = "Send SMS to a specified phone number.")
    public void sendSms(String phoneNumber, String message) throws Throwable {
        String callbackId = SMS_CALLBACK_ID_PREFIX + (++mCallbackCounter);
        OutboundSmsReceiver receiver = new OutboundSmsReceiver(mContext, callbackId);

        if (message.length() > MAX_CHAR_COUNT_PER_SMS) {
            ArrayList<String> parts = mSmsManager.divideMessage(message);
            ArrayList<PendingIntent> sIntents = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                sIntents.add(
                        PendingIntent.getBroadcast(mContext, 0, new Intent(SMS_SENT_ACTION), 0));
            }
            receiver.setExpectedMessageCount(parts.size());
            mContext.registerReceiver(receiver, new IntentFilter(SMS_SENT_ACTION));
            mSmsManager.sendMultipartTextMessage(phoneNumber, null, parts, sIntents, null);
        } else {
            PendingIntent sentIntent =
                    PendingIntent.getBroadcast(mContext, 0, new Intent(SMS_SENT_ACTION), 0);
            receiver.setExpectedMessageCount(1);
            mContext.registerReceiver(receiver, new IntentFilter(SMS_SENT_ACTION));
            mSmsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null);
        }

        SnippetEvent result =
                Utils.waitForSnippetEvent(
                        callbackId, SMS_SENT_EVENT_NAME, DEFAULT_TIMEOUT_MILLISECOND);

        if (result.getData().containsKey("error")) {
            throw new SmsSnippetException(
                    "Failed to send SMS, error code: " + result.getData().getInt("error"));
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @AsyncRpc(description = "Async wait for incoming SMS message.")
    public void asyncWaitForSms(String callbackId) {
        SmsReceiver receiver = new SmsReceiver(mContext, callbackId);
        mContext.registerReceiver(receiver, new IntentFilter(Intents.SMS_RECEIVED_ACTION));
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Rpc(description = "Wait for incoming SMS message.")
    public JSONObject waitForSms(int timeoutMillis) throws Throwable {
        String callbackId = SMS_CALLBACK_ID_PREFIX + (++mCallbackCounter);
        SmsReceiver receiver = new SmsReceiver(mContext, callbackId);
        mContext.registerReceiver(receiver, new IntentFilter(Intents.SMS_RECEIVED_ACTION));
        return Utils.waitForSnippetEvent(callbackId, SMS_RECEIVED_EVENT_NAME, timeoutMillis)
                .toJson();
    }

    @Override
    public void shutdown() {}

    private static class OutboundSmsReceiver extends BroadcastReceiver {
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

        public void setExpectedMessageCount(int count) {
            mExpectedMessageCount = count;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (SMS_SENT_ACTION.equals(action)) {
                SnippetEvent event = new SnippetEvent(mCallbackId, SMS_SENT_EVENT_NAME);
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        if (mExpectedMessageCount == 1) {
                            event.getData().putBoolean("sent", true);
                            mEventCache.postEvent(event);
                            mContext.unregisterReceiver(this);
                        }

                        if (mExpectedMessageCount > 0) {
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
                    default:
                        event.getData().putBoolean("sent", false);
                        event.getData().putInt("error_code", -1 /* Unknown */);
                        mEventCache.postEvent(event);
                        mContext.unregisterReceiver(this);
                        break;
                }
            }
        }
    }

    private static class SmsReceiver extends BroadcastReceiver {
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
                SnippetEvent event = new SnippetEvent(mCallbackId, SMS_RECEIVED_EVENT_NAME);
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    SmsMessage[] msgs = Intents.getMessagesFromIntent(intent);
                    StringBuilder smsMsg = new StringBuilder();

                    SmsMessage sms = msgs[0];
                    String sender = sms.getOriginatingAddress();
                    event.getData().putString("OriginatingAddress", sender);

                    for (SmsMessage msg : msgs) {
                        smsMsg.append(msg.getMessageBody());
                    }
                    event.getData().putString("MessageBody", smsMsg.toString());
                    mEventCache.postEvent(event);
                    mContext.unregisterReceiver(this);
                }
            }
        }
    }
}
