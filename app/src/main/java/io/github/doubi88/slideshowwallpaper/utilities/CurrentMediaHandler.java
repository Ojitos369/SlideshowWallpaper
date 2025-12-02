/*
 * Slideshow Wallpaper: An Android live wallpaper displaying custom images and videos.
 * Copyright (C) 2022  Doubi88 <tobis_mail@yahoo.de>
 *
 * Slideshow Wallpaper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Slideshow Wallpaper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package io.github.doubi88.slideshowwallpaper.utilities;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager;

public class CurrentMediaHandler {
    private static final String TAG = "CurrentMediaHandler";

    private int currentIndex;
    private MediaInfo currentMedia;

    private SharedPreferencesManager manager;
    private int width;
    private int height;
    private Context context;
    private android.view.SurfaceHolder surfaceHolder;
    private final Object lock = new Object();
    private Handler mainHandler;

    private boolean runnable;
    private boolean isPaused;

    private Timer currentTimer;
    private ArrayList<NextMediaListener> nextMediaListeners;

    private ExoPlayer exoPlayer;
    private boolean isVideoPlaying = false;
    private boolean isSurfaceLocked = false;

    public interface NextMediaListener {
        void nextMedia(MediaInfo media);
    }

    public CurrentMediaHandler(SharedPreferencesManager manager, int width, int height, Context context,
            android.view.SurfaceHolder surfaceHolder) {
        this.manager = manager;
        this.width = width;
        this.height = height;
        this.context = context;
        this.surfaceHolder = surfaceHolder;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.runnable = true;
        this.isPaused = false;
        nextMediaListeners = new ArrayList<>(1);
    }

    private void initializeExoPlayer() {
        if (exoPlayer == null) {
            // ExoPlayer with default renderers (includes ImageRenderer from media3-image)
            exoPlayer = new ExoPlayer.Builder(context).build();
            Log.d(TAG, "ExoPlayer created with image support");

            // Set surface ONCE - it stays under ExoPlayer control forever
            if (surfaceHolder != null && surfaceHolder.getSurface() != null
                    && surfaceHolder.getSurface().isValid()) {
                exoPlayer.setVideoSurface(surfaceHolder.getSurface());
                Log.d(TAG, "Surface set to ExoPlayer");
            }

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "Media playback completed (video or image)");
                        isVideoPlaying = false;
                        if (runnable && !isPaused) {
                            forceNextMedia(context);
                        }
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error", error);
                    isVideoPlaying = false;
                    if (runnable && !isPaused) {
                        forceNextMedia(context);
                    }
                }
            });
        }
    }

    /**
     * Unified method to prepare and play BOTH images and videos using ExoPlayer.
     * Uses media3-image library for image rendering with duration.
     */
    private void prepareMedia(Uri uri, boolean isVideo) {
        Log.d(TAG, "prepareMedia: " + uri + " (isVideo=" + isVideo + ")");

        initializeExoPlayer();

        try {
            MediaItem mediaItem;

            if (isVideo) {
                // Video: standard MediaItem
                mediaItem = MediaItem.fromUri(uri);
                Log.d(TAG, "Preparing video MediaItem");
            } else {
                // Image: MediaItem with duration using media3-image
                long imageDurationMs = getImageDurationMs();
                mediaItem = new MediaItem.Builder()
                        .setUri(uri)
                        .setImageDurationMs(imageDurationMs)
                        .build();
                Log.d(TAG, "Preparing image MediaItem with duration: " + imageDurationMs + "ms");
            }

            // Apply mute setting (videos only, images have no audio)
            if (isVideo && manager.getMuteVideos()) {
                exoPlayer.setVolume(0f);
            } else if (isVideo) {
                exoPlayer.setVolume(1f);
            }

            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();

            if (!isPaused) {
                exoPlayer.play();
                if (isVideo) {
                    isVideoPlaying = true;
                }
                Log.d(TAG, "Playback started");
            } else {
                Log.d(TAG, "Media prepared but paused. isPaused=" + isPaused);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error preparing media: " + e.getMessage(), e);
            isVideoPlaying = false;
            if (runnable && !isPaused) {
                forceNextMedia(context);
            }
        }
    }

    /**
     * Get image display duration in milliseconds from preferences.
     */
    private long getImageDurationMs() {
        int seconds = 5; // default
        try {
            seconds = manager.getSecondsBetweenImages();
        } catch (Exception e) {
            Log.e(TAG, "Error getting image duration, using default", e);
        }
        return seconds * 1000L;
    }

    public void updateSurface(android.view.SurfaceHolder holder) {
        this.surfaceHolder = holder;

        if (exoPlayer != null) {
            // Validate new surface before updating
            if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                try {
                    exoPlayer.setVideoSurface(holder.getSurface());
                    Log.d(TAG, "Surface updated successfully");

                    // Resume if was playing and not paused
                    if (!isPaused) {
                        exoPlayer.play();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating surface", e);
                    pauseVideo();
                }
            } else {
                Log.w(TAG, "New surface is invalid, pausing playback");
                pauseVideo();
            }
        }
    }

    public void addNextMediaListener(NextMediaListener l) {
        this.nextMediaListeners.add(l);
    }

    public void removeNextMediaListener(NextMediaListener l) {
        this.nextMediaListeners.remove(l);
    }

    private void notifyNextMediaListeners(MediaInfo media) {
        for (NextMediaListener l : nextMediaListeners) {
            l.nextMedia(media);
        }
    }

    public MediaInfo getCurrentMedia() {
        return currentMedia;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setDimensions(int width, int height, Context context) {
        this.width = width;
        this.height = height;
        updateAfter(context, 0);
    }

    public void startTimer(Context context) {
        runnable = true;
        isPaused = false;
        if (isVideoPlaying) {
            startVideo();
        } else {
            long update = getDelaySeconds(context);
            updateAfter(context, update);
        }
    }

    public void pause() {
        isPaused = true;
        if (isVideoPlaying) {
            pauseVideo();
        }
    }

    public void resume(Context context) {
        isPaused = false;
        if (isVideoPlaying) {
            startVideo();
        } else {
            startTimer(context);
        }
    }

    private void startVideo() {
        if (exoPlayer != null && !exoPlayer.isPlaying()) {
            exoPlayer.play();
            isVideoPlaying = true;
        }
    }

    private void pauseVideo() {
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            // Fix #3: Do not set isVideoPlaying to false when pausing
        }
    }

    public void updateAfter(Context context, long delay) {
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
        if (runnable && !isPaused && !isVideoPlaying) {
            currentTimer = new Timer("CurrentMediaHandlerTimer");
            currentTimer.schedule(new Runner(context), delay < 0 ? 0 : delay * 1000);
        }
    }

    public void stop() {
        Log.d(TAG, "stop() called");
        runnable = false;
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
        if (exoPlayer != null) {
            try {
                Log.d(TAG, "Stopping and releasing ExoPlayer");
                exoPlayer.stop();
                exoPlayer.clearVideoSurface();
                exoPlayer.release();
                exoPlayer = null;
                Log.d(TAG, "ExoPlayer released");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping ExoPlayer", e);
                exoPlayer = null; // Force null even on error
            }
        }
        currentMedia = null;
        isVideoPlaying = false;
        isSurfaceLocked = false;
    }

    public boolean isStarted() {
        return currentTimer != null && runnable;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isVideoPlaying() {
        return isVideoPlaying;
    }

    public boolean isSurfaceLocked() {
        return isSurfaceLocked;
    }

    /**
     * Clear the surface by drawing black screen.
     * This is CRITICAL before video playback to disconnect Canvas from the Surface.
     * MediaCodec requires exclusive Surface access and will fail if Canvas is still
     * connected.
     */
    private void clearSurfaceForVideo() {
        if (surfaceHolder != null) {
            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(android.graphics.Color.BLACK);
                    Log.d(TAG, "Surface cleared for video playback");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing surface", e);
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    public void forceNextMedia(Context context) {
        synchronized (lock) {
            if (runnable) {
                if (currentTimer != null) {
                    currentTimer.cancel();
                }
                currentTimer = new Timer("CurrentMediaHandlerTimer");
                currentTimer.schedule(new Runner(context, Direction.NEXT, true), 0);
            }
        }
    }

    public void forcePreviousMedia(Context context) {
        synchronized (lock) {
            if (runnable) {
                if (currentTimer != null) {
                    currentTimer.cancel();
                }
                currentTimer = new Timer("CurrentMediaHandlerTimer");
                currentTimer.schedule(new Runner(context, Direction.PREVIOUS, true), 0);
            }
        }
    }

    private enum Direction {
        NEXT, PREVIOUS
    }

    private class Runner extends TimerTask {
        private Context context;
        private Direction direction;
        private boolean isForced;

        public Runner(Context context) {
            this(context, Direction.NEXT, false);
        }

        public Runner(Context context, Direction direction, boolean isForced) {
            this.context = context;
            this.direction = direction;
            this.isForced = isForced;
        }

        @Override
        public void run() {
            try {
                loadNewMedia(context, direction, isForced);
            } catch (IOException e) {
                Log.e(TAG, "Error loading media", e);
            }

            if (runnable && !isPaused && (currentMedia == null || !currentMedia.isVideo())) {
                startTimer(context);
            }
        }
    }

    private boolean loadNewMedia(Context context, Direction direction, boolean isForced) throws IOException {
        synchronized (lock) {
            Uri uri = getNextUri(context, direction, isForced);
            boolean result = false;
            if (uri != null) {
                MediaInfo.MediaType type = MediaInfo.determineType(context, uri);
                currentMedia = MediaLoader.loadMedia(uri, context, width, height, type);

                if (currentMedia != null) {
                    boolean isVideo = currentMedia.isVideo();
                    Log.d(TAG, "Loading " + (isVideo ? "video" : "image") + ": " + uri);

                    notifyNextMediaListeners(currentMedia);

                    // Use unified prepareMedia for both types
                    mainHandler.post(() -> prepareMedia(uri, isVideo));

                    result = true;
                }
            }
            return result;
        }
    }

    private Uri getNextUri(Context context, Direction direction, boolean isForced) {
        Uri result = null;
        Resources resources = context.getResources();
        SharedPreferencesManager.Ordering ordering = manager.getCurrentOrdering(resources);
        int countUris = manager.getImageUrisCount();

        if (countUris > 0) {
            int currentMediaIndex = manager.getCurrentIndex();
            if (currentMediaIndex >= countUris) {
                currentMediaIndex = 0;
            }

            if (direction == Direction.PREVIOUS) {
                currentMediaIndex--;
                if (currentMediaIndex < 0) {
                    currentMediaIndex = countUris - 1;
                }
            } else {
                currentMediaIndex++;
                if (currentMediaIndex >= countUris) {
                    currentMediaIndex = 0;
                }
            }

            manager.setCurrentIndex(currentMediaIndex);
            manager.setLastUpdate(System.currentTimeMillis());

            result = manager.getImageUri(currentMediaIndex, ordering);
            currentIndex = currentMediaIndex;
        }

        return result;
    }

    private int getDelaySeconds(Context context) {
        int seconds = 5;
        try {
            seconds = manager.getSecondsBetweenImages();
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid number", e);
        }
        return seconds;
    }
}