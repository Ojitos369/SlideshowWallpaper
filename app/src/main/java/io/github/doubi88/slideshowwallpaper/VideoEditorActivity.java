package io.github.doubi88.slideshowwallpaper;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

// import com.arthenica.mobileffmpeg.Config;
// import com.arthenica.mobileffmpeg.FFmpeg;
import com.google.android.material.slider.RangeSlider;

import java.io.File;
import java.util.List;

public class VideoEditorActivity extends AppCompatActivity {

    private static final String TAG = "VideoEditorActivity";
    public static final String EXTRA_VIDEO_URI = "extra_video_uri";
    public static final String RESULT_VIDEO_URI = "result_video_uri";

    private PlayerView playerView;
    private RangeSlider rangeSlider;
    private Button btnSave;
    private Button btnCancel;

    private ExoPlayer exoPlayer;
    private Uri videoUri;
    private long durationMs;
    private float startTrim = 0f;
    private float endTrim = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_video_editor);

        videoUri = getIntent().getParcelableExtra(EXTRA_VIDEO_URI);
        if (videoUri == null) {
            Log.e(TAG, "Video URI is null");
            Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Log.d(TAG, "Video URI: " + videoUri);

        playerView = findViewById(R.id.player_view);
        rangeSlider = findViewById(R.id.range_slider);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);

        initializePlayer();
        setupListeners();
    }

    private void initializePlayer() {
        Log.d(TAG, "initializePlayer");

        try {
            exoPlayer = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(exoPlayer);

            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        durationMs = exoPlayer.getDuration();
                        setupRangeSlider();
                    }
                }

                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error in Editor", error);
                    runOnUiThread(() -> {
                        Toast.makeText(VideoEditorActivity.this,
                                "Error playing video: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        // Don't finish immediately, let user try to save or cancel
                    });
                }
            });

            // Start playback
            exoPlayer.play();

        } catch (Exception e) {
            Log.e(TAG, "Fatal error during player initialization", e);
            Toast.makeText(this, "Cannot initialize video player: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupRangeSlider() {
        if (durationMs > 0) {
            rangeSlider.setValueFrom(0f);
            rangeSlider.setValueTo((float) durationMs / 1000f); // Seconds
            rangeSlider.setValues(0f, (float) durationMs / 1000f);
            endTrim = (float) durationMs / 1000f;
        }
    }

    private void setupListeners() {
        rangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            startTrim = values.get(0);
            endTrim = values.get(1);

            // Seek to start if user is adjusting start
            if (fromUser) {
                exoPlayer.seekTo((long) (startTrim * 1000));
            }
        });

        btnCancel.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> saveVideo());
    }

    private void saveVideo() {
        if (videoUri == null)
            return;

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Processing video...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        File outputFile = new File(getExternalCacheDir(), "edited_" + System.currentTimeMillis() + ".mp4");
        String outputPath = outputFile.getAbsolutePath();

        // Basic trim command
        // -ss start -to end -i input -c copy output
        // Note: -ss before -i is faster but less accurate. -ss after -i is accurate.
        // For trimming, we usually want accuracy.

        // We need the file path from URI. This is tricky with Content URIs.
        // For now, let's assume we can pass the URI string if ffmpeg-kit supports it,
        // or we need a helper to get the path.
        // FFmpegKit supports Content URIs on Android!

        String command = String.format("-i \"%s\" -ss %f -to %f -c:v libx264 -c:a copy \"%s\"",
                videoUri.toString(), startTrim, endTrim, outputPath);

        Log.d(TAG, "FFmpeg command: " + command);

        // FFmpeg.executeAsync(command, (executionId, returnCode) -> {
        // new Handler(Looper.getMainLooper()).post(() -> {
        // progressDialog.dismiss();
        // if (returnCode == Config.RETURN_CODE_SUCCESS) {
        // Toast.makeText(VideoEditorActivity.this, "Video saved!",
        // Toast.LENGTH_SHORT).show();
        // Intent resultIntent = new Intent();
        // resultIntent.putExtra(RESULT_VIDEO_URI, Uri.fromFile(outputFile));
        // setResult(RESULT_OK, resultIntent);
        // finish();
        // } else {
        // Toast.makeText(VideoEditorActivity.this, "Error saving video",
        // Toast.LENGTH_SHORT).show();
        // Log.e(TAG, "FFmpeg failed with return code: " + returnCode);
        // }
        // });
        // });
        progressDialog.dismiss();
        Toast.makeText(this, "FFmpeg library missing. Cannot save video.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            Log.d(TAG, "Player paused in onPause");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (exoPlayer != null && !exoPlayer.isPlaying()) {
            exoPlayer.play();
            Log.d(TAG, "Player resumed in onResume");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy - releasing player");
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                exoPlayer.clearVideoSurface();
                exoPlayer.release();
                exoPlayer = null;
                Log.d(TAG, "ExoPlayer released successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing ExoPlayer in onDestroy", e);
            }
        }
    }
}
