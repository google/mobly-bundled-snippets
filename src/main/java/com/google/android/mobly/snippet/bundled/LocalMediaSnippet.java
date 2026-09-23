package com.google.android.mobly.snippet.bundled;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.MediaRouter;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.io.File;

/**
 * Snippet class for controlling local (non-VoIP) media playback, e.g. simulating a phone
 * streaming a song to a Bluetooth speaker/headset under test.
 *
 * <p>This intentionally lives in a separate class from {@link MediaSnippet} rather than modifying
 * it in place, so both the plain upstream Mobly media RPCs and these af-specific ones stay
 * available side by side. Because both classes are loaded into the same snippet server (see
 * AndroidManifest.xml's {@code mobly-snippets} meta-data), every {@code @Rpc} method name here is
 * prefixed with {@code localMedia} to guarantee it never collides with {@link MediaSnippet}'s
 * {@code media*} RPC names — Mobly's {@code SnippetManager} throws at startup if two classes
 * register an RPC with the same name.
 *
 * <p>High-level flow for anyone new to this file:
 * <ul>
 *   <li>{@link #localMediaPlayAudioFile} loads a file into a {@link MediaPlayer}, requests audio
 *       focus, and starts playback on the MEDIA usage/stream (as opposed to
 *       {@code VoIpTelephonySnippet}, which plays audio on the VOICE_COMMUNICATION usage).</li>
 *   <li>A {@link MediaSession} mirrors playback state (playing/paused/stopped) so that a
 *       connected Bluetooth device's AVRCP/LE Audio media-control layer sees accurate transport
 *       state, just like a real music app would publish.</li>
 *   <li>Audio routing to Bluetooth for media is mostly automatic (Android routes USAGE_MEDIA
 *       audio to whatever output device is connected); this class only actively intervenes for
 *       logging/diagnostics, not to force routing the way VoIP call audio does.</li>
 * </ul>
 */
public class LocalMediaSnippet implements Snippet {
    private static final String TAG = "LocalMediaSnippet";
    private static final int TIMEOUT_SEC = 5;
    private static final long SEEK_INTERVAL_MS = 10000;

    /*
     * Official Android references used by this class:
     * MediaPlayer: https://developer.android.com/reference/android/media/MediaPlayer
     * MediaSession: https://developer.android.com/reference/android/media/session/MediaSession
     * PlaybackState: https://developer.android.com/reference/android/media/session/PlaybackState
     * MediaMetadata: https://developer.android.com/reference/android/media/MediaMetadata
     * AudioDeviceCallback: https://developer.android.com/reference/android/media/AudioDeviceCallback
     * AudioDeviceInfo: https://developer.android.com/reference/android/media/AudioDeviceInfo
     * Bluetooth ACL broadcasts: https://developer.android.com/reference/android/bluetooth/BluetoothDevice#ACTION_ACL_CONNECTED
     */

    private final Context mContext;
    private final MediaPlayer mPlayer;
    private final MediaRouter mMediaRouter;

    // NOTE: mMediaSession cannot be final since its initialization is posted asynchronously
    // (MediaSession must be created on a thread with a Looper — see the constructor).
    private MediaSession mMediaSession = null;

    /** Listens for Classic Bluetooth (HFP/A2DP) connect/disconnect to log routing diagnostics. */
    private BroadcastReceiver mClassicBluetoothReceiver;
    /** Listens for LE Audio device add/remove to log routing diagnostics (API 31+ only). */
    private AudioDeviceCallback mLeAudioCallback;

    /**
     * Creates the underlying {@link MediaPlayer}, registers Bluetooth connect/disconnect
     * listeners for routing diagnostics, and asynchronously initializes a {@link MediaSession}
     * so playback state is published to any connected Bluetooth media-control layer (AVRCP/LE
     * Audio).
     */
    public LocalMediaSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPlayer = new MediaPlayer();
        mMediaRouter = (MediaRouter) mContext.getSystemService(Context.MEDIA_ROUTER_SERVICE);

        registerClassicBluetoothReceiver();
        registerLeAudioCallback();

        // MediaSession must be created on a thread with a Looper, like the main thread.
        // We use the Handler associated with the application's main Looper to post the
        // initialization since the Snippet constructor may run off the main thread.
        Handler mainHandler = new Handler(mContext.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                    try {
                        mMediaSession = new MediaSession(mContext, TAG);
                        mMediaSession.setCallback(new MediaSessionCallback());
                        mMediaSession.setFlags(
                                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
                        // Set it to active so it can receive media button events and
                        // publish transport state to the Bluetooth AVRCP/LE Audio layer.
                        mMediaSession.setActive(true);
                        updateMediaSessionPlaybackState(PlaybackState.STATE_STOPPED);
                        Log.d(TAG, "MediaSession initialized successfully on the Main Thread.");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize MediaSession.", e);
                        mMediaSession = null;
                    }
                } else {
                    Log.w(TAG, "MediaSession is not supported on this API level.");
                }
            }
        });
    }

    /**
     * Updates the MediaSession playback state and triggers the Bluetooth AVRCP/LE Audio
     * indication. Every state change here is what causes the sink device to receive a media
     * status update over the Bluetooth transport.
     */
    private void updateMediaSessionPlaybackState(int state) {
        if (mMediaSession == null) {
            // Most commonly hit right after construction: the MediaSession is created
            // asynchronously on the main looper (see constructor) and may not exist yet when
            // an early RPC call tries to publish a state change. Logged at warn level because a
            // dropped state update means the Bluetooth sink never sees this transition.
            Log.w(TAG, "Skipped publishing playback state=" + playbackStateToString(state)
                    + ": MediaSession not initialized yet");
            return;
        }

        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_FAST_FORWARD
                | PlaybackState.ACTION_REWIND
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

        long position = getCurrentPlaybackPosition();
        // Playback speed is 1.0 only when actively playing; 0.0 for all paused/stopped states.
        float playbackSpeed = state == PlaybackState.STATE_PLAYING ? 1.0f : 0.0f;

        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position, playbackSpeed)
                .build();

        mMediaSession.setPlaybackState(playbackState);
        // Records exactly what was published (state/position/speed) so a test failure like
        // "seek didn't take effect" can be diagnosed from logs alone, without reproducing live.
        Log.d(TAG, "Published MediaSession state=" + playbackStateToString(state)
                + " positionMs=" + position + " speed=" + playbackSpeed);

        // Log which BT transport is active so we can confirm the indication reaches the sink.
        logPublishedBluetoothMediaIndication(state);
    }

    /** Returns the player's current position, or PLAYBACK_POSITION_UNKNOWN if not in a valid state. */
    private long getCurrentPlaybackPosition() {
        try {
            return mPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            // MediaPlayer may not be in a valid state (e.g. after reset); position is unknown.
            // Logged because a run of PLAYBACK_POSITION_UNKNOWN in the logs is otherwise
            // indistinguishable from a real unknown-position value with no way to tell why.
            Log.w(TAG, "getCurrentPlaybackPosition: player not in a valid state", e);
            return PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        }
    }

    /** Publishes basic track metadata (title/artist/duration) to the MediaSession. */
    private void updateMediaSessionMetadata(String mediaFilePath) {
        if (mMediaSession == null) {
            return;
        }

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, new File(mediaFilePath).getName())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Mobly LocalMediaSnippet");

        try {
            metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, mPlayer.getDuration());
        } catch (IllegalStateException e) {
            // Duration is only available after prepare(); ignore if not ready yet.
            Log.w(TAG, "Media duration unavailable", e);
        }

        mMediaSession.setMetadata(metadataBuilder.build());
    }

    /**
     * Seeks relative to the current position, clamped to [0, duration].
     * Positive offsetMs seeks forward; negative seeks backward.
     */
    private boolean seekMediaBy(long offsetMs) {
        try {
            int durationMs = mPlayer.getDuration();
            int currentPositionMs = mPlayer.getCurrentPosition();
            long targetPositionMs = Math.max(0, Math.min(durationMs, currentPositionMs + offsetMs));
            Log.d(TAG, "seekMediaBy: offsetMs=" + offsetMs + " currentPositionMs=" + currentPositionMs
                    + " durationMs=" + durationMs + " -> targetPositionMs=" + targetPositionMs);
            return seekMediaTo(targetPositionMs);
        } catch (Exception e) {
            Log.e(TAG, "Failed to seek media by offsetMs=" + offsetMs, e);
            return false;
        }
    }

    /** Seeks to an absolute position, clamped to [0, duration], and refreshes MediaSession state. */
    private boolean seekMediaTo(long positionMs) {
        try {
            int durationMs = mPlayer.getDuration();
            int targetPositionMs = (int) Math.max(0, Math.min(durationMs, positionMs));
            if (targetPositionMs != positionMs) {
                // Worth flagging distinctly from the normal seek log: the caller asked for a
                // position outside [0, duration] and got clamped, which can otherwise look like
                // a silently "wrong" seek in test results.
                Log.w(TAG, "seekMediaTo: requested positionMs=" + positionMs
                        + " clamped to targetPositionMs=" + targetPositionMs
                        + " (durationMs=" + durationMs + ")");
            }
            mPlayer.seekTo(targetPositionMs);
            Log.d(TAG, "seekMediaTo: seeked to " + targetPositionMs + "ms");
            // Keep MediaSession state consistent with the player after every seek.
            updateMediaSessionPlaybackState(mPlayer.isPlaying()
                    ? PlaybackState.STATE_PLAYING
                    : PlaybackState.STATE_PAUSED);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to seek media to positionMs=" + positionMs, e);
            return false;
        }
    }

    /** Handles transport-control callbacks delivered to the MediaSession (e.g. from a paired remote/headset). */
    private class MediaSessionCallback extends MediaSession.Callback {
        @Override
        public void onPlay() {
            Log.d(TAG, "MediaSession received onPlay command.");
            // Call mPlayer.start() directly rather than going through localMediaPlayAudioFile()
            // because the file is already loaded and prepared; we only need to resume.
            try {
                if (!mPlayer.isPlaying()) {
                    mPlayer.start();
                    updateMediaSessionPlaybackState(PlaybackState.STATE_PLAYING);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling onPlay", e);
            }
        }

        @Override
        public void onPause() {
            Log.d(TAG, "MediaSession received onPause command.");
            if (!localMediaPause()) {
                Log.e(TAG, "Error handling onPause.");
            }
        }

        @Override
        public void onStop() {
            Log.d(TAG, "MediaSession received onStop command.");
            if (!localMediaStop()) {
                Log.e(TAG, "Error handling onStop.");
            }
        }

        @Override
        public void onFastForward() {
            Log.d(TAG, "MediaSession received onFastForward command.");
            if (!localMediaFastForward()) {
                Log.e(TAG, "Error handling onFastForward.");
            }
        }

        @Override
        public void onRewind() {
            Log.d(TAG, "MediaSession received onRewind command.");
            if (!localMediaRewind()) {
                Log.e(TAG, "Error handling onRewind.");
            }
        }

        @Override
        public void onSeekTo(long requestedPositionMs) {
            Log.d(TAG, "MediaSession received onSeekTo command: " + requestedPositionMs);
            if (!seekMediaTo(requestedPositionMs)) {
                Log.e(TAG, "Error handling onSeekTo.");
            }
        }

        // Map skip to fast-forward/rewind since this is a single-track test player.
        @Override
        public void onSkipToNext() {
            onFastForward();
        }

        @Override
        public void onSkipToPrevious() {
            onRewind();
        }
    }

    /**
     * Resets the local media player to idle, regardless of its current state, and restores
     * AudioManager to MODE_NORMAL (in case a prior VoIP call left it in
     * MODE_IN_COMMUNICATION). Safe to call at any time, including before the first playback.
     */
    @Rpc(description = "Resets local media player to an idle state, regardless of current state.")
    public void localMediaReset() {
        mPlayer.reset();

        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // clearCommunicationDevice() is required on Android 12+ to release any
            // VoIP-specific routing set during a call before switching back to media mode.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice();
                    Log.d(TAG, "Communication device cleared (resetting local media context)");
                } catch (Throwable ignore) {
                }
            }

            // Force audio mode back to NORMAL so media routing is not stuck in
            // MODE_IN_COMMUNICATION (which may have been set during a VoIP call).
            if (audioManager.getMode() != AudioManager.MODE_NORMAL) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                Log.d(TAG, "Audio mode forced to MODE_NORMAL in localMediaReset");
            }

            // Wait for the mode change to propagate before proceeding.
            Utils.waitUntil(() -> audioManager.getMode() == AudioManager.MODE_NORMAL, TIMEOUT_SEC);
            Log.d(TAG, "AudioManager ready for local media playback");
        } else {
            Log.e(TAG, "AudioManager is null in localMediaReset");
        }

        if (mMediaSession != null) {
            updateMediaSessionPlaybackState(PlaybackState.STATE_STOPPED);
        }
    }

    /**
     * Plays an audio file stored at {@code mediaFilePath} using the local (non-VoIP) media
     * player, on the MEDIA audio usage/stream. Requests audio focus so other audio sources
     * duck/pause, and publishes playing state + metadata to the MediaSession.
     *
     * @param mediaFilePath absolute path to a readable audio file; must be non-null/non-empty.
     * @return true if playback started successfully; false otherwise.
     */
    @Rpc(description = "Play an audio file stored at a specified file path in external storage.")
    public boolean localMediaPlayAudioFile(String mediaFilePath) {
        Log.d(TAG, "Local media filePath: " + mediaFilePath);
        if (mediaFilePath == null || mediaFilePath.isEmpty()) {
            Log.e(TAG, "localMediaPlayAudioFile requires a non-empty mediaFilePath");
            return false;
        }

        // Reset MediaPlayer and AudioManager state before loading a new source.
        // This is especially important after a VoIP call, which changes audio mode and routing.
        localMediaReset();

        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.w(TAG, "AudioManager is null, cannot play local media");
            return false;
        }

        // Request AUDIOFOCUS_GAIN so other audio sources (e.g. notifications) duck/pause.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build())
                        .build();

                int focusResult = audioManager.requestAudioFocus(focusRequest);
                if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w(TAG, "Audio focus not granted for local media playback");
                }
            } else {
                int focusResult = audioManager.requestAudioFocus(
                        null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w(TAG, "Audio focus not granted for local media playback");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to request audio focus", t);
        }

        // Configure MediaPlayer audio attributes so Android routes audio through the media stream.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            mPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }

        try {
            mPlayer.setDataSource(mediaFilePath);
            // prepare() is synchronous — blocks until the player is ready for playback.
            mPlayer.prepare();
            updateMediaSessionMetadata(mediaFilePath);
            mPlayer.start();
            Log.d(TAG, "Local media playback started: " + mediaFilePath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play local media file: " + mediaFilePath, e);
            return false;
        }

        if (mMediaSession != null) {
            updateMediaSessionPlaybackState(PlaybackState.STATE_PLAYING);
        }
        return true;
    }

    /** Checks whether the local media player is currently playing. */
    @Rpc(description = "Checks if local media is currently playing.")
    public boolean localMediaIsPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }

    /**
     * Stops local media playback, publishes STOPPED to the MediaSession, and resets the player
     * so it's ready for the next {@link #localMediaPlayAudioFile} call.
     */
    @Rpc(description = "Stops local media playback and resets player.")
    public boolean localMediaStop() {
        try {
            if (mPlayer.isPlaying()) {
                mPlayer.stop();
                Log.d(TAG, "Local media playback stopped");
            }

            if (mMediaSession != null) {
                updateMediaSessionPlaybackState(PlaybackState.STATE_STOPPED);
            }

            localMediaReset();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "localMediaStop failed", e);
            return false;
        }
    }

    /**
     * Pauses local media playback (leaving position/state intact for a later resume) and
     * publishes PAUSED to the MediaSession.
     */
    @Rpc(description = "Pauses local media playback.")
    public boolean localMediaPause() {
        try {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                Log.d(TAG, "Local media playback paused");

                if (mMediaSession != null) {
                    updateMediaSessionPlaybackState(PlaybackState.STATE_PAUSED);
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "localMediaPause failed", e);
            return false;
        }
    }

    /** Fast-forwards local media playback by {@link #SEEK_INTERVAL_MS} (10 seconds). */
    @Rpc(description = "Fast forwards local media playback by 10 seconds.")
    public boolean localMediaFastForward() {
        return seekMediaBy(SEEK_INTERVAL_MS);
    }

    /** Rewinds local media playback by {@link #SEEK_INTERVAL_MS} (10 seconds). */
    @Rpc(description = "Rewinds local media playback by 10 seconds.")
    public boolean localMediaRewind() {
        return seekMediaBy(-SEEK_INTERVAL_MS);
    }

    /** Returns the device type of the currently selected live-audio route (e.g. Bluetooth/speaker). */
    @Rpc(
            description =
                    "Returns the type of the receiver device associated with the live audio route.")
    public int localMediaGetLiveAudioRouteType() {
        if (mMediaRouter == null) {
            Log.w(TAG, "localMediaGetLiveAudioRouteType: MediaRouter is null, returning -1");
            return -1;
        }
        int routeType = mMediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO).getDeviceType();
        Log.d(TAG, "localMediaGetLiveAudioRouteType: " + routeType);
        return routeType;
    }

    /** Returns the user-visible name of the currently selected live-audio route. */
    @Rpc(description = "Returns the user-visible name of the live audio route.")
    public String localMediaGetLiveAudioRouteName() {
        if (mMediaRouter == null) {
            Log.w(TAG, "localMediaGetLiveAudioRouteName: MediaRouter is null, returning \"Unknown\"");
            return "Unknown";
        }
        String routeName = mMediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO).getName().toString();
        Log.d(TAG, "localMediaGetLiveAudioRouteName: " + routeName);
        return routeName;
    }

    /**
     * Called by the Mobly framework when the snippet is torn down. Unregisters Bluetooth/LE
     * Audio listeners and releases the MediaPlayer/MediaSession so nothing leaks between test
     * runs.
     */
    @Override
    public void shutdown() {
        Log.d(TAG, "shutdown: releasing MediaPlayer/MediaSession and unregistering BT listeners");
        // Unregister the Classic BT broadcast receiver to avoid leaking the registration.
        if (mClassicBluetoothReceiver != null) {
            try {
                mContext.unregisterReceiver(mClassicBluetoothReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Classic Bluetooth receiver was not registered", e);
            }
            mClassicBluetoothReceiver = null;
        }

        // Unregister LE Audio device callback (Android 12+ only).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mLeAudioCallback != null) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.unregisterAudioDeviceCallback(mLeAudioCallback);
            }
            mLeAudioCallback = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
        }
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
        }
        Log.i(TAG, "LocalMediaSnippet shutdown complete.");
    }

    // ---------------------------
    // CLASSIC BT DETECTION
    // ---------------------------
    /**
     * Registers a BroadcastReceiver for Classic Bluetooth ACL connect/disconnect events, purely
     * for routing diagnostics/logging. Disconnect handling intentionally does not pause
     * playback by default (see inline comment) so media isn't interrupted by transient
     * disconnections during testing.
     */
    private void registerClassicBluetoothReceiver() {
        if (mClassicBluetoothReceiver != null) {
            Log.d(TAG, "registerClassicBluetoothReceiver: already registered, skipping");
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
                    updateMediaForBtConnection(true);
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    Log.d(TAG, "Classic BT disconnected: " + device.getName());
                    /*
                     * Enable this to pause playback on the source when the Bluetooth
                     * (Classic or LE Audio) connection is disconnected.
                     */
                    // localMediaPause();
                }
            }
        };
        mContext.registerReceiver(mClassicBluetoothReceiver, filter);
        Log.d(TAG, "registerClassicBluetoothReceiver: registered for ACL connect/disconnect events");
    }

    // ------------------------------------------------------
    // LE AUDIO DETECTION
    // ------------------------------------------------------
    /**
     * Registers an AudioDeviceCallback to detect LE Audio (BLE headset/speaker/broadcast)
     * connect/disconnect events on Android 12+, purely for routing diagnostics/logging. Classic
     * BT uses ACL broadcasts instead because AudioDeviceCallback does not fire for A2DP on all
     * devices.
     */
    private void registerLeAudioCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (mLeAudioCallback != null) {
                Log.d(TAG, "registerLeAudioCallback: already registered, skipping");
                return;
            }
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                Log.w(TAG, "registerLeAudioCallback: AudioManager is null, cannot register");
                return;
            }

            mLeAudioCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    for (AudioDeviceInfo device : addedDevices) {
                        int deviceType = device.getType();
                        if (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET
                                || deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER
                                || deviceType == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                            Log.d(TAG, "LE Audio added: " + device.getProductName());
                            updateMediaForBtConnection(true);
                        }
                    }
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    for (AudioDeviceInfo device : removedDevices) {
                        int deviceType = device.getType();
                        if (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET
                                || deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER
                                || deviceType == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                            Log.d(TAG, "LE Audio removed: " + device.getProductName());
                            /*
                             * Enable this to pause playback on the source when the Bluetooth
                             * (Classic or LE Audio) connection is disconnected.
                             */
                            // localMediaPause();
                        }
                    }
                }
            };
            // Register on the main looper so callbacks are delivered on the UI thread,
            // consistent with where MediaSession was initialized.
            audioManager.registerAudioDeviceCallback(mLeAudioCallback, new Handler(Looper.getMainLooper()));
            Log.d(TAG, "registerLeAudioCallback: registered for LE Audio device add/remove events");
        } else {
            Log.d(TAG, "registerLeAudioCallback: skipped, API " + Build.VERSION.SDK_INT + " < S");
        }
    }

    // ---------------------------
    // HELPER: BT CONNECT RESPONSE
    // ---------------------------
    /**
     * Called by both the Classic BT and LE Audio listeners when a voice/media-capable Bluetooth
     * device connects. Android already auto-routes USAGE_MEDIA audio to the newly connected
     * device, so this only logs the currently available BT outputs for diagnostics — it does
     * NOT call {@code setCommunicationDevice()} (that API only affects
     * USAGE_VOICE_COMMUNICATION streams, not media).
     */
    private void updateMediaForBtConnection(boolean connected) {
        if (!connected) {
            return;
        }
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.w(TAG, "updateMediaForBtConnection: AudioManager is null, cannot log output devices");
            return;
        }
        try {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            Log.d(TAG, "BT connected; current output device count=" + devices.length);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to enumerate output devices after BT connect", t);
        }
    }

    // ------------------------------------------------------
    // HELPER: LOG BLUETOOTH MEDIA INDICATION
    // ------------------------------------------------------
    /**
     * Logs which BT transport is active when the MediaSession state changes. Helps confirm that
     * AVRCP/LE Audio state indications are sent to the correct sink device during manual/log
     * inspection.
     */
    private void logPublishedBluetoothMediaIndication(int state) {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }

        boolean btConnected = false;
        String btTransportName = "NONE";

        // If more than one Bluetooth output is present, the last one found in the device list
        // wins; this is only used for diagnostic logging, so exact precedence doesn't matter.
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            String transportName = bluetoothOutputTypeName(device.getType());
            if (transportName != null) {
                btConnected = true;
                btTransportName = transportName;
            }
        }

        if (btConnected) {
            Log.d(TAG, "Publishing media indication to sink | state="
                    + playbackStateToString(state) + " | transport=" + btTransportName);
        } else {
            Log.d(TAG, "Media state updated, but no Bluetooth sink connected");
        }
    }

    /**
     * Maps an output {@link AudioDeviceInfo} type to a readable Bluetooth transport name for
     * logging, or null if the type isn't a Bluetooth output. Distinguishing Classic (A2DP) from
     * LE Audio (headset/speaker/broadcast) and hearing aids matters because each negotiates
     * media audio differently at the Bluetooth stack level.
     */
    private static String bluetoothOutputTypeName(int deviceType) {
        switch (deviceType) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "BT Classic (A2DP)";

            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                return "BT LE Audio";

            case AudioDeviceInfo.TYPE_HEARING_AID:
                return "BT Hearing Aid";

            default:
                return null;
        }
    }

    /** Maps a {@code PlaybackState.STATE_*} constant to its readable name for logging. */
    private static String playbackStateToString(int state) {
        switch (state) {
            case PlaybackState.STATE_NONE:
                return "NONE";
            case PlaybackState.STATE_STOPPED:
                return "STOPPED";
            case PlaybackState.STATE_PAUSED:
                return "PAUSED";
            case PlaybackState.STATE_PLAYING:
                return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING:
                return "FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING:
                return "REWINDING";
            case PlaybackState.STATE_BUFFERING:
                return "BUFFERING";
            case PlaybackState.STATE_ERROR:
                return "ERROR";
            case PlaybackState.STATE_CONNECTING:
                return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                return "SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
                return "SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return "SKIPPING_TO_QUEUE_ITEM";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }
}
