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
import android.media.AudioManager;
//import android.media.AudioSystem;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/* Snippet class to control audio */
public class AudioSnippet implements Snippet {

    private final AudioManager mAudioManager;

    public AudioSnippet() {
        Context context = InstrumentationRegistry.getContext();
        this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Rpc(description = "Gets the music stream volume.")
    public int getMusicVolume() {
        return mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Rpc(description = "Sets the music stream volume.")
    public void setMusicVolume(Integer value) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0 /* flags, 0 = no flags */);
    }

    @Rpc(description = "Gets the ringer volume.")
    public int getRingVolume() {
        return mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
    }

    @Rpc(description = "Sets the ringer volume.")
    public void setRingVolume(Integer value) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, value, 0 /* flags, 0 = no flags */);
    }

    @Rpc(description = "Silences all audio streams.")
    public void muteAll() {
        // for (int i=0; i<AudioSystem.getNumStreamTypes(); i++) {
        //    mAudioManager.setStreamVolume(
        //            i /* Stream type */,
        //            0 /* value */,
        //            0 /* flags, 0 = no flags */);
        //}
        mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_DTMF, 0, 0);
        muteMusic(); // STREAM_MUSIC
        mAudioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
        muteRing();  // STREAM_RING
        mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0);

    }

    @Rpc(description = "Puts the ringer volume at the lowest setting, but not set it DO NOT DISTURB.")
    public void muteRing() {
        setRingVolume(0);
    }

    @Rpc(description = "Mute music stream.")
    public void muteMusic() {
        setMusicVolume(0);
    }

    @Override
    public void shutdown() {}
}
