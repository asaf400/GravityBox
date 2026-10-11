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

import com.ceco.r.gravitybox.ledcontrol.LedSettings;
import com.ceco.r.gravitybox.tuner.TunerMainActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.ContextThemeWrapper;
import android.widget.Toast;

public class UnlockActivity extends GravityBoxActivity implements GravityBoxResultReceiver.Receiver {
    private static final String PKG_UNLOCKER = "com.ceco.gravitybox.unlocker";
    private static final String ACTION_CHECK_POLICY = "gravitybox.intent.action.CHECK_POLICY";
    private static final String PERMISSION_UNLOCK = "gravitybox.permission.UNLOCK";
    private static final int UNLOCKER_VERSION_MIN = 23;
    private static final String PREF_KEY_POLICY_CHECK_TIMESTAMP = "policy_check_timestamp";
    private static final long POLICY_CHECK_INTERVAL = 259200000L;

    protected interface CheckPolicyHandler {
        void onPolicyResult(boolean ok);
    } 

    private GravityBoxResultReceiver mReceiver;
    private Handler mHandler;
    private Dialog mAlertDialog;
    private ProgressDialog mProgressDialog;
    private int mDlgThemeId;

    private Runnable mGetSystemPropertiesTimeout = new Runnable() {
        @Override
        public void run() {
            dismissProgressDialog();
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(UnlockActivity.this, mDlgThemeId))
                .setTitle(R.string.app_name)
                .setMessage(R.string.gb_startup_error)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                });
            mAlertDialog = builder.create();
            mAlertDialog.show();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(Utils.getFilesDir(this) + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        mDlgThemeId = file.exists() ? android.R.style.Theme_Material_Dialog :
            android.R.style.Theme_Material_Light_Dialog;
        super.onCreate(savedInstanceState);

        mHandler = new Handler();
        mReceiver = new GravityBoxResultReceiver(mHandler);
        mReceiver.setReceiver(this);
        Intent intent = new Intent();
        intent.setAction(SystemPropertyProvider.ACTION_GET_SYSTEM_PROPERTIES);
        intent.putExtra("receiver", mReceiver);
        intent.putExtra("settings_uuid", SettingsManager.getInstance(this).getOrCreateUuid());
        mProgressDialog = new ProgressDialog(
                new ContextThemeWrapper(UnlockActivity.this, mDlgThemeId));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setTitle(R.string.app_name);
        mProgressDialog.setMessage(getString(R.string.gb_startup_progress));
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        mHandler.postDelayed(mGetSystemPropertiesTimeout, 5000);
        sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mGetSystemPropertiesTimeout);
            mHandler = null;
        }
        mReceiver = null;
        dismissProgressDialog();
        dismissAlertDialog();

        super.onDestroy();
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        if (mHandler != null) {
            mHandler.removeCallbacks(mGetSystemPropertiesTimeout);
            mHandler = null;
        }
        dismissProgressDialog();
        if (resultCode == SystemPropertyProvider.RESULT_SYSTEM_PROPERTIES) {
            dismissProgressDialog();
            Intent intent = new Intent(SystemPropertyProvider.ACTION_REGISTER_UUID);
            intent.putExtra(SystemPropertyProvider.EXTRA_UUID,
                    SettingsManager.getInstance(this).getOrCreateUuid());
            intent.putExtra(SystemPropertyProvider.EXTRA_UUID_TYPE, "Unlocker");
            sendBroadcast(intent);
            LedSettings.lockUnc(this, false);
            TunerMainActivity.lockTuner(this, false);
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(UnlockActivity.this, mDlgThemeId))
                .setTitle(R.string.app_name)
                .setMessage(R.string.premium_unlocked_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                });
            mAlertDialog = builder.create();
            mAlertDialog.show();
        } else {
            finish();
        }
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }

    private void dismissAlertDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
        mAlertDialog = null;
    }

    public static class UnlockReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getParcelableExtra("receiver") instanceof ResultReceiver) {
                ((ResultReceiver)intent.getParcelableExtra("receiver")).send(0, null);
            }
            Intent i = new Intent(context, UnlockActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    public static class UnlockerInstalledReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            maybeRunUnlocker(context);
        }
    }

    protected static void maybeRunUnlocker(Context context) {
        try {
            PackageInfo pkgInfo = context.getPackageManager()
                    .getPackageInfo(PKG_UNLOCKER, 0);
            if (pkgInfo.versionCode < UNLOCKER_VERSION_MIN) {
                Toast.makeText(context, context.getString(R.string.msg_unlocker_old),
                        Toast.LENGTH_LONG).show();
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setPackage(PKG_UNLOCKER);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception e) { 
            e.printStackTrace();
        }
    }

    private static class CheckPolicyReceiver extends BroadcastReceiver {
        private Context mContext;
        private Handler mHandler;
        private CheckPolicyHandler mPolicyHandler;
        private boolean mIsRegistered;

        private Runnable mExpiredRunnable = new Runnable() {
            @Override
            public void run() {
                unregister();
                mPolicyHandler.onPolicyResult(false);
            }
        };

        CheckPolicyReceiver(Context context, CheckPolicyHandler policyHandler) {
            mContext = context;
            mPolicyHandler = policyHandler;
            mHandler = new Handler();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.removeCallbacks(mExpiredRunnable);
            mPolicyHandler.onPolicyResult(true);
            unregister();
            SettingsManager.getInstance(mContext).getMainPrefs().edit()
                .putLong(PREF_KEY_POLICY_CHECK_TIMESTAMP, System.currentTimeMillis())
                .commit();
        }

        private void register() {
            mContext.registerReceiver(this, new IntentFilter(ACTION_CHECK_POLICY),
                    PERMISSION_UNLOCK, null, Context.RECEIVER_EXPORTED);
            mIsRegistered = true;
            mHandler.postDelayed(mExpiredRunnable, 3000);
        }

        private void unregister() {
            if (mIsRegistered) {
                mContext.unregisterReceiver(this);
                mIsRegistered = false;
            }
        }
    }

    private static boolean shouldPerformPolicyCheck(Context context) {
        long lastCheck = SettingsManager.getInstance(context)
                .getMainPrefs().getLong(PREF_KEY_POLICY_CHECK_TIMESTAMP, 0);
        return ((System.currentTimeMillis() - lastCheck) > POLICY_CHECK_INTERVAL);
    }

    protected static void checkPolicyOk(final Context context, final CheckPolicyHandler policyHandler) {
        CheckPolicyReceiver receiver = null;
        try {
            PackageInfo pkgInfo = context.getPackageManager()
                    .getPackageInfo(PKG_UNLOCKER, 0);
            if (pkgInfo.versionCode < UNLOCKER_VERSION_MIN) {
                policyHandler.onPolicyResult(false);
                Toast.makeText(context, context.getString(R.string.msg_unlocker_old),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (shouldPerformPolicyCheck(context)) {
                receiver = new CheckPolicyReceiver(context, policyHandler);
                Intent intent = new Intent(ACTION_CHECK_POLICY);
                intent.setComponent(new ComponentName(pkgInfo.packageName,
                        pkgInfo.packageName+".CheckPolicyService"));
                receiver.register();
                context.startForegroundService(intent);
            } else {
                policyHandler.onPolicyResult(true);
            }
        } catch (NameNotFoundException nnfe) {
            policyHandler.onPolicyResult(false);
            Toast.makeText(context, context.getString(R.string.msg_unlocker_missing),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (receiver != null) {
                receiver.unregister();
            }
            policyHandler.onPolicyResult(false);
            Toast.makeText(context,
                    String.format("%s: %s",
                    context.getString(R.string.msg_unlocker_error),
                    e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
}
