package com.google.android.mobly.snippet.bundled;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsMessage;

import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;

public class IncomingSmsBroadcastReceiver extends BroadcastReceiver {

    private final String mCallbackId;
    private Context mContext;
    private final EventCache mEventCache;

    public IncomingSmsBroadcastReceiver(Context context, String callbackId) {
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