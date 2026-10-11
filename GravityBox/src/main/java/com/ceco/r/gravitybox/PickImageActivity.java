/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.r.gravitybox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

import android.Manifest.permission;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.widget.Toast;

import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageActivity;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

public class PickImageActivity extends GravityBoxActivity {

    private static final int REQ_PICK_IMAGE = 1;
    private static final int REQ_CROP_IMAGE = 2;

    public static final String EXTRA_CROP_MODE = "cropMode";
    public static final String EXTRA_ASPECT_X = "aspectX";
    public static final String EXTRA_ASPECT_Y = "aspectY";
    public static final String EXTRA_OUTPUT_X = "outputX";
    public static final String EXTRA_OUTPUT_Y = "outputY";
    public static final String EXTRA_FILE_PATH = "filePath";

    private enum CropMode { ORIGINAL, CROP, ASK }

    private ProgressDialog mProgressDialog;
    private CropMode mCropMode = CropMode.ORIGINAL;
    private Point mAspectSize;
    private Point mOutputSize;
    private AlertDialog mAlertDialog;
    private File mImageFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkSelfPermission(permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_storage_denied, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mProgressDialog = new ProgressDialog(PickImageActivity.this);
        mProgressDialog.setMessage(getString(R.string.lc_please_wait));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);

        Intent startIntent = getIntent();
        if (savedInstanceState == null && startIntent != null) {
            if (startIntent.hasExtra(EXTRA_CROP_MODE)) {
                mCropMode = CropMode.valueOf(startIntent.getStringExtra(EXTRA_CROP_MODE));
            }
            if (startIntent.hasExtra(EXTRA_ASPECT_X) || startIntent.hasExtra(EXTRA_ASPECT_Y)) {
                mAspectSize = new Point(startIntent.getIntExtra(EXTRA_ASPECT_X, 0),
                        startIntent.getIntExtra(EXTRA_ASPECT_Y, 0));
            }
            if (startIntent.hasExtra(EXTRA_OUTPUT_X) || startIntent.hasExtra(EXTRA_OUTPUT_Y)) {
                mOutputSize = new Point(startIntent.getIntExtra(EXTRA_OUTPUT_X, 0),
                        startIntent.getIntExtra(EXTRA_OUTPUT_Y, 0));
            }

            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.imgpick_dialog_title)),
                    REQ_PICK_IMAGE);
        } else {
            finish();
        }
    }

    @Override
    public void onDestroy() {
        dismissProgressDialog();
        dismissAlertDialog();
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                new ImageLoader().execute(data.getData());
            } else {
                sendResult(Activity.RESULT_CANCELED);
            }
        } else if (requestCode == REQ_CROP_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                sendResult(Activity.RESULT_OK);
            } else {
                Toast.makeText(this, R.string.imgpick_crop_uri_null, Toast.LENGTH_LONG).show();
                sendResult(Activity.RESULT_OK);
            }
        }
    }

    private void onImageLoadedResult(final LoadResult result) {
        if (isDestroyed()) {
            return;
        } else if (result.exception != null) {
            Toast.makeText(this, String.format("%s: %s", getString(R.string.imgpick_choose_error),
                    result.exception.getMessage()), Toast.LENGTH_LONG).show();
            sendResult(Activity.RESULT_CANCELED);
            return;
        }

        mImageFile = result.file;
        if (mCropMode == CropMode.ORIGINAL) {
            sendResult(Activity.RESULT_OK);
        } else if (mCropMode == CropMode.CROP) {
            cropImage();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(
                    this, isThemeDark() ? R.style.AlertDialogStyleDark : R.style.AlertDialogStyle))
                .setTitle(R.string.imgpick_crop_ask_title)
                .setMessage(getString(R.string.imgpick_crop_ask_msg, mOutputSize.x, mOutputSize.y))
                .setPositiveButton(R.string.yes, (dialog, which) -> cropImage())
                .setNegativeButton(R.string.no, (dialog, which) -> sendResult(Activity.RESULT_OK));
            mAlertDialog = builder.create();
            mAlertDialog.show();
        }
    }

    private void sendResult(int result) {
        if (result == Activity.RESULT_CANCELED || mImageFile == null) {
            setResult(Activity.RESULT_CANCELED);
        } else {
            setResult(Activity.RESULT_OK,
                        new Intent().putExtra(EXTRA_FILE_PATH, mImageFile.getAbsolutePath()));
        }
        finish();
    }

    private File createFileFromUri(Uri uri) throws Exception {
        File outFile = new File(Utils.getCacheDir(PickImageActivity.this) + "/" + UUID.randomUUID().toString());
        InputStream in = null;
        FileOutputStream out = null;
        BitmapFactory.Options options = null;
        try {
            // downscale to maxWidth/maxHeight of display
            in = getContentResolver().openInputStream(uri);
            options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            options.inSampleSize = BitmapUtils.calculateInSampleSize(
                    options, getMaxWidth(), getMaxHeight());
            options.inJustDecodeBounds = false;
            in.close();

            // save bitmap
            in = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            out = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            bitmap.recycle();
        } finally {
            try { in.close(); } catch (Exception ignored) { }
            try { out.close(); } catch (Exception ignored) { }
        }
        return outFile;
    }

    private int getMaxWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int getMaxHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private void cropImage() {
        try {
            Uri uri = Uri.fromFile(mImageFile);
            CropImageOptions options = new CropImageOptions();
            if (mAspectSize != null) {
                options.fixAspectRatio = true;
                options.aspectRatioX = mAspectSize.x;
                options.aspectRatioY = mAspectSize.y;
            }
            if (mOutputSize != null) {
                options.outputRequestWidth = mOutputSize.x;
                options.outputRequestHeight = mOutputSize.y;
                options.outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE;
            }
            options.customOutputUri = uri;

            Bundle bundle = new Bundle();
            bundle.putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, uri);
            bundle.putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, options);
            Intent intent = new Intent(this, CropImageActivity.class);
            intent.putExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE, bundle);
            startActivityForResult(intent, REQ_CROP_IMAGE);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, String.format("%s: %s", getString(R.string.imgpick_crop_error),
                    e.getMessage()), Toast.LENGTH_LONG).show();
            sendResult(Activity.RESULT_CANCELED);
        }
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    private void dismissAlertDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }

    class LoadResult {
        File file;
        Exception exception;
    }

    class ImageLoader extends AsyncTask<Uri, Integer, LoadResult> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
        }

        @Override
        protected LoadResult doInBackground(Uri... params) {
            LoadResult result = new LoadResult();
            try { 
                result.file = createFileFromUri(params[0]);
            } catch (Exception e) {
                result.exception = e;
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(LoadResult result) {
            dismissProgressDialog();
            onImageLoadedResult(result);
        }
    }
}
