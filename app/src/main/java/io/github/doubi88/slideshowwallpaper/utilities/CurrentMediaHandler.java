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
import android.media.MediaPlayer;
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

    private MediaPlayer mediaPlayer;
    private boolean isVideoPlaying = false;

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

    private void initializeMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            Log.d(TAG, "MediaPlayer created");

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Video completed");
                isVideoPlaying = false;
                if (runnable && !isPaused) {
                    forceNextMedia(context);
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Video error: " + what + ", " + extra);
                isVideoPlaying = false;
                if (runnable && !isPaused) {
                    forceNextMedia(context);
                }
                return true; // We handled the error
            });

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "Video prepared");
                if (!isPaused) {
                    startVideo();
                }
            });
        }
    }

    private void prepareVideo(Uri uri) {
        if (surfaceHolder == null || !surfaceHolder.getSurface().isValid()) {
            Log.e(TAG, "Surface is not valid, cannot prepare video.");
            return;
        }

        initializeMediaPlayer(); // Ensures player is not null

        try {
            mediaPlayer.reset();
            Log.d(TAG, "MediaPlayer reset");

            // Fix #2: Apply mute setting
            if (manager.getMuteVideos()) {
                mediaPlayer.setVolume(0f, 0f);
            } else {
                mediaPlayer.setVolume(1f, 1f);
            }

            mediaPlayer.setSurface(surfaceHolder.getSurface());
            Log.d(TAG, "setSurface done");

            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                long size = pfd.getStatSize();
                mediaPlayer.setDataSource(pfd.getFileDescriptor(), 0, size);
                pfd.close();
                Log.d(TAG, "setDataSource with size: " + size);
            } else {
                throw new IOException("FileDescriptor is null for uri: " + uri);
            }
            
            mediaPlayer.prepareAsync();
            Log.d(TAG, "prepareAsync called");

        } catch (Exception e) {
            Log.e(TAG, "Error preparing video", e);
            if (runnable && !isPaused) {
                forceNextMedia(context);
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
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isVideoPlaying = true;
        }
    }

    private void pauseVideo() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
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
        runnable = false;
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            Log.d(TAG, "MediaPlayer released");
        }
        currentMedia = null;
        isVideoPlaying = false;
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
                boolean updated = loadNewMedia(context, direction, isForced);
                if (updated) {
                    notifyNextMediaListeners(currentMedia);
                }
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

                // Fix #1: Release player when switching from video to image
                if (currentMedia != null && currentMedia.isVideo() && type == MediaInfo.MediaType.IMAGE) {
                    mainHandler.post(() -> {
                        if (mediaPlayer != null) {
                            mediaPlayer.release();
                            mediaPlayer = null;
                            Log.d(TAG, "MediaPlayer RELEASED for image transition.");
                        }
                    });
                    isVideoPlaying = false;
                }
                
                currentMedia = MediaLoader.loadMedia(uri, context, width, height, type);

                if (currentMedia.isVideo()) {
                    Log.d(TAG, "Loading video: " + uri);
                    mainHandler.post(() -> prepareVideo(uri));
                } else {
                    Log.d(TAG, "Loading image: " + uri);
                }
                result = true;
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