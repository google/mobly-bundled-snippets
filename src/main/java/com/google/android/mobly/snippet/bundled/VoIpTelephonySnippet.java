package com.google.android.mobly.snippet.bundled;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcDefault;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Snippet class for simulating and controlling self-managed VoIP calls from Mobly tests.
 *
 * <p>High-level flow for anyone new to this file:
 * <ul>
 *   <li>This snippet registers a self-managed {@link PhoneAccount} with Android's
 *       {@link TelecomManager}, so the OS treats our simulated calls like real phone/VoIP
 *       calls (ringing UI, audio focus, Bluetooth routing all work the same way).</li>
 *   <li>{@link VoipConnectionService} is the entry point Android calls into when a call is
 *       created; it hands back a {@link VoipConnection}, which is our per-call state machine
 *       (ringing -> active -> disconnected).</li>
 *   <li>Test code drives calls indirectly through the {@code @Rpc} methods below (e.g.
 *       {@link #voipStartIncomingCall}, {@link #voipAcceptIncomingCall}), which is the public
 *       surface exposed to Python Mobly test scripts.</li>
 *   <li>Audio routing (Classic Bluetooth HFP/SCO vs. LE Audio vs. local speaker/earpiece) is
 *       handled separately via {@link AudioManager}, because Telecom only manages call
 *       state/UI, not the actual audio path.</li>
 * </ul>
 */
public class VoIpTelephonySnippet implements Snippet {
    private static final String TAG = "VoIpTelephonySnippet";

    /** Android context used for all system-service lookups (AudioManager, TelecomManager, etc.). */
    private final Context mContext;
    /** System service used to register our PhoneAccount and to place/receive calls. */
    private final TelecomManager mTelecomManager;
    /** Identifies our self-managed VoIP PhoneAccount to Telecom; passed on every call operation. */
    private final PhoneAccountHandle mVoipAccountHandle;

    // ---- Shared call state -------------------------------------------------------------
    // These are static+volatile because VoipConnection/VoipConnectionService (Telecom
    // callbacks, which may run on a different thread than the RPC caller) and the RPC methods
    // on this instance all need to read/write the same "current call" state.
    /** The connection object for the call currently in progress, or null if no call is active. */
    private static volatile VoipConnection sCurrentConnection;
    /** True from the moment an incoming call is signaled until it is answered/rejected/aborted. */
    private static volatile boolean sIncomingCallActive = false;
    /** Who last changed call state: "SOURCE_RPC" (test-driven) or "SINK_OR_TELECOM" (system/UI-driven). */
    private static volatile String sLastCallControlSource = "NONE";
    /** The last call-control action taken, e.g. "ACCEPT", "REJECT", "END", "START_INCOMING". */
    private static volatile String sLastCallControlAction = "NONE";
    /** The call address (URI) involved in the last call-control action, for test assertions. */
    private static volatile String sLastCallAddress = "";
    /** Wall-clock time (ms since epoch) of the last call-control action, for timing assertions. */
    private static volatile long sLastCallControlTimeMs = 0;

    /** Currently playing incoming-call ringtone, if any; null when no ringtone is active. */
    private static Ringtone sIncomingCallRingtone;
    /** Looping "far-end" audio player used to simulate the other party's voice during a call. */
    private static MediaPlayer sCallAudioPlayer;

    /** WAV file played as the simulated far-end voice once a call becomes active. */
    private static final String voiceInputFile = "/sdcard/Music/testInput.wav";
    /** Max seconds to wait for AudioManager state changes (e.g. mode reset) to take effect. */
    private static final int TIMEOUT_SEC = 5;

    /*
     * Official Android references for VoIP/Bluetooth behavior:
     * TelecomManager: https://developer.android.com/reference/android/telecom/TelecomManager
     * ConnectionService: https://developer.android.com/reference/android/telecom/ConnectionService
     * Connection: https://developer.android.com/reference/android/telecom/Connection
     * PhoneAccount: https://developer.android.com/reference/android/telecom/PhoneAccount
     * AudioManager communication routing: https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)
     * AudioDeviceCallback: https://developer.android.com/reference/android/media/AudioDeviceCallback
     * AudioDeviceInfo: https://developer.android.com/reference/android/media/AudioDeviceInfo
     * AudioRecord: https://developer.android.com/reference/android/media/AudioRecord
     * MediaRecorder.AudioSource.VOICE_COMMUNICATION: https://developer.android.com/reference/android/media/MediaRecorder.AudioSource#VOICE_COMMUNICATION
     * AudioAttributes.USAGE_VOICE_COMMUNICATION: https://developer.android.com/reference/android/media/AudioAttributes#USAGE_VOICE_COMMUNICATION
     * Bluetooth ACL broadcasts: https://developer.android.com/reference/android/bluetooth/BluetoothDevice#ACTION_ACL_CONNECTED
     */

    /** Listens for Classic Bluetooth (HFP) connect/disconnect to re-route active call audio. */
    private BroadcastReceiver mClassicBluetoothReceiver;
    /** Listens for LE Audio device add/remove to re-route active call audio. */
    private AudioDeviceCallback mLeAudioCallback;

    /**
     * Registers this app as a self-managed VoIP {@link PhoneAccount} with Telecom and starts
     * listening for Bluetooth audio device changes, so subsequent RPC calls (start/accept/end
     * call) have a valid account to operate against.
     */
    public VoIpTelephonySnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        /* Access to telecom services (managing calls, placing/ending calls, connection services like VoIP) */
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        /* Create a PhoneAccountHandle that uniquely identifies our self-managed VoIP account */
        mVoipAccountHandle = new PhoneAccountHandle(
                new ComponentName(mContext, VoIpTelephonySnippet.VoipConnectionService.class), "Mobly-Voip");

        if (mTelecomManager != null) {
            try {
                /* Build a PhoneAccount using the handle */
                PhoneAccount account = PhoneAccount.builder(mVoipAccountHandle, "Mobly VoIP")
                        /* Mark this account as self-managed (we handle all call UI and routing logic) */
                        .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED).build();
                /* Register the PhoneAccount with the system TelecomManager */
                mTelecomManager.registerPhoneAccount(account);
                Log.d(TAG, "Registered self-managed VoIP PhoneAccount.");
            } catch (Throwable t) {
                Log.w(TAG, "registerPhoneAccount failed: " + t);
            }
        } else {
            Log.w(TAG, "TelecomManager is null; cannot register PhoneAccount.");
        }
        registerClassicBluetoothReceiver();
        registerLeAudioCallback();
    }

    /**
     * Checks whether it's safe to start the incoming-call ringtone: we need a valid context,
     * no ringtone already playing, and the audio mode must not already indicate an active call
     * (otherwise we'd ring over live call audio).
     *
     * @return true if {@link #playIncomingRingtone} may proceed.
     */
    private static boolean isSafeToStartIncomingCallRingtone(Context context) {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot start incoming call ringtone");
            return false;
        }

        if (sIncomingCallRingtone != null &&
                sIncomingCallRingtone.isPlaying()) {
            Log.d(TAG, "Incoming call ringtone already playing");
            return false;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.w(TAG, "AudioManager is null");
            return false;
        }

        int currentAudioMode = audioManager.getMode();
        if (currentAudioMode == AudioManager.MODE_IN_COMMUNICATION ||
                currentAudioMode == AudioManager.MODE_IN_CALL) {
            Log.w(TAG, "Audio mode indicates active call, skip ringtone");
            return false;
        }
        return true;
    }

    /**
     * Plays the device's default ringtone to simulate an incoming call notification. Called
     * from {@link VoipConnectionService#onCreateIncomingConnection} when a new incoming call
     * is created. No-op if it's unsafe to ring (see {@link #isSafeToStartIncomingCallRingtone}).
     */
    public static void playIncomingRingtone(Context context) {
        if (!isSafeToStartIncomingCallRingtone(context)) {
            Log.d(TAG, "Incoming call ringtone start skipped");
            return;
        }

        stopIncomingRingtone();

        try {
            Uri ringtoneUri = Settings.System.DEFAULT_RINGTONE_URI;
            Ringtone ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
            if (ringtone == null) {
                Log.w(TAG, "Default ringtone not available");
                return;
            }

            try {
                ringtone.setStreamType(AudioManager.STREAM_RING);
            } catch (Throwable ignore) {}

            sIncomingCallRingtone = ringtone;
            sIncomingCallRingtone.play();
            Log.d(TAG, "Incoming call ringtone started");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start incoming call ringtone", t);
        }
    }

    /**
     * Stops the incoming-call ringtone (if playing) and resets audio focus/mode back to
     * normal. Safe to call even when no ringtone is active. Called on answer, reject, abort,
     * and snippet shutdown to guarantee the ringtone never keeps playing past the call.
     */
    public static void stopIncomingRingtone() {
        final Ringtone ringtone = sIncomingCallRingtone;
        sIncomingCallRingtone = null;

        if (ringtone == null) {
            return;
        }

        try {
            if (ringtone.isPlaying()) {
                ringtone.stop();
                Log.d(TAG, "Incoming call ringtone stopped");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error stopping incoming call ringtone", t);
        }

        try {
            Context context = InstrumentationRegistry.getInstrumentation().getContext();
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.abandonAudioFocus(null);
                audioManager.setMode(AudioManager.MODE_NORMAL);
                Log.d(TAG, "Audio reset after incoming call ringtone stop");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to reset audio after ringtone stop", t);
        }
    }

    /**
     * Restores {@link AudioManager} to its idle state after a call ends: abandons audio focus,
     * sets mode back to NORMAL, stops Bluetooth SCO, clears the communication device (API 31+),
     * and unmutes the mic. Each step is wrapped individually so one failing call (e.g. on an
     * OEM build without a given API) doesn't block the rest of the cleanup.
     */
    private static void resetAudioMode() {
        try {
            AudioManager audioManager = (AudioManager) InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                try { audioManager.abandonAudioFocus(null); } catch (Throwable ignore) {}
                try { audioManager.setMode(AudioManager.MODE_NORMAL); } catch (Throwable ignore) {}
                try { audioManager.stopBluetoothSco(); } catch (Throwable ignore) {}
                try { audioManager.setBluetoothScoOn(false); } catch (Throwable ignore) {}
                /* CLEAR Communication Device (Android 12+) */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    /* This is the specific line that resets the context */
                    try {
                        audioManager.clearCommunicationDevice();
                        Log.d(TAG, "Communication device cleared (Resetting context)");
                    } catch (Throwable ignore) {}
                }
                try { audioManager.setMicrophoneMute(false); } catch (Throwable ignore) {}
                Utils.waitUntil( () -> audioManager.getMode() == AudioManager.MODE_NORMAL, TIMEOUT_SEC );
                Log.d(TAG, "Audio mode reset to MODE_NORMAL and SCO stopped");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to reset audio mode", t);
        }
    }

    /** Tracks the audio-focus request made in {@link #startCallAudio} so it can be released later. */
    private static AudioFocusRequest sCallFocusRequest;

    /**
     * Starts simulating an active call's audio: puts AudioManager into call mode, requests
     * voice-call audio focus, routes to Bluetooth if available, and loops the far-end WAV
     * ({@code fileName}) so a paired device (e.g. a af headset under test) has something to
     * play. Called once a call transitions to active (answered or outgoing-connected).
     */
    private static void startCallAudio(String fileName) {
        stopCallAudio();
        try {
            Context ctx = InstrumentationRegistry.getInstrumentation().getContext();
            AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                Log.w(TAG, "AudioManager is null, cannot start call audio");
                return;
            }

            /* 1. Enter call mode */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

            /* 2. Request proper voice-call focus (modern) */
            AudioAttributes callAttrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            sCallFocusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(callAttrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focus -> Log.d(TAG, "Call focus change=" + focus))
                    .build();

            int result = audioManager.requestAudioFocus(sCallFocusRequest);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Call audio focus not granted");
            }

            /* Route to Bluetooth (SCO or LE Audio) */
            routeAudioToBluetoothForVoiceCall(audioManager);

            /* Mute local mic for pure far-end simulation */
            audioManager.setMicrophoneMute(false);

            /* Play far-end WAV */
            sCallAudioPlayer = new MediaPlayer();
            sCallAudioPlayer.setAudioAttributes(callAttrs);
            sCallAudioPlayer.setLooping(true);
            sCallAudioPlayer.setDataSource(fileName);
            sCallAudioPlayer.prepare();
            sCallAudioPlayer.start();
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                0
            );
            Log.d(TAG, "VoIP far-end audio started: " + fileName);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start call audio", e);
            stopCallAudio();
        }
    }

    /**
     * Stops and releases the far-end audio player started by {@link #startCallAudio}, then
     * restores AudioManager to its pre-call state (SCO off, mic unmuted, mode NORMAL, focus
     * abandoned). Called whenever a call ends, regardless of who ended it.
     */
    private static void stopCallAudio() {
        try {
            /* Stop and release call audio player */
            if (sCallAudioPlayer != null) {
                try {
                    if (sCallAudioPlayer.isPlaying()) {
                        sCallAudioPlayer.stop();
                    }
                } catch (Throwable ignore) {}

                try {
                    sCallAudioPlayer.release();
                } catch (Throwable ignore) {}

                sCallAudioPlayer = null;
                Log.d(TAG, "Call audio player released");
            }

            /* Restore AudioManager state */
            Context ctx = InstrumentationRegistry.getInstrumentation().getContext();
            AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

            if (audioManager != null) {

                /* Stop SCO for LE Audio */
                try {
                    audioManager.setBluetoothScoOn(false);
                } catch (Throwable ignore) {}

                try {
                    audioManager.stopBluetoothSco();
                } catch (Throwable ignore) {}

                /* Restore mic */
                try {
                    audioManager.setMicrophoneMute(false);
                } catch (Throwable ignore) {}

                /* Restore audio mode */
                try {
                    audioManager.setMode(AudioManager.MODE_NORMAL);
                } catch (Throwable ignore) {}

                /* Android 12+ - clear call routing (LE Audio fix) */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice();
                }

                /* Abandon modern audio focus properly */
                if (sCallFocusRequest != null) {
                    try {
                        audioManager.abandonAudioFocusRequest(sCallFocusRequest);
                    } catch (Throwable ignore) {}
                    sCallFocusRequest = null;
                }

                Log.d(TAG, "Audio state restored after call simulation");
            }
        } catch (Throwable t) {
            Log.w(TAG, "stopCallAudio cleanup failed", t);
        }
    }

    // ===== VoIP RPCs =====
    // Everything below marked @Rpc is callable by name from Python Mobly test scripts via
    // the snippet client (e.g. ad.mbs.voipStartIncomingCall("+15551234567")).

    /**
     * Records who last changed call state and what they did, so {@link #voipGetCallStatus} can
     * report it. {@code source} is "SOURCE_RPC" for test-driven actions or "SINK_OR_TELECOM"
     * for actions coming from the system/Telecom UI (e.g. user tapping answer on-screen).
     */
    private static void updateCallControlStatus(String source, String action, Uri address) {
        sLastCallControlSource = source;
        sLastCallControlAction = action;
        sLastCallAddress = address != null ? address.toString() : "";
        sLastCallControlTimeMs = System.currentTimeMillis();
        Log.d(TAG, "Call control: source=" + source + ", action=" + action + ", address=" + sLastCallAddress);
    }

    /**
     * Simulates an incoming VoIP call from {@code caller}, causing the device to ring exactly
     * as it would for a real call. Internally calls {@link TelecomManager#addNewIncomingCall},
     * which triggers Android to invoke {@link VoipConnectionService#onCreateIncomingConnection}.
     *
     * @param caller phone-number-like string used to build a {@code tel:} URI for the call.
     * @return true if the incoming call was successfully triggered; false if one is already
     *     active, the TelecomManager is unavailable, or Telecom rejected the request.
     */
    @Rpc(description = "Simulate incoming ring VoIP call")
    public boolean voipStartIncomingCall(String caller) {
        if (sIncomingCallActive) {
            Log.d(TAG, "Incoming call already active, ignoring duplicate: " + caller);
            return false;
        }
        if (mTelecomManager == null) {
            Log.w(TAG, "TelecomManager is null; cannot add incoming call");
            return false;
        }

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mVoipAccountHandle);
        // Use tel: scheme so the system UI treats this as a phone-like incoming call
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts("tel", caller.trim(), null));

        try {
            mTelecomManager.addNewIncomingCall(mVoipAccountHandle, extras);
            sIncomingCallActive = true;
            updateCallControlStatus("SOURCE_RPC", "START_INCOMING", Uri.fromParts("tel", caller.trim(), null));
            Log.d(TAG, "Incoming VoIP call triggered: " + caller);
            return true;
        } catch (Throwable t) {
            sIncomingCallActive = false;
            Log.e(TAG, "addNewIncomingCall failed", t);
            return false;
        }
    }

    /**
     * Answers the currently ringing VoIP call (equivalent to the user tapping "answer"):
     * marks the connection active, stops the ringtone, and starts simulated far-end call
     * audio.
     *
     * @return true if there was a connection to answer and it succeeded; false otherwise.
     */
    @Rpc(description = "Answer current VoIP call")
    public boolean voipAcceptIncomingCall() {
        if (sCurrentConnection != null) {
            try {
                sCurrentConnection.setActive();
                stopIncomingRingtone();
                startCallAudio(voiceInputFile);
                sIncomingCallActive = false;
                updateCallControlStatus("SOURCE_RPC", "ACCEPT", sCurrentConnection.getAddress());
                Log.d(TAG, "VoIP call answered: " + sCurrentConnection.getAddress());
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "voipAcceptIncomingCall failed", t);
                return false;
            }
        } else {
            Log.w(TAG, "No current connection to answer");
            return false;
        }
    }

    /**
     * Rejects the currently ringing VoIP call (equivalent to the user declining it):
     * disconnects the connection with cause {@code REJECTED} and stops the ringtone.
     *
     * @return true if there was a connection to reject and it succeeded; false otherwise.
     */
    @Rpc(description = "Reject current VoIP call")
    public boolean voipRejectIncomingCall() {
        if (sCurrentConnection != null) {
            try {
                sCurrentConnection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                sCurrentConnection.destroy();
                stopIncomingRingtone();
                sIncomingCallActive = false;
                updateCallControlStatus("SOURCE_RPC", "REJECT", sCurrentConnection.getAddress());
                sCurrentConnection = null;
                Log.d(TAG, "VoIP call rejected");
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "voipRejectIncomingCall failed", t);
                return false;
            }
        } else {
            Log.w(TAG, "No current connection to reject");
            return false;
        }
    }

    /**
     * Ends the currently active VoIP call: stops call audio, disconnects the connection with
     * cause {@code LOCAL} (we hung up), and resets AudioManager back to its idle state so the
     * device is ready for the next test case.
     *
     * @return true if there was a connection to end and it succeeded; false otherwise.
     */
    @Rpc(description = "End current VoIP call")
    public boolean voipEndCall() {
        if (sCurrentConnection != null) {
            try {
                stopCallAudio();
                Uri address = sCurrentConnection.getAddress();
                sCurrentConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                sCurrentConnection.destroy();
                Log.d(TAG, "VoIP call ended: " + address);
                sCurrentConnection = null;
                sIncomingCallActive = false;
                updateCallControlStatus("SOURCE_RPC", "END", address);

                // Reset audio mode to normal
                resetAudioMode();
                Log.d(TAG, "VoIP call ended");
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "voipEndCall failed", t);
                return false;
            }
        } else {
            Log.w(TAG, "No current connection to end");
            return false;
        }
    }

    /**
     * Places a simulated outgoing VoIP call to {@code call_id}. Internally calls
     * {@link TelecomManager#placeCall}, which triggers Android to invoke
     * {@link VoipConnectionService#onCreateOutgoingConnection}; that callback immediately marks
     * the call active and starts call audio (no separate "answer" step for outgoing calls).
     *
     * @param call_id destination identifier, wrapped in a {@code sip:} URI.
     * @return true if the call was successfully placed; false if TelecomManager is unavailable
     *     or the call could not be placed.
     */
    @Rpc(description = "Start Outgoing VoIP call")
    public boolean voipStartOutgoingCall(String call_id) {
        if (mTelecomManager == null) {
            Log.w(TAG, "TelecomManager is null; cannot place outgoing call");
            return false;
        }
        Uri uri = Uri.fromParts("sip", call_id, null);
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mVoipAccountHandle);
        try {
            mTelecomManager.placeCall(uri, extras);
            updateCallControlStatus("SOURCE_RPC", "START_OUTGOING", uri);
            Log.d(TAG, "Outgoing VoIP call: " + call_id);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "placeCall failed", t);
            return false;
        }
    }

    /**
     * Mutes or unmutes the microphone at the {@link AudioManager} level (not tied to any
     * specific call). Useful for tests that need to verify mute behavior independent of the
     * call state machine.
     *
     * @param mute true to mute the mic, false to unmute it.
     * @return true if the mute state was applied; false if AudioManager is unavailable.
     */
    @Rpc(description = "Mute/unmute mic")
    public boolean voipSetMicMute(boolean mute) {
        try {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                try { audioManager.setMicrophoneMute(mute); } catch (Throwable ignore) {}
                Log.d(TAG, "Mic mute: " + mute);
                return true;
            } else {
                Log.w(TAG, "AudioManager is null; cannot set microphone mute");
                return false;
            }
        } catch (Throwable t) {
            Log.e(TAG, "setMicrophoneMute failed", t);
            return false;
        }
    }

    /**
     * Reports a full snapshot of VoIP call state for test assertions: whether a connection
     * exists, its Telecom state, the last call-control action taken and by whom (test RPC vs.
     * system/Telecom UI), and the current call audio route (see {@link #voipGetCallAudioState}).
     */
    @Rpc(description = "Get current VoIP call status and last source/sink call-control action.")
    public Map<String, Object> voipGetCallStatus() {
        Map<String, Object> out = new HashMap<>();
        VoipConnection connection = sCurrentConnection;
        out.put("hasConnection", connection != null);
        out.put("incomingCallActive", sIncomingCallActive);
        out.put("connectionState", connection != null ? connection.getState() : Connection.STATE_DISCONNECTED);
        out.put("connectionAddress", connection != null && connection.getAddress() != null
                ? connection.getAddress().toString()
                : "");
        out.put("lastCallControlSource", sLastCallControlSource);
        out.put("lastCallControlAction", sLastCallControlAction);
        out.put("lastCallAddress", sLastCallAddress);
        out.put("lastCallControlTimeMs", sLastCallControlTimeMs);
        out.put("callAudioState", voipGetCallAudioState());
        return out;
    }

    /**
     * Reports the audio route currently used for a call, resolved from {@link
     * AudioManager#getCommunicationDevice()} so both Classic Bluetooth (HFP/SCO) and LE Audio
     * (LE_HEADSET/LE_SPEAKER/LE_BROADCAST) devices are distinguished from local routes
     * (earpiece/speaker/wired). {@code getCommunicationDevice()} requires API 31+ and can throw
     * on devices/OEM builds that don't support it, so the lookup is wrapped separately and a
     * failure there degrades to an "unknown" route instead of failing the whole RPC.
     */
    @Rpc(description = "Get current call audio state (supports Classic HFP + LE Audio).")
    public Map<String, Object> voipGetCallAudioState() {
        Map<String, Object> out = new HashMap<>();
        try {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                out.put("error", "AudioManager null");
                return out;
            }

            boolean isMuted = audioManager.isMicrophoneMute();
            int mode = audioManager.getMode();

            RouteInfo routeInfo = RouteInfo.UNKNOWN;
            String deviceName = null;

            // getCommunicationDevice() is the modern (API 31+) source of truth for the active
            // call route; older/OEM AudioManager implementations may not support it, so treat
            // any failure as "no route info" rather than surfacing an error to the caller.
            AudioDeviceInfo commDevice = null;
            try {
                commDevice = audioManager.getCommunicationDevice();
            } catch (Throwable ignore) {
            }

            if (commDevice != null) {
                deviceName = String.valueOf(commDevice.getProductName());
                routeInfo = resolveRoute(commDevice.getType());
            }

            out.put("isMuted", isMuted);
            out.put("mode", mode);
            // Human-readable mirror of "mode" since the raw AudioManager.MODE_* int is opaque
            // in test logs/reports.
            out.put("modeName", audioModeToString(mode));
            out.put("route", routeInfo.route);
            out.put("routeType", routeInfo.routeType);
            if (deviceName != null) {
                out.put("deviceName", deviceName);
            }

            Log.d(TAG, "CallAudioState: " + out);
        } catch (Throwable t) {
            out.put("error", t.toString());
            Log.e(TAG, "voipGetCallAudioState failed", t);
        }
        return out;
    }

    /** Human-readable call route plus its broad category (BLUETOOTH vs LOCAL). */
    private static final class RouteInfo {
        static final RouteInfo UNKNOWN = new RouteInfo("UNKNOWN", "LOCAL");

        final String route;
        final String routeType;

        RouteInfo(String route, String routeType) {
            this.route = route;
            this.routeType = routeType;
        }
    }

    /**
     * Classifies an {@link AudioDeviceInfo} type into the route names Mobly test cases assert
     * on. Distinguishing LE Audio (LE_HEADSET/LE_SPEAKER/LE_BROADCAST) from Classic Bluetooth
     * (CLASSIC_HEADSET via SCO) matters because the two stacks exercise different af firmware
     * paths during a call.
     */
    private static RouteInfo resolveRoute(int deviceType) {
        switch (deviceType) {
            // === Classic Bluetooth (HFP SCO) ===
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return new RouteInfo("CLASSIC_HEADSET", "BLUETOOTH");

            // === Bluetooth A2DP: some OEMs route call audio here when SCO/LE isn't available ===
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return new RouteInfo("BLUETOOTH_A2DP", "BLUETOOTH");

            // === LE Audio ===
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return new RouteInfo("LE_HEADSET", "BLUETOOTH");
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return new RouteInfo("LE_SPEAKER", "BLUETOOTH");
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                return new RouteInfo("LE_BROADCAST", "BLUETOOTH");

            // === Hearing aids (also negotiate over LE Audio on newer devices) ===
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return new RouteInfo("HEARING_AID", "BLUETOOTH");

            // === Local routes ===
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return new RouteInfo("SPEAKER", "LOCAL");
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return new RouteInfo("EARPIECE", "LOCAL");
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return new RouteInfo("WIRED", "LOCAL");

            default:
                return new RouteInfo("OTHER(" + deviceType + ")", "LOCAL");
        }
    }

    /** Maps an {@code AudioManager.MODE_*} constant to its readable name for logging/reporting. */
    private static String audioModeToString(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL:
                return "NORMAL";
            case AudioManager.MODE_RINGTONE:
                return "RINGTONE";
            case AudioManager.MODE_IN_CALL:
                return "IN_CALL";
            case AudioManager.MODE_IN_COMMUNICATION:
                return "IN_COMMUNICATION";
            case AudioManager.MODE_CALL_SCREENING:
                return "CALL_SCREENING";
            default:
                return "UNKNOWN(" + mode + ")";
        }
    }

    // ===== ConnectionService & VoipConnection =====
    /**
     * System-registered entry point Android/Telecom calls into to create call connections for
     * our self-managed {@link PhoneAccount}. Test code never calls this class directly — it's
     * invoked by the OS in response to {@link TelecomManager#addNewIncomingCall} (incoming) or
     * {@link TelecomManager#placeCall} (outgoing).
     */
    public static class VoipConnectionService extends ConnectionService {
        /**
         * Called by Telecom when {@link #voipStartIncomingCall} triggers a new incoming call.
         * Rejects the request with {@code BUSY} if a call is already active (this snippet only
         * supports one call at a time); otherwise creates a ringing {@link VoipConnection} and
         * starts the incoming-call ringtone.
         */
        @Override
        public Connection onCreateIncomingConnection(PhoneAccountHandle handle, ConnectionRequest req) {
            Log.d(TAG, "onCreateIncomingConnection: " + req.getAddress());

            if (sCurrentConnection != null) {
                Log.w(TAG, "Rejecting duplicate incoming call: " + req.getAddress());
                Connection failed = new Connection() {};
                failed.setDisconnected(new DisconnectCause(DisconnectCause.BUSY));
                failed.destroy();
                return failed;
            }

            VoipConnection voipConnection = new VoipConnection(req.getAddress(), true);
            sCurrentConnection = voipConnection;

            // Explicitly mark as ringing and configure connection
            voipConnection.setAddress(req.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
            voipConnection.setRinging();
            voipConnection.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
            voipConnection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
            playIncomingRingtone(getApplicationContext());
            return voipConnection;
        }

        /**
         * Called by Telecom when {@link #voipStartOutgoingCall} places a new outgoing call.
         * Unlike incoming calls, outgoing calls are marked active immediately (no separate
         * "answer" step) and call audio starts right away.
         */
        @Override
        public Connection onCreateOutgoingConnection(PhoneAccountHandle handle, ConnectionRequest req) {
            Log.d(TAG, "onCreateOutgoingConnection: " + req.getAddress());
            VoipConnection voipConnection = new VoipConnection(req.getAddress(), false);
            sCurrentConnection = voipConnection;
            voipConnection.setActive();
            startCallAudio(voiceInputFile);
            updateCallControlStatus("TELECOM", "OUTGOING_ACTIVE", req.getAddress());
            return voipConnection;
        }
    }

    /**
     * Represents a single VoIP call's state machine to Telecom. Each of the {@code onXxx()}
     * callbacks below is invoked by the system in response to either a test RPC call (e.g.
     * {@link #voipAcceptIncomingCall} calls {@code sCurrentConnection.setActive()}, which
     * Telecom routes back into {@link #onAnswer()}) or a direct system/UI action (e.g. the
     * user answering from the system incoming-call screen).
     */
    public static class VoipConnection extends Connection {
        /** Call address (URI) this connection was created for; kept for logging/status reporting. */
        private final Uri mAddr;
        /** True if this connection was created for an incoming call, false for outgoing. */
        private final boolean mIncoming;

        /** Initializes the connection and immediately transitions it to ringing (incoming) or dialing (outgoing). */
        VoipConnection(Uri address, boolean incoming) {
            mAddr = address;
            mIncoming = incoming;
            setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
            setConnectionCapabilities(getConnectionCapabilities() | CAPABILITY_MUTE);
            setInitializing();
            if (mIncoming) {
                setRinging();
                Log.d(TAG, "Ringing set for incoming call: " + mAddr);
                sIncomingCallActive = true;
            } else {
                setDialing();
                Log.d(TAG, "Dialing set for outgoing call: " + mAddr);
            }
        }

        /** Called when the call is answered (via RPC or system UI); mirrors {@link #voipAcceptIncomingCall}. */
        @Override
        public void onAnswer() {
            try {
                setActive();
                Log.d(TAG, "onAnswer: " + mAddr);
                stopIncomingRingtone();
                sIncomingCallActive = false;
                startCallAudio(voiceInputFile);
                updateCallControlStatus("SINK_OR_TELECOM", "ACCEPT", mAddr);
            } catch (Throwable t) {
                Log.e(TAG, "onAnswer failed", t);
            }
        }

        /** Called when the call is rejected (via RPC or system UI); mirrors {@link #voipRejectIncomingCall}. */
        @Override
        public void onReject() {
            try {
                setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                destroy();
                stopIncomingRingtone();
                sIncomingCallActive = false;
                updateCallControlStatus("SINK_OR_TELECOM", "REJECT", mAddr);
                sCurrentConnection = null;
                Log.d(TAG, "onReject: " + mAddr);
            } catch (Throwable t) {
                Log.e(TAG, "onReject failed", t);
            }
        }

        /** Called when the call is ended locally (via RPC or system UI); mirrors {@link #voipEndCall}. */
        @Override
        public void onDisconnect() {
            try {
                setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                destroy();
                stopCallAudio();
                sIncomingCallActive = false;
                updateCallControlStatus("SINK_OR_TELECOM", "END", mAddr);
                sCurrentConnection = null;
                resetAudioMode();
                Log.d(TAG, "onDisconnect: " + mAddr);
            } catch (Throwable t) {
                Log.e(TAG, "onDisconnect failed", t);
            }
        }

        /** Called when Telecom cancels the call before it was answered (e.g. caller hung up while ringing). */
        @Override
        public void onAbort() {
            try {
                setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                destroy();
                Log.d(TAG, "onAbort: " + mAddr);
                stopIncomingRingtone();
                sIncomingCallActive = false;
                updateCallControlStatus("SINK_OR_TELECOM", "ABORT", mAddr);
            } catch (Throwable t) {
                Log.e(TAG, "onAbort failed", t);
            }
        }
    }

    /** Provides an application {@link Context} for static call sites (e.g. {@link VoipConnectionService}) that have no instance context of their own. */
    private static Context getApplicationContext() {
        return InstrumentationRegistry.getInstrumentation().getContext().getApplicationContext();
    }

    // ===== Microphone Recording RPCs =====
    private MicrophoneRecorder mMicrophoneRecorder;
    /*
    In Audacity, use File -> Import -> Raw Data... and set:
    Encoding: Signed 16-bit PCM
    Byte order: Little-endian
    Channels: 1 Channel (Mono)
    Start offset: 0 bytes
    Amount to import: 100% (or "till end")
    Sample rate: 16000 Hz
    */
    /**
     * Starts capturing microphone audio into a raw PCM file, using the {@code
     * VOICE_COMMUNICATION} audio source so the capture matches what the call's far-end would
     * actually hear (echo cancellation + noise suppression applied, same as during a real call).
     * Any previous recording is stopped first (see {@link #voipStopMicrophoneRecording}).
     *
     * @param sampleRateHz capture sample rate; defaults to 16000 Hz if omitted.
     * @param durationMs maximum recording length before it auto-stops; defaults to 60000 ms.
     * @param fileName output file name under the app's external files directory.
     * @return the on-device output file path, or "" if recording failed to start.
     */
    @Rpc(description = "Start recording VoIP microphone audio to a raw PCM 16-bit mono file.")
    public String voipStartMicrophoneRecording( @RpcDefault("16000") Integer sampleRateHz,
                                            @RpcDefault("60000") Integer durationMs,
                                            @RpcDefault("MicRecording.raw") String fileName) {
        // Always stop any in-progress recording before starting a new one to avoid
        // leaving a dangling AudioRecord session that holds the mic hardware.
        voipStopMicrophoneRecording();
        mMicrophoneRecorder = new MicrophoneRecorder(sampleRateHz, durationMs, fileName);
        return mMicrophoneRecorder.start();
    }

    /**
     * Stops any in-progress microphone recording started by {@link #voipStartMicrophoneRecording}
     * and releases the underlying {@link AudioRecord}. Safe to call even if nothing is recording.
     *
     * @return the recorded file's path, or "" if no recording was active.
     */
    @Rpc(description = "Stop the microphone recording and return the on-device file path.")
    public String voipStopMicrophoneRecording() {
        if (mMicrophoneRecorder != null) {
            String filePath = mMicrophoneRecorder.stop();
            mMicrophoneRecorder = null;
            Log.d(TAG, "Stopped microphone recording. Data saved to: " + filePath);
            return filePath != null ? filePath : "";
        }
        return "";
    }

    /**
     * Reports the status of the current (or most recently finished) microphone recording,
     * including running state, file path, byte count, and computed audio levels (peak/RMS —
     * see {@link MicrophoneRecorder#getStatus()}). If no recorder has ever been started, returns
     * a minimal "not running" result plus the current call audio state for context.
     */
    @Rpc(description = "Get current/last VoIP microphone recording status and audio levels.")
    public Map<String, Object> voipGetMicrophoneRecordingStatus() {
        if (mMicrophoneRecorder == null) {
            Map<String, Object> out = new HashMap<>();
            out.put("isRunning", false);
            out.put("error", "No active microphone recorder");
            out.put("callAudioState", voipGetCallAudioState());
            return out;
        }
        return mMicrophoneRecorder.getStatus();
    }

    // ===== MicrophoneRecorder Inner Class =====
    /**
     * Captures raw PCM 16-bit mono audio from the VoIP microphone path on a background thread.
     * Output is always written as raw PCM (no container/header) for direct FFT analysis.
     */
    private class MicrophoneRecorder {
        private final int SAMPLE_RATE;
        private final int DURATION_MS;
        private final String BASE_FILE_NAME;
        private static final int CHANNEL_COUNT = 1;
        private static final int BITS_PER_SAMPLE = 16;
        private AudioRecord mAudioRecord;
        private Thread mRecordThread;

        // volatile: mIsRunning and audio stats are written by the record thread and
        // read by the main thread (stop/getStatus), so visibility must be guaranteed.
        private volatile boolean mIsRunning = false;
        private String mOutputFilePath;           // only written in start() before thread launch
        private volatile long mBytesWritten = 0;
        private volatile long mSamplesRead = 0;
        private volatile double mSumSquares = 0;  // accumulates sample² for RMS calculation
        private volatile int mPeakAmplitude = 0;
        private volatile String mLastError = "";

        MicrophoneRecorder(int sampleRate, int durationMs, String fileName) {
            SAMPLE_RATE = sampleRate;
            DURATION_MS = durationMs;
            BASE_FILE_NAME = fileName;
        }

        /**
         * Configures AudioManager/AudioRecord and spawns the background thread that actually
         * reads mic samples (see {@link #recordAudio()}). Returns immediately after the thread
         * starts; recording continues until {@link #stop()} is called or {@code DURATION_MS}
         * elapses.
         *
         * @return the output file path on success, or "" if setup failed (see {@link #mLastError}).
         */
        String start() {
            if (SAMPLE_RATE <= 0 || DURATION_MS <= 0) {
                mLastError = "Invalid sampleRateHz or durationMs";
                Log.e(TAG, mLastError);
                return "";
            }

            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setMicrophoneMute(false);
                // Only route to BT if the communication device is not already on a BT voice path.
                // Calling setCommunicationDevice() when LE Audio CIS is already active triggers
                // a stream reconfiguration on the sink, causing a brief audio silence.
                AudioDeviceInfo currentDev = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? audioManager.getCommunicationDevice() : null;
                boolean alreadyOnBt = currentDev != null &&
                        (currentDev.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                         currentDev.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
                if (!alreadyOnBt) {
                    routeAudioToBluetoothForVoiceCall(audioManager);
                }
            }

            // VOICE_COMMUNICATION applies Acoustic Echo Cancellation and Noise Suppression,
            // matching the signal path used by the telephony stack during active calls.
            int audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat);
            if (bufferSize <= 0) {
                mLastError = "Invalid AudioRecord buffer size: " + bufferSize;
                Log.e(TAG, mLastError);
                return "";
            }

            // Double the minimum buffer to reduce the risk of overrun under scheduler jitter.
            mAudioRecord = new AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(channelConfig)
                            .build())
                    .setBufferSizeInBytes(bufferSize * 2)
                    .build();
            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                mLastError = "AudioRecord failed to initialize";
                Log.e(TAG, mLastError);
                mAudioRecord.release();
                mAudioRecord = null;
                return "";
            }

            // getExternalFilesDir returns null when external storage is not mounted.
            File externalDir = mContext.getExternalFilesDir(null);
            if (externalDir == null) {
                mLastError = "External storage is not available";
                Log.e(TAG, mLastError);
                mAudioRecord.release();
                mAudioRecord = null;
                return "";
            }

            // @RpcDefault only applies when the parameter is omitted entirely; it does NOT
            // substitute when the caller explicitly passes null (e.g. Python None).
            String resolvedName = (BASE_FILE_NAME != null && !BASE_FILE_NAME.isEmpty())
                    ? BASE_FILE_NAME : "testMicRecording.raw";
            mOutputFilePath = externalDir.getAbsolutePath() + File.separator + resolvedName;

            // Reset all stats so a reused instance starts clean.
            mBytesWritten = 0;
            mSamplesRead = 0;
            mSumSquares = 0;
            mPeakAmplitude = 0;
            mLastError = "";

            // mIsRunning must be true before the thread starts; the record loop checks it
            // on every iteration and exits immediately if it sees false.
            mIsRunning = true;
            mRecordThread = new Thread(this::recordAudio);
            mRecordThread.start();

            Log.d(TAG, "Started microphone recording: " + mOutputFilePath);
            return mOutputFilePath;
        }

        /**
         * Signals the background record thread to stop, waits (up to 3s) for it to exit, then
         * releases the {@link AudioRecord}. Safe to call multiple times or when nothing is
         * recording.
         *
         * @return the output file path that was being written to.
         */
        String stop() {
            // Signal the record loop to exit on the next iteration.
            mIsRunning = false;

            if (mRecordThread != null) {
                try {
                    // At 16 kHz, one 4096-byte buffer read takes ~128 ms, so 3 s is a safe
                    // upper bound for the thread to drain its current read and exit.
                    mRecordThread.join(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Record thread interrupted", e);
                }
                mRecordThread = null;
            }

            // Release AudioRecord only after the thread has exited to avoid calling stop()
            // on a resource that the thread may still be reading from.
            if (mAudioRecord != null) {
                try {
                    if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        mAudioRecord.stop();
                    }
                    mAudioRecord.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error stopping AudioRecord", e);
                }
                mAudioRecord = null;
            }
            return mOutputFilePath;
        }

        /** Builds a snapshot of recording progress and computed audio levels for the RPC layer. */
        Map<String, Object> getStatus() {
            Map<String, Object> out = new HashMap<>();
            // RMS = sqrt(mean of squared samples) — standard measure of signal power.
            double rms = mSamplesRead > 0 ? Math.sqrt(mSumSquares / mSamplesRead) : 0;
            out.put("isRunning", mIsRunning);
            out.put("filePath", mOutputFilePath != null ? mOutputFilePath : "");
            out.put("bytesWritten", mBytesWritten);
            out.put("sampleRateHz", SAMPLE_RATE);
            out.put("durationMs", DURATION_MS);
            out.put("peakAmplitude", mPeakAmplitude);
            out.put("rmsAmplitude", rms);
            // Peak > 500 (out of 32767) is a practical threshold to distinguish real audio
            // from mic noise floor; adjust if the test environment is unusually noisy/quiet.
            out.put("hasAudio", mPeakAmplitude > 500 && mBytesWritten > 0);
            out.put("format", "pcm16_raw");
            out.put("error", mLastError);
            out.put("callAudioState", voipGetCallAudioState());
            return out;
        }

        /**
         * Background-thread loop: reads PCM buffers from {@link AudioRecord} and writes them
         * straight to disk (raw, no WAV header) until {@code mIsRunning} is cleared, the record
         * duration elapses, or a read error occurs. Runs on the {@link #mRecordThread} started
         * by {@link #start()}.
         */
        private void recordAudio() {
            try (FileOutputStream recordingOutputStream = new FileOutputStream(mOutputFilePath)) {
                byte[] buffer = new byte[4096];
                mAudioRecord.startRecording();
                Log.d(TAG, "Recording started...");
                long startTime = System.currentTimeMillis();

                while (mIsRunning &&
                    mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING &&
                    (System.currentTimeMillis() - startTime) < DURATION_MS) {
                    int bytesRead = mAudioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        recordingOutputStream.write(buffer, 0, bytesRead);
                        updateAudioStats(buffer, bytesRead);
                        mBytesWritten += bytesRead;
                    } else if (bytesRead < 0) {
                        // Negative return codes from AudioRecord.read() are error constants
                        // (e.g. ERROR_INVALID_OPERATION = -3, ERROR_DEAD_OBJECT = -6).
                        mLastError = "AudioRecord read failed: " + bytesRead;
                        Log.e(TAG, mLastError);
                        break;
                    }
                    // bytesRead == 0: no data yet, loop continues — not an error.
                }
                Log.d(TAG, "Recording finished. Duration: " + (System.currentTimeMillis() - startTime) + "ms");
            } catch (IOException | SecurityException | IllegalStateException e) {
                // mOutputFilePath is intentionally preserved on failure so that stop() can
                // still update the WAV header and return a valid path. The partial file
                // remains pullable via ADB for post-mortem diagnosis.
                mLastError = e.toString();
                Log.e(TAG, "Recording failed: " + mLastError, e);
            }
        }

        /**
         * Accumulates peak amplitude and sum-of-squares for RMS over all PCM samples.
         * Samples are little-endian signed 16-bit, two bytes each.
         */
        private void updateAudioStats(byte[] buffer, int bytesRead) {
            for (int i = 0; i + 1 < bytesRead; i += 2) {
                // Reconstruct a signed 16-bit little-endian sample: low byte first, high byte second.
                int sample = (short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8));
                int absSample = Math.abs(sample);
                if (absSample > mPeakAmplitude) {
                    mPeakAmplitude = absSample;
                }
                mSumSquares += sample * (double) sample;
                mSamplesRead++;
            }
        }
    }

    /**
     * Deletes a file previously written by {@link #voipStartMicrophoneRecording}, so tests can
     * clean up recordings between cases instead of letting device storage fill up.
     *
     * @param fileName name of the file to delete (searched under external files dir, falling
     *     back to internal files dir if external storage is unavailable).
     * @return true if the file existed and was deleted; false otherwise.
     */
    @Rpc(description = "Delete a recorded audio file from app-private storage")
    public boolean voipRemoveRecordedFile(String fileName) {
        try {
            /*Eg Path: '/storage/emulated/0/Android/data/com.google.android.mobly.snippet.bundled/files/<fileName>' */
            File dir = mContext.getExternalFilesDir(null);
            if (dir == null) dir = mContext.getFilesDir();
            String filePath = dir.getAbsolutePath() + File.separator + fileName;
            File file = new File(filePath);

            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d(TAG, "Deleted file: " + filePath + " -> " + deleted);
                return deleted;
            } else {
                Log.w(TAG, "File not found: " + filePath);
                return false;
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to delete file " + fileName, ex);
            return false;
        }
    }

    /**
     * Called by the Mobly framework when the snippet is torn down (e.g. test session ends).
     * Unregisters Bluetooth/LE Audio listeners and forces all call/audio state back to idle so
     * leftover state doesn't leak into the next test run.
     */
    @Override
    public void shutdown() {
        Log.d(TAG, "resetCallState");
        if (mClassicBluetoothReceiver != null) {
            try {
                mContext.unregisterReceiver(mClassicBluetoothReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Classic Bluetooth receiver was not registered", e);
            }
            mClassicBluetoothReceiver = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mLeAudioCallback != null) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.unregisterAudioDeviceCallback(mLeAudioCallback);
            }
            mLeAudioCallback = null;
        }
        stopIncomingRingtone();
        stopCallAudio();
        sCurrentConnection = null;
        sIncomingCallActive = false;
        resetAudioMode();
        Log.i(TAG, "VoIpTelephonySnippet shutdown.");
    }

    // ------------------------------------------------------
    // CONNECTION STATE CLASSIC BT DETECTION
    // ------------------------------------------------------
    /**
     * Registers {@link #mClassicBluetoothReceiver} to listen for Classic Bluetooth (HFP)
     * connect/disconnect broadcasts, so an active call's audio can be re-routed to a headset
     * that connects mid-call. Called once from the constructor; no-op if already registered.
     */
    private void registerClassicBluetoothReceiver() {
        if (mClassicBluetoothReceiver != null) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        mClassicBluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device == null) {
                    return;
                }

                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    Log.d(TAG, "Classic BT connected: " + device.getName());
                    updateVoIpCallForBtConnection(device, true);
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    Log.d(TAG, "Classic BT disconnected: " + device.getName());
                }
            }
        };
        mContext.registerReceiver(mClassicBluetoothReceiver, filter);
    }

    // ------------------------------------------------------
    // CONNECTION STATE LE AUDIO DETECTION
    // ------------------------------------------------------
    /**
     * Registers {@link #mLeAudioCallback} (API 31+ only) to observe LE Audio / Bluetooth voice
     * device add/remove events via {@link AudioManager#registerAudioDeviceCallback}. Classic
     * Bluetooth connect/disconnect is covered separately by {@link #registerClassicBluetoothReceiver}
     * because LE Audio devices don't reliably fire the same ACL broadcasts. Called once from the
     * constructor; no-op on older API levels or if already registered.
     */
    private void registerLeAudioCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (mLeAudioCallback != null) {
                return;
            }
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                return;
            }

            mLeAudioCallback = new AudioDeviceCallback() {
                /**
                 * Fired when any audio device (not just Bluetooth) is added to the system. We
                 * filter for LE Audio / Classic SCO voice devices and, if a call is already
                 * active, explicitly re-route to the newly added device rather than trusting
                 * {@link #routeAudioToBluetoothForVoiceCall} to pick it (see inline comment
                 * below for why).
                 */
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    for (AudioDeviceInfo device : addedDevices) {
                        int type = device.getType();
                        if (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            Log.d(TAG, "Bluetooth voice device added: " + device.getProductName()
                                    + " type=" + type);
                            // Route the active call to THIS specific device type.
                            // routeAudioToBluetoothForVoiceCall picks the first entry from
                            // getAvailableCommunicationDevices(), which may still contain the
                            // LE Audio headset while it is being disabled — causing setCommunicationDevice
                            // to be called on a stale device and Classic SCO to never start.
                            if (sCurrentConnection != null
                                    && sCurrentConnection.getState() == Connection.STATE_ACTIVE
                                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                                if (am != null) {
                                    am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                                    for (AudioDeviceInfo commDev : am.getAvailableCommunicationDevices()) {
                                        if (commDev.getType() == type) {
                                            boolean ok = am.setCommunicationDevice(commDev);
                                            Log.d(TAG, "Routed active call to " + commDev.getProductName()
                                                    + " type=" + type + " ok=" + ok);
                                            break;
                                        }
                                    }
                                }
                            } else {
                                updateVoIpCallForBtConnection(device, true);
                            }
                        }
                    }
                }

                /** Fired when an audio device is removed; only used here for diagnostic logging. */
                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    for (AudioDeviceInfo device : removedDevices) {
                        int type = device.getType();
                        if (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ) {
                            Log.d(TAG, "Bluetooth voice device removed: " + device.getProductName());
                        }
                    }
                }
            };
            audioManager.registerAudioDeviceCallback(mLeAudioCallback, new Handler(Looper.getMainLooper()));
        }
    }

    // ------------------------------------------------------
    // AUDIO ROUTING FUNCTION
    // ------------------------------------------------------
    /**
     * Puts the device into call-audio mode and routes to whichever Bluetooth voice device
     * (LE Audio headset or Classic SCO) is available, preferring the modern
     * {@link AudioManager#setCommunicationDevice} API on Android 12+ and falling back to
     * legacy {@link AudioManager#startBluetoothSco} on older versions. If no Bluetooth voice
     * device is available, audio stays on the local route (speaker/earpiece).
     */
    private static void routeAudioToBluetoothForVoiceCall(AudioManager audioManager) {
        if (audioManager == null) {
            Log.w(TAG, "AudioManager is null or No Active call, cannot route voice call audio");
            return;
        }

        try {
            /* Communication mode (required for voice call routing) */
            if (audioManager.getMode() != AudioManager.MODE_IN_COMMUNICATION) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                Log.d(TAG, "Audio mode set to MODE_IN_COMMUNICATION for voice call");
            }

            /* Android 12+ (LE Audio & modern routing) */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AudioDeviceInfo btVoiceDevice = null;
                List<AudioDeviceInfo> devices = audioManager.getAvailableCommunicationDevices();
                if (devices != null) {
                    for (AudioDeviceInfo device : devices) {
                        switch (device.getType()) {
                            case AudioDeviceInfo.TYPE_BLE_HEADSET:      // LE Audio voice
                            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:   // Classic HFP
                                btVoiceDevice = device;
                                break;
                        }
                        if (btVoiceDevice != null) break;
                    }
                }

                if (btVoiceDevice != null) {
                    boolean ok = audioManager.setCommunicationDevice(btVoiceDevice);
                    Log.d(TAG, "Voice call routed to BT device: "
                            + btVoiceDevice.getProductName()
                            + " type=" + btVoiceDevice.getType()
                            + " success=" + ok);
                    return;
                } else {
                    Log.w(TAG, "No Bluetooth voice communication device found");
                }
            }

            /* Classic Bluetooth (HFP / SCO) */
            if (audioManager.isBluetoothScoAvailableOffCall()) {
                audioManager.startBluetoothSco();
                audioManager.setBluetoothScoOn(true);
                Log.d(TAG, "Classic Bluetooth SCO started for voice call");
            }

        } catch (Throwable t) {
            Log.w(TAG, "routeAudioToBluetoothForVoiceCall failed", t);
        }
    }

    // ------------------------------------------------------
    // HELPER: UPDATE VoIP Call WHEN BT CONNECT/DISCONNECT
    // ------------------------------------------------------
    /**
     * Re-routes an already-active call's audio to Bluetooth when a Classic BT device connects
     * mid-call (called from {@link #mClassicBluetoothReceiver}). No-op if there's no active
     * call or the device just disconnected — this method only reacts to new connections.
     */
    private void updateVoIpCallForBtConnection(Object device, boolean connected) {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null && sCurrentConnection != null) {
            if ((sCurrentConnection.getState() == Connection.STATE_ACTIVE) && connected) {
                routeAudioToBluetoothForVoiceCall(audioManager);
            }
        }
    }
}
