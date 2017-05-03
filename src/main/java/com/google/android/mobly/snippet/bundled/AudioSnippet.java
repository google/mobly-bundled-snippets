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
import android.media.AudioManager;
import android.support.test.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.lang.reflect.Method;

/* Snippet class to control audio */
public class AudioSnippet implements Snippet {

    private final AudioManager mAudioManager;

    public AudioSnippet() {
        Context context = InstrumentationRegistry.getContext();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Rpc(description = "Gets the music stream volume.")
    public int getMusicVolume() {
        return mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Rpc(description = "Gets the maximum music stream volume value.")
    public int getMusicMaxVolume() {
        return mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    @Rpc(
        description =
                "Sets the music stream volume. The minimum value is 0. Use getMusicMaxVolume"
                        + "to determine the maximum."
    )
    public void setMusicVolume(Integer value) {
        mAudioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC, value, 0 /* flags, 0 = no flags */);
    }

    @Rpc(description = "Gets the ringer volume.")
    public int getRingVolume() {
        return mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
    }

    @Rpc(description = "Gets the maximum ringer volume value.")
    public int getRingMaxVolume() {
        return mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
    }

    @Rpc(
        description =
                "Sets the ringer stream volume. The minimum value is 0. Use getRingMaxVolume"
                        + "to determine the maximum."
    )
    public void setRingVolume(Integer value) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, value, 0 /* flags, 0 = no flags */);
    }

    @Rpc(description = "Silences all audio streams.")
    public void muteAll() throws Exception {
        /* Get numStreams from AudioSystem through reflection. If for some reason this fails,
         * calling muteAll will throw. */
        Class<?> audioSystem = Class.forName("android.media.AudioSystem");
        Method getNumStreamTypes = audioSystem.getDeclaredMethod("getNumStreamTypes");
        int numStreams = (int) getNumStreamTypes.invoke(null /* instance */);
        for (int i = 0; i < numStreams; i++) {
            mAudioManager.setStreamVolume(i /* audio stream */, 0 /* value */, 0 /* flags */);
        }
    }

    @Rpc(
        description =
                "Puts the ringer volume at the lowest setting, but does not set it to "
                        + "DO NOT DISTURB; the phone will vibrate when receiving a call."
    )
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
