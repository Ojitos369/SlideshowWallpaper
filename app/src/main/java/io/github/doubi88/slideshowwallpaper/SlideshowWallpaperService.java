package io.github.doubi88.slideshowwallpaper;

import android.app.WallpaperColors;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.util.Log;

import androidx.annotation.RequiresApi;

import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager;
import io.github.doubi88.slideshowwallpaper.utilities.CurrentMediaHandler;
import io.github.doubi88.slideshowwallpaper.utilities.MediaInfo;

/**
 * Clean SlideshowWallpaperService implementation.
 * This file has been replaced to remove corrupted/duplicated fragments.
 */
public class SlideshowWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new SlideshowWallpaperEngine();
    }

    private class SlideshowWallpaperEngine extends Engine {
        private static final String TAG = "SlideshowWallpaperEngine";
        private final Handler handler = new Handler(Looper.getMainLooper());
        private CurrentMediaHandler currentMediaHandler;
        private int width = 0;
        private int height = 0;
        // Removed imagePaint - no longer using Canvas rendering
        private final SharedPreferencesManager manager;
        private GestureDetector gestureDetector;
        private boolean surfaceReady = false;

        SlideshowWallpaperEngine() {
            SharedPreferences prefs = SlideshowWallpaperService.this
                    .getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
            manager = new SharedPreferencesManager(prefs);
            // Removed imagePaint initialization - ExoPlayer handles all rendering
            initGestureDetector();
        }

        private void initGestureDetector() {
            gestureDetector = new GestureDetector(getApplicationContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        private static final int SWIPE_THRESHOLD_VELOCITY = 200;

                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                            if (manager.getSwipeToChange() && currentMediaHandler != null) {
                                if (e1.getX() - e2.getX() > 50 && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                                    currentMediaHandler.forceNextMedia(getApplicationContext());
                                    return true;
                                } else if (e2.getX() - e1.getX() > 50
                                        && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                                    currentMediaHandler.forcePreviousMedia(getApplicationContext());
                                    return true;
                                }
                            }
                            return super.onFling(e1, e2, velocityX, velocityY);
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            if (currentMediaHandler != null && currentMediaHandler.getCurrentMedia() != null
                                    && currentMediaHandler.getCurrentMedia().isVideo()) {
                                if (currentMediaHandler.isPaused())
                                    currentMediaHandler.resume(getApplicationContext());
                                else
                                    currentMediaHandler.pause();
                                return true;
                            }
                            return super.onDoubleTap(e);
                        }
                    });
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            if (gestureDetector != null)
                gestureDetector.onTouchEvent(event);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "onSurfaceCreated");
            surfaceReady = true;
            if (currentMediaHandler != null) {
                currentMediaHandler.updateSurface(holder);
            }
            if (isVisible() && currentMediaHandler != null) {
                currentMediaHandler.resume(getApplicationContext());
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "onSurfaceDestroyed");
            surfaceReady = false;
            if (currentMediaHandler != null)
                currentMediaHandler.stop();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;
            this.height = height;
            if (currentMediaHandler == null) {
                currentMediaHandler = new CurrentMediaHandler(manager, width, height, getApplicationContext(),
                        getSurfaceHolder());
                currentMediaHandler.addNextMediaListener(this::displayCurrentMedia);
                currentMediaHandler.updateAfter(getApplicationContext(), 0);
            } else {
                currentMediaHandler.updateSurface(holder);
                currentMediaHandler.setDimensions(width, height, getApplicationContext());
                displayCurrentMedia(currentMediaHandler.getCurrentMedia());
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d(TAG, "onVisibilityChanged: " + visible);
            if (currentMediaHandler != null) {
                if (visible && surfaceReady)
                    currentMediaHandler.resume(getApplicationContext());
                else
                    currentMediaHandler.pause();
            }
        }

        private void displayCurrentMedia(MediaInfo media) {
            // ExoPlayer handles all rendering now (images and videos)
            // No Canvas drawing needed!
            // Just notify colors for system integration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                handler.post(this::notifyColorsChanged);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.O_MR1)
        @Override
        public WallpaperColors onComputeColors() {
            if (currentMediaHandler != null && currentMediaHandler.getCurrentMedia() != null) {
                Bitmap bitmap = currentMediaHandler.getCurrentMedia().getImage();
                if (bitmap != null) {
                    return WallpaperColors.fromBitmap(bitmap);
                }
            }
            return super.onComputeColors();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (currentMediaHandler != null)
                currentMediaHandler.stop();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                int xPixelOffset, int yPixelOffset) {
            // ExoPlayer handles all rendering - no scrolling offset needed
            // Images/videos are rendered by ExoPlayer which handles scaling automatically
        }
    }
}