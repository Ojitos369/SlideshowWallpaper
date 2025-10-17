/*
 * Slideshow Wallpaper: An Android live wallpaper displaying custom images.
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
package io.github.doubi88.slideshowwallpaper.preferences.imageList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import io.github.doubi88.slideshowwallpaper.R;
import io.github.doubi88.slideshowwallpaper.listeners.OnCropListener;
import io.github.doubi88.slideshowwallpaper.listeners.OnSelectListener;
import io.github.doubi88.slideshowwallpaper.preferences.SharedPreferencesManager;
import io.github.doubi88.slideshowwallpaper.utilities.ImageInfo;

public class ImageListActivity extends AppCompatActivity implements OnCropListener {

    private SharedPreferencesManager manager;
    private static final int REQUEST_CODE_FILE = 1;

    private ImageListAdapter imageListAdapter;

    private FloatingActionButton removeButton;
    private Uri originalUri;

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private ActivityResultLauncher<PickVisualMediaRequest> launcher = null;

    public ImageListActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            this.launcher = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(), this::imagePickerCallback);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_list);

        this.removeButton = findViewById(R.id.delete_button);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        RecyclerView recyclerView = findViewById(R.id.image_list);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(layoutManager);
        manager = new SharedPreferencesManager(getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE));

        List<Uri> uris = manager.getImageUris(SharedPreferencesManager.Ordering.SELECTION);

        imageListAdapter = new ImageListAdapter(uris);
        imageListAdapter.setOnCropListener(this);
        imageListAdapter.addOnSelectListener(new OnSelectListener() {
            @Override
            public void onImageSelected(ImageInfo info) {

            }

            @Override
            public void onImagedDeselected(ImageInfo info) {

            }

            @Override
            public void onSelectionChanged(HashSet<ImageInfo> setInfo) {
                Log.d(ImageListActivity.class.getSimpleName(), setInfo.size() + " image(s) selected");
                if (setInfo.size() > 0) {
                    removeButton.setVisibility(View.VISIBLE);
                } else {
                    removeButton.setVisibility(View.GONE);
                }
            }
        });
        recyclerView.setAdapter(imageListAdapter);

        findViewById(R.id.add_button).setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            } else {
                intent.setAction(Intent.ACTION_GET_CONTENT);
            }
            startActivityForResult(intent, REQUEST_CODE_FILE);
        });

        this.removeButton.setOnClickListener(view -> {
            HashSet<ImageInfo> selectedImages = imageListAdapter.getSelectedImages();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.remove_confirmation_title));
            builder.setMessage(getResources().getQuantityString(R.plurals.remove_confirmation_message, selectedImages.size(), selectedImages.size()));
            builder.setPositiveButton(getString(R.string.positive_action_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    for (ImageInfo imageInfo : selectedImages) {
                        manager.removeUri(imageInfo.getUri());
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && imageInfo.getSize() > 0 && !manager.hasImageUri(imageInfo.getUri())) {
                            getContentResolver().releasePersistableUriPermission(imageInfo.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                    }
                    imageListAdapter.delete(selectedImages);
                }
            });

            builder.setNegativeButton(getString(R.string.cancel_action_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            // Create and display the AlertDialog
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void imagePickerCallback(List<Uri> uris) {
        List<Uri> urisToAdd = new ArrayList<>(uris.size());
        for (Uri uri : uris) {
            boolean takePermissionSuccess = takePermission(uri);
            if (takePermissionSuccess && manager.addUri(uri)) {
                urisToAdd.add(uri);
            }
        }
        imageListAdapter.addUris(urisToAdd);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            final Uri resultUri = UCrop.getOutput(data);
            if (originalUri != null && resultUri != null) {
                manager.removeUri(originalUri);
                manager.addUri(resultUri);
                imageListAdapter.updateUri(originalUri, resultUri);
            }
            originalUri = null;
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            Log.e(ImageListActivity.class.getSimpleName(), "Crop error: " + cropError);
        }

        List<Uri> uris = new LinkedList<>();
        if (requestCode == REQUEST_CODE_FILE && resultCode == Activity.RESULT_OK) {
            ClipData clipData = data.getClipData();
            if (clipData == null) {
                Uri uri = data.getData();
                if (uri != null) {
                    boolean takePermissionSuccess = takePermission(uri);
                    if (takePermissionSuccess && manager.addUri(uri)) {
                        uris.add(uri);
                    }
                }
            } else {
                for (int index = 0; index < clipData.getItemCount(); index++) {
                    Uri uri = clipData.getItemAt(index).getUri();
                    boolean takePermissionSuccess = takePermission(uri);
                    if (takePermissionSuccess && manager.addUri(uri)) {
                        uris.add(uri);
                    }
                }
            }

            imageListAdapter.addUris(uris);
        }
    }

    private boolean takePermission(Uri uri) {
        boolean takePermissionSuccess = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ContentResolver res = getContentResolver();
            int perms = res.getPersistedUriPermissions().size();
            res.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // If taking the permission was unsuccessful (e.g. because the limit was reached), Don't add the uri
            if (res.getPersistedUriPermissions().size() <= perms) {
                takePermissionSuccess = false;
            }
        }
        return takePermissionSuccess;
    }

    @Override
    public void onCrop(Uri uri) {
        this.originalUri = uri;
        UCrop.Options options = new UCrop.Options();
        options.setAspectRatioOptions(0, new com.yalantis.ucrop.model.AspectRatio("9:16", 9, 16),
                new com.yalantis.ucrop.model.AspectRatio("16:9", 16, 9),
                new com.yalantis.ucrop.model.AspectRatio("4:3", 4, 3),
                new com.yalantis.ucrop.model.AspectRatio("3:4", 3, 4),
                new com.yalantis.ucrop.model.AspectRatio("1:1", 1, 1));

        options.setToolbarColor(ContextCompat.getColor(this, R.color.primaryColor));
        options.setStatusBarColor(ContextCompat.getColor(this, R.color.primaryDarkColor));
        options.setActiveControlsWidgetColor(ContextCompat.getColor(this, R.color.primaryColor));

        String destinationFileName = "croppedImage" + System.currentTimeMillis() + ".jpg";
        UCrop.of(uri, Uri.fromFile(new File(getCacheDir(), destinationFileName)))
                .withOptions(options)
                .start(this);
    }
}