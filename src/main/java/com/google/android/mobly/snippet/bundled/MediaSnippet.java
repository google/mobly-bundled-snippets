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

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import java.io.IOException;

/* Snippet class to control media playback. */
public class MediaSnippet implements Snippet {

    private final MediaPlayer mPlayer;

    public MediaSnippet() {
        mPlayer = new MediaPlayer();
    }

    @Rpc(description = "Resets snippet media player to an idle state, regardless of current state.")
    public void mediaReset() {
        mPlayer.reset();
    }

    @Rpc(description = "Play an audio file stored at a specified file path in external storage.")
    public void mediaPlayAudioFile(String mediaFilePath) throws IOException {
        mediaReset();
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            mPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }
        mPlayer.setDataSource(mediaFilePath);
        mPlayer.prepare(); // Synchronous call blocks until the player is ready for playback.
        mPlayer.start();
    }

    @Rpc(description = "Stops media playback.")
    public void mediaStop() throws IOException {
        mPlayer.stop();
    }

    @Override
    public void shutdown() {}
}
