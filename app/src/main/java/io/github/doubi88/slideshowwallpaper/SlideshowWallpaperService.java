package io.github.doubi88.slideshowwallpaper;

import android.app.WallpaperColors;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.os.Bundle;
import android.app.WallpaperManager;


import androidx.annotation.RequiresApi;

import java.io.IOException;

import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager;
import io.github.doubi88.slideshowwallpaper.utilities.CurrentImageHandler;
import io.github.doubi88.slideshowwallpaper.utilities.ImageInfo;
import io.github.doubi88.slideshowwallpaper.utilities.ImageLoader;


public class SlideshowWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new SlideshowWallpaperEngine();
    }


    public class SlideshowWallpaperEngine extends Engine {

        private Handler handler;
        private CurrentImageHandler currentImageHandler;
        private int width;
        private int height;
        private Paint imagePaint;
        private float deltaX;
        private boolean isScrolling = false;
        private SharedPreferencesManager manager;
        private GestureDetector gestureDetector;
        private MediaPlayer mediaPlayer;
        private boolean surfaceReady = false;

        public SlideshowWallpaperEngine() {
            SharedPreferences prefs = getSharedPreferences();
            manager = new SharedPreferencesManager(prefs);
            handler = new Handler(Looper.getMainLooper());
            imagePaint = new Paint();
            if (manager.getAntiAlias()) {
                imagePaint.setAntiAlias(true);
            }
            initializeGestureDetector();
        }

        private void initializeGestureDetector() {
            gestureDetector = new GestureDetector(getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
                private static final int SWIPE_THRESHOLD_VELOCITY = 200;

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (manager.getSwipeToChange()) {
                        if (e1.getX() - e2.getX() > 50 && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                            currentImageHandler.forceNextImage(getApplicationContext());
                        } else if (e2.getX() - e1.getX() > 50 && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                            currentImageHandler.forcePreviousImage(getApplicationContext());
                        }
                    }
                    return super.onFling(e1, e2, velocityX, velocityY);
                }
            });
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            this.surfaceReady = true;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;
            this.height = height;

            if (currentImageHandler == null) {
                currentImageHandler = new CurrentImageHandler(manager, width, height);
                currentImageHandler.addNextImageListener(this::displayCurrentImage);
                currentImageHandler.updateAfter(getApplicationContext(), 0); // Load first image
            } else {
                currentImageHandler.setDimensions(width, height, getApplicationContext());
                displayCurrentImage(currentImageHandler.getCurrentImage());
            }
        }

        private void displayCurrentImage(ImageInfo imageInfo) {
            handler.post(() -> {
                if (!surfaceReady) return;

                if (imageInfo != null && imageInfo.getUri() != null) {
                    if (isVideoUri(imageInfo.getUri())) {
                        playVideo(imageInfo.getUri());
                    } else {
                        drawImage(imageInfo);
                    }
                } else {
                    drawBlackScreen();
                }
            });
        }

        private boolean isVideoUri(Uri uri) {
            String mimeType = getContentResolver().getType(uri);
            return mimeType != null && mimeType.startsWith("video/");
        }

        private void playVideo(Uri uri) {
            currentImageHandler.stop();
            // Release the current player immediately to stop video playback instantly.
            releaseMediaPlayer();

            // Post the creation and preparation of the new player to the handler with a small delay.
            // This ensures a clean separation and gives the system time to release the surface after canvas drawing, preventing race conditions.
            handler.postDelayed(() -> {
                // Check if the surface is still valid, as time has passed and the wallpaper might have been hidden.
                if (!surfaceReady) return;

                mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(getApplicationContext(), uri);
                    mediaPlayer.setSurface(getSurfaceHolder().getSurface());
                    mediaPlayer.setLooping(false);

                    mediaPlayer.setOnPreparedListener(mp -> {
                        if (!surfaceReady) return; // Re-check surface validity before starting.
                        if (manager.getMuteVideos()) {
                            mp.setVolume(0f, 0f);
                        } else {
                            mp.setVolume(1f, 1f);
                        }
                        mp.start();
                    });

                    mediaPlayer.setOnCompletionListener(mp -> {
                        handler.post(() -> {
                            releaseMediaPlayer();
                            currentImageHandler.updateAfter(getApplicationContext(), 0);
                        });
                    });

                    mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        Log.e("VIDEO_PLAYBACK", "MediaPlayer error: what=" + what + ", extra=" + extra);
                        handler.post(() -> {
                            releaseMediaPlayer();
                            currentImageHandler.forceNextImage(getApplicationContext());
                        });
                        return true;
                    });
                    
                    mediaPlayer.prepareAsync();

                } catch (Exception e) {
                    Log.e("VIDEO_PLAYBACK", "Error setting up video playback", e);
                    handler.post(() -> {
                        releaseMediaPlayer();
                        currentImageHandler.forceNextImage(getApplicationContext());
                    });
                }
            }, 100); // 100ms delay
        }

        private void drawImage(ImageInfo image) {
            releaseMediaPlayer();
            if (currentImageHandler != null && !currentImageHandler.isStarted()) {
                currentImageHandler.startTimer(getApplicationContext());
            }

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    if (image != null && image.getImage() != null) {
                        Bitmap bitmap = image.getImage();
                        // ... (drawing logic remains the same)
                        SharedPreferencesManager.TooWideImagesRule rule = manager.getTooWideImagesRule(getResources());
                        boolean antiAlias = manager.getAntiAlias();
                        boolean antiAliasScrolling = manager.getAntiAliasWhileScrolling();
                        imagePaint.setAntiAlias(antiAlias && (!isScrolling || antiAliasScrolling));

                        if (rule == SharedPreferencesManager.TooWideImagesRule.SCALE_DOWN) {
                            canvas.drawBitmap(bitmap, ImageLoader.calculateMatrixScaleToFit(bitmap, width, height, true), imagePaint);
                        } else {
                            canvas.save();
                            canvas.translate(deltaX, 0);
                            canvas.drawBitmap(bitmap, ImageLoader.calculateMatrixScaleToFit(bitmap, width, height, false), imagePaint);
                            canvas.restore();
                        }
                    } else {
                        canvas.drawColor(Color.BLACK);
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(SlideshowWallpaperService.class.getSimpleName(), "Error unlocking canvas", e);
                    }
                }
            }
        }
        
        private void drawBlackScreen() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK);
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(SlideshowWallpaperService.class.getSimpleName(), "Error unlocking canvas", e);
                    }
                }
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            surfaceReady = false;
            if (currentImageHandler != null) {
                currentImageHandler.stop();
            }
            releaseMediaPlayer();
        }

        private void releaseMediaPlayer() {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.reset();
                    mediaPlayer.release();
                } catch (Exception e) {
                    Log.e("VIDEO_PLAYBACK", "Error during MediaPlayer release", e);
                } finally {
                    mediaPlayer = null;
                }
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                if (currentImageHandler != null) {
                    currentImageHandler.startTimer(getApplicationContext());
                }
            } else {
                if (currentImageHandler != null) {
                    currentImageHandler.stop();
                }
            }
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            if (currentImageHandler != null && currentImageHandler.getCurrentImage() != null && currentImageHandler.getCurrentImage().getImage() != null) {
                Bitmap image = currentImageHandler.getCurrentImage().getImage();
                deltaX = calculateDeltaX(image, xOffset, xOffsetStep);
                isScrolling = (Math.floor(xOffset) != xOffset);
                displayCurrentImage(currentImageHandler.getCurrentImage());
            }
        }

        private float calculateDeltaX(Bitmap image, float xOffset, float xOffsetStep) {
            int width = image.getWidth();
            float result = 0;
            SharedPreferencesManager.TooWideImagesRule rule = manager.getTooWideImagesRule(getResources());
            float scale = ImageLoader.calculateScaleFactorToFit(image, this.width, this.height, rule == SharedPreferencesManager.TooWideImagesRule.SCALE_DOWN);
            width = Math.round(width * scale);
            if (width > this.width) {
                if (rule == SharedPreferencesManager.TooWideImagesRule.SCALE_UP) {
                    xOffset = 0.5f;
                } else if (rule == SharedPreferencesManager.TooWideImagesRule.SCROLL_BACKWARD) {
                    xOffset = 1 - xOffset;
                }
                result = -xOffset * (width - this.width);
            }
            return result;
        }

        private SharedPreferences getSharedPreferences() {
            return SlideshowWallpaperService.this.getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        }
        
        @Override
        public void onTouchEvent(MotionEvent event) {
            gestureDetector.onTouchEvent(event);
        }
    }
}
        
