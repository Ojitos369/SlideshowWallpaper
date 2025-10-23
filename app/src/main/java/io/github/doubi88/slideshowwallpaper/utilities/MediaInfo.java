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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;

public class MediaInfo {
    public enum MediaType {
        IMAGE,
        VIDEO
    }

    private Uri uri;
    private String name;
    private int size;
    private Bitmap image;
    private MediaType type;
    private MediaPlayer mediaPlayer;
    private SurfaceTexture surfaceTexture;
    private Surface surface;

    public MediaInfo(Uri uri, String name, int size, Bitmap image, MediaType type) {
        this.uri = uri;
        this.name = name;
        this.size = size;
        this.image = image;
        this.type = type;
    }

    public Uri getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public Bitmap getImage() {
        return image;
    }

    public void setImage(Bitmap image) {
        this.image = image;
    }

    public MediaType getType() {
        return type;
    }

    public void prepareVideo(Context context, MediaPlayer.OnCompletionListener onCompletionListener) {
        if (type == MediaType.VIDEO) {
            try {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                }
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(context, uri);
                
                // Create surface texture for video playback
                if (surfaceTexture != null) {
                    surfaceTexture.release();
                }
                if (surface != null) {
                    surface.release();
                }
                
                surfaceTexture = new SurfaceTexture(0);
                surface = new Surface(surfaceTexture);
                mediaPlayer.setSurface(surface);
                
                mediaPlayer.setOnCompletionListener(onCompletionListener);
                mediaPlayer.prepare();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void startVideo() {
        if (type == MediaType.VIDEO && mediaPlayer != null) {
            try {
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void pauseVideo() {
        if (type == MediaType.VIDEO && mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopVideo() {
        if (type == MediaType.VIDEO && mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.reset();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
    }

    public boolean isVideo() {
        return type == MediaType.VIDEO;
    }

    @Override
    protected void finalize() throws Throwable {
        release();
        super.finalize();
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = false;
        if ((obj != null) && (obj instanceof MediaInfo)) {
            result = ((obj == this) || (((MediaInfo) obj).getUri().equals(getUri())));
        }
        return result;
    }

    @Override
    public int hashCode() {
        return getUri().hashCode();
    }

    public static MediaType determineType(Context context, Uri uri) {
        ContentResolver cr = context.getContentResolver();
        String mimeType = cr.getType(uri);
        if (mimeType != null) {
            if (mimeType.startsWith("video/")) {
                return MediaType.VIDEO;
            } else if (mimeType.startsWith("image/")) {
                return MediaType.IMAGE;
            }
        }
        // Default to image if can't determine
        return MediaType.IMAGE;
    }
}