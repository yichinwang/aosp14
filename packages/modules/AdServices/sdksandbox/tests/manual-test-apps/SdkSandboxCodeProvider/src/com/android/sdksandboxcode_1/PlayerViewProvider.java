/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdksandboxcode_1;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.WeakHashMap;

/** Create PlayerView with Player and controlling playback based on host activity lifecycle. */
class PlayerViewProvider {

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final WeakHashMap<PlayerView, PlayerState> mCreatedViews = new WeakHashMap<>();

    private boolean mHostActivityStarted = true;

    public View createPlayerView(Context windowContext, String videoUrl) {
        final PlayerView view = new PlayerView(windowContext);
        final PlayerState playerState = new PlayerState(windowContext, videoUrl);

        mMainHandler.post(
                () -> {
                    mCreatedViews.put(view, playerState);
                    if (mHostActivityStarted) {
                        final Player player = playerState.initializePlayer();
                        view.setPlayer(player);
                    }
                });

        return view;
    }

    public void onHostActivityStarted() {
        mMainHandler.post(
                () -> {
                    mHostActivityStarted = true;
                    mCreatedViews.forEach(
                            (view, state) -> {
                                if (view.getPlayer() == null) {
                                    final Player player = state.initializePlayer();
                                    view.setPlayer(player);
                                    view.onResume();
                                }
                            });
                });
    }

    public void onHostActivityStopped() {
        mMainHandler.post(
                () -> {
                    mHostActivityStarted = false;
                    mCreatedViews.forEach(
                            (view, state) -> {
                                view.onPause();
                                state.releasePlayer();
                                view.setPlayer(null);
                            });
                });
    }

    private static final class PlayerState {
        private final Context mContext;
        private final MediaItem mMediaItem;
        private ExoPlayer mPlayer;
        private boolean mAutoPlay;
        private long mAutoPlayPosition;

        private PlayerState(Context context, String videoUrl) {
            mContext = context;
            mMediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            mAutoPlayPosition = C.TIME_UNSET;
            mAutoPlay = true;
        }

        private Player initializePlayer() {
            if (mPlayer != null) {
                return mPlayer;
            }

            AudioAttributes audioAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build();

            mPlayer =
                    new ExoPlayer.Builder(mContext)
                            .setAudioAttributes(audioAttributes, true)
                            .build();
            mPlayer.setPlayWhenReady(mAutoPlay);
            mPlayer.setMediaItem(mMediaItem);
            boolean hasStartPosition = mAutoPlayPosition != C.TIME_UNSET;
            if (hasStartPosition) {
                mPlayer.seekTo(0, mAutoPlayPosition);
            }
            mPlayer.prepare();

            return mPlayer;
        }

        private void releasePlayer() {
            if (mPlayer == null) {
                return;
            }

            mAutoPlay = mPlayer.getPlayWhenReady();
            mAutoPlayPosition = mPlayer.getContentPosition();

            mPlayer.release();
            mPlayer = null;
        }
    }
}
