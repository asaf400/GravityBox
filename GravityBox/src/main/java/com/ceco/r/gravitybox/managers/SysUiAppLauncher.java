/*
 * Copyright (C) 2021 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.r.gravitybox.managers;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import com.ceco.r.gravitybox.BitmapUtils;
import com.ceco.r.gravitybox.ColorUtils;
import com.ceco.r.gravitybox.ModStatusBar;
import com.ceco.r.gravitybox.R;
import com.ceco.r.gravitybox.GravityBox;
import com.ceco.r.gravitybox.GravityBoxSettings;
import com.ceco.r.gravitybox.Utils;
import com.ceco.r.gravitybox.preference.AppPickerPreference;
import com.ceco.r.gravitybox.shortcuts.ShortcutActivity;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class SysUiAppLauncher implements BroadcastMediator.Receiver, SysUiConfigChangeMonitor.ConfigChangeListener {
    private static final String TAG = "GB:AppLauncher";
    private static final boolean DEBUG = false;

    public static final String ACTION_SHOW_APP_LAUCNHER = "gravitybox.intent.action.SHOW_APP_LAUNCHER";

    enum DialogTheme { DEFAULT, LIGHT, DARK }

    private final Context mContext;
    private Context mGbContext;
    private final Resources mResources;
    private Resources mGbResources;
    private Dialog mDialog;
    private final Handler mHandler;
    private final PackageManager mPm;
    private final List<AppInfo> mAppSlots;
    private final XSharedPreferences mPrefs;
    private DialogTheme mDialogTheme;
    private boolean mIsFirstShow = true;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private final Runnable mDismissAppDialogRunnable = this::dismissDialog;

    private final BroadcastReceiver mPackageRemoveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());

            Uri data = intent.getData();
            String pkgName = data == null ? null : data.getSchemeSpecificPart();
            if (pkgName != null) {
                for (AppInfo ai : mAppSlots) {
                    if (pkgName.equals(ai.getPackageName())) {
                        ai.initAppInfo(null);
                        if (DEBUG) log("Removed package: " + pkgName);
                    }
                }
            }
        }
    };

    public SysUiAppLauncher(Context context, XSharedPreferences prefs) throws Throwable {
        mContext = context;
        mResources = mContext.getResources();
        mPrefs = prefs;
        mGbContext = Utils.getGbContext(mContext, mResources.getConfiguration());
        mGbResources = mGbContext.getResources();
        mHandler = new Handler();
        mPm = mContext.getPackageManager();

        mDialogTheme = DialogTheme.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_APP_LAUNCHER_THEME, "DEFAULT"));

        mAppSlots = new ArrayList<>();
        mAppSlots.add(new AppInfo(R.id.quickapp1));
        mAppSlots.add(new AppInfo(R.id.quickapp2));
        mAppSlots.add(new AppInfo(R.id.quickapp3));
        mAppSlots.add(new AppInfo(R.id.quickapp4));
        mAppSlots.add(new AppInfo(R.id.quickapp5));
        mAppSlots.add(new AppInfo(R.id.quickapp6));
        mAppSlots.add(new AppInfo(R.id.quickapp7));
        mAppSlots.add(new AppInfo(R.id.quickapp8));
        mAppSlots.add(new AppInfo(R.id.quickapp9));
        mAppSlots.add(new AppInfo(R.id.quickapp10));
        mAppSlots.add(new AppInfo(R.id.quickapp11));
        mAppSlots.add(new AppInfo(R.id.quickapp12));

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intentFilter.addDataScheme("package");
        mContext.registerReceiver(mPackageRemoveReceiver, intentFilter, Context.RECEIVER_EXPORTED);

        SysUiManagers.BroadcastMediator.subscribe(this,
                GravityBoxSettings.ACTION_PREF_APP_LAUNCHER_CHANGED,
                ACTION_SHOW_APP_LAUCNHER);
    }

    @Override
    public void onDensityDpiChanged(Configuration config) {
        try {
            mGbContext = Utils.getGbContext(mContext, config);
            mGbResources = mGbContext.getResources();
            mIsFirstShow = true;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (DEBUG) log("Broadcast received: " + intent.toString());
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_APP_LAUNCHER_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_APP_LAUNCHER_SLOT) &&
                    intent.hasExtra(GravityBoxSettings.EXTRA_APP_LAUNCHER_APP)) {
                int slot = intent.getIntExtra(GravityBoxSettings.EXTRA_APP_LAUNCHER_SLOT, -1);
                String app = intent.getStringExtra(GravityBoxSettings.EXTRA_APP_LAUNCHER_APP);
                if (DEBUG) log("appSlot=" + slot + "; app=" + app);
                updateAppSlot(slot, app);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_APP_LAUNCHER_THEME)) {
                mDialogTheme = DialogTheme.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_APP_LAUNCHER_THEME));
            }
        }
        if (intent.getAction().equals(ACTION_SHOW_APP_LAUCNHER)) {
            showDialog();
        }
    }

    private boolean dismissDialog() {
        boolean dismissed = false;
        mHandler.removeCallbacks(mDismissAppDialogRunnable);
        if (mDialog != null) {
            if (mDialog.isShowing()) {
                mDialog.dismiss();
                dismissed = true;
            }
            mDialog = null;
        }
        return dismissed;
    }

    private ContextThemeWrapper getThemedContext(int defaultThemeResId) {
        int resId = mResources.getIdentifier("qs_theme", "style", mContext.getPackageName());
        if (resId == 0) {
            resId = defaultThemeResId;
        }
        return new ContextThemeWrapper(mContext, resId);
    }

    @SuppressLint("InflateParams")
    public void showDialog() {
        try {
            if (dismissDialog()) {
                return;
            }

            if (mIsFirstShow) {
                mPrefs.reload();
                for (int i = 0; i < GravityBoxSettings.PREF_KEY_APP_LAUNCHER_SLOT.size(); i++) {
                    updateAppSlot(i, mPrefs.getString(
                            GravityBoxSettings.PREF_KEY_APP_LAUNCHER_SLOT.get(i), null));
                }
                mIsFirstShow = false;
            }

            LayoutInflater inflater = LayoutInflater.from(mGbContext);
            View appView = inflater.inflate(R.layout.navbar_app_dialog, null);
            if (mDialogTheme == DialogTheme.LIGHT) {
                mDialog = new Dialog(mContext, android.R.style.Theme_Material_Light_Dialog_NoActionBar);
            } else if (mDialogTheme == DialogTheme.DARK) {
                mDialog = new Dialog(mContext, android.R.style.Theme_Material_Dialog_NoActionBar);
            } else {
                mDialog = new Dialog(getThemedContext(
                        android.R.style.Theme_Material_Light_Dialog_NoActionBar));
            }
            mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mDialog.setContentView(appView);
            mDialog.setCanceledOnTouchOutside(true);
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            int pf = XposedHelpers.getIntField(mDialog.getWindow().getAttributes(), "privateFlags");
            pf |= 0x00000010;
            XposedHelpers.setIntField(mDialog.getWindow().getAttributes(), "privateFlags", pf);
            mDialog.getWindow().getAttributes().gravity = Gravity.BOTTOM;

            View appRow1 = appView.findViewById(R.id.appRow1);
            View appRow2 = appView.findViewById(R.id.appRow2);
            View appRow3 = appView.findViewById(R.id.appRow3);
            View separator = appView.findViewById(R.id.separator);
            View separator2 = appView.findViewById(R.id.separator2);
            int appCount = 0;
            boolean appRow1Visible = false;
            boolean appRow2Visible = false;
            boolean appRow3Visible = false;
            TextView lastVisible = null;
            int color = ColorUtils.getColorFromStyleAttr(mDialog.getContext(), android.R.attr.textColorPrimary);
            for (AppInfo ai : mAppSlots) {
                TextView tv = appView.findViewById(ai.getResId());
                if (ai.getValue() == null || (ai.isUnsafeAction() &&
                        SysUiManagers.KeyguardMonitor.isShowing() &&
                        SysUiManagers.KeyguardMonitor.isLocked())) {
                    tv.setVisibility(View.GONE);
                    continue;
                }

                tv.setTextColor(ColorStateList.valueOf(color));
                tv.setText(ai.getAppName());
                tv.setCompoundDrawablesWithIntrinsicBounds(null, ai.getAppIcon(), null, null);
                tv.setOnClickListener(mAppOnClick);
                tv.setVisibility(View.VISIBLE);
                lastVisible = tv;

                appRow1Visible |= ai.getResId() == R.id.quickapp1 || ai.getResId() == R.id.quickapp2 || 
                        ai.getResId() == R.id.quickapp3 || ai.getResId() == R.id.quickapp4;
                appRow2Visible |= ai.getResId() == R.id.quickapp5 || ai.getResId() == R.id.quickapp6 || 
                        ai.getResId() == R.id.quickapp7 || ai.getResId() == R.id.quickapp8;
                appRow3Visible |= ai.getResId() == R.id.quickapp9 || ai.getResId() == R.id.quickapp10 || 
                        ai.getResId() == R.id.quickapp11 || ai.getResId() == R.id.quickapp12;

                appCount++;
            }

            if (appCount == 0) {
                Toast.makeText(mContext, mGbContext.getString(R.string.app_launcher_no_apps),
                        Toast.LENGTH_LONG).show();
            } else if (appCount == 1) {
                mAppOnClick.onClick(lastVisible);
            } else {
                appRow1.setVisibility(appRow1Visible ? View.VISIBLE : View.GONE);
                appRow2.setVisibility(appRow2Visible ? View.VISIBLE : View.GONE);
                appRow3.setVisibility(appRow3Visible ? View.VISIBLE : View.GONE);
                separator.setVisibility(appRow1Visible && appRow2Visible ?
                        View.VISIBLE : View.GONE);
                separator2.setVisibility(appRow2Visible && appRow3Visible ||
                        appRow1Visible && appRow3Visible ? View.VISIBLE : View.GONE);
                mDialog.show();
                mHandler.postDelayed(mDismissAppDialogRunnable, 4000);
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, "Error opening app launcher dialog: ", t);
        }
    }

    private final View.OnClickListener mAppOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            dismissDialog();

            try {
                for(AppInfo ai : mAppSlots) {
                    if (v.getId() == ai.getResId()) {
                        startActivity(v.getContext(), ai.getIntent());
                        return;
                    }
                }
            } catch (Exception e) {
                GravityBox.log(TAG, "Unable to start activity: ", e);
            }
        }
    };

    public void startActivity(Context context, Intent intent, ActivityOptions opts) throws Exception {
        // if intent is a GB action of broadcast type, handle it directly here
        if (ShortcutActivity.isGbBroadcastShortcut(intent)) {
            boolean isLaunchBlocked = SysUiManagers.KeyguardMonitor.isShowing() &&
                        SysUiManagers.KeyguardMonitor.isLocked() &&
                        !ShortcutActivity.isActionSafe(intent.getStringExtra(
                                ShortcutActivity.EXTRA_ACTION));
            if (DEBUG) log("isLaunchBlocked: " + isLaunchBlocked);

            if (!isLaunchBlocked) {
                Intent newIntent = new Intent(intent.getStringExtra(ShortcutActivity.EXTRA_ACTION));
                newIntent.putExtras(intent);
                context.sendBroadcast(newIntent);
            }
        // otherwise start activity dismissing keyguard
        } else {
            try {
                if (ModStatusBar.getStatusBar() != null) {
                    XposedHelpers.callMethod(ModStatusBar.getStatusBar(), "postStartActivityDismissingKeyguard", intent, 0);
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    Constructor<?> uhConst = XposedHelpers.findConstructorExact(UserHandle.class, int.class);
                    UserHandle uh = (UserHandle) uhConst.newInstance(-3);
                    if (opts != null) {
                        XposedHelpers.callMethod(context, "startActivityAsUser", intent, opts.toBundle(), uh);
                    } else {
                        XposedHelpers.callMethod(context, "startActivityAsUser", intent, uh);
                    }
                }
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    }

    public void startActivity(Context context, Intent intent) throws Exception {
        startActivity(context, intent, null);
    }

    private void updateAppSlot(int slot, String value) {
        mAppSlots.get(slot).initAppInfo(value);
    }

    public AppInfo createAppInfo() {
        return new AppInfo(0);
    }

    public final class AppInfo {
        private String mAppName;
        private Drawable mAppIcon;
        private String mValue;
        private final int mResId;
        private Intent mIntent;
        private String mPkgName;
        private int mSizeDp;

        AppInfo(int resId) {
            mResId = resId;
            mSizeDp = 50;
        }

        public int getResId() {
            return mResId;
        }

        public String getAppName() {
            return mAppName;
        }

        public Drawable getAppIcon() {
            return (mAppIcon == null ? 
                    mContext.getDrawable(android.R.drawable.ic_menu_help) : mAppIcon);
        }

        public void setAppIcon(Drawable d) {
            mAppIcon = d;
        }

        public void setSizeDp(int sizeDp) {
            mSizeDp = sizeDp;
        }

        public String getValue() {
            return mValue;
        }

        public Intent getIntent() {
            return mIntent;
        }

        public String getPackageName() {
            return mPkgName;
        }

        boolean isUnsafeAction() {
            return (mIntent != null &&
                    !ShortcutActivity.isActionSafe(mIntent.getStringExtra(
                            ShortcutActivity.EXTRA_ACTION)));
        }

        private void reset() {
            mValue = mAppName = null;
            mAppIcon = null;
            mIntent = null;
            mPkgName = null;
        }

        public void initAppInfo(String value) {
            initAppInfo(value, true);
        }

        void initAppInfo(String value, boolean loadLabelAndIcon) {
            mValue = value;
            if (mValue == null) {
                reset();
                return;
            }

            try {
                mIntent = Intent.parseUri(value, 0);
                if (!mIntent.hasExtra("mode")) {
                    reset();
                    return;
                }
                if (mIntent.getComponent() != null) {
                    mPkgName = mIntent.getComponent().getPackageName();
                }

                if (loadLabelAndIcon) {
                    final int mode = mIntent.getIntExtra("mode", AppPickerPreference.MODE_APP);
                    Bitmap appIcon = null;
                    final String iconResName = mIntent.getStringExtra("iconResName");
                    final int iconResId = iconResName != null ?
                            mGbResources.getIdentifier(iconResName, "drawable",
                                    mGbContext.getPackageName()) : 0;
                    if (iconResId != 0) {
                        appIcon = BitmapUtils.drawableToBitmap(mGbContext.getDrawable(iconResId));
                    } else if (mIntent.hasExtra("icon")) {
                        final String appIconPath = mIntent.getStringExtra("icon");
                        if (appIconPath != null) {
                            File f = new File(appIconPath);
                            if (f.exists() && f.canRead()) {
                                FileInputStream fis = new FileInputStream(f);
                                appIcon = BitmapFactory.decodeStream(fis);
                                fis.close();
                            }
                        }
                    }

                    if (mode == AppPickerPreference.MODE_APP) {
                        ActivityInfo ai = mPm.getActivityInfo(mIntent.getComponent(), 0);
                        mAppName = ai.loadLabel(mPm).toString();
                        if (appIcon == null) {
                            appIcon = BitmapUtils.drawableToBitmap(ai.loadIcon(mPm));
                        }
                    } else if (mode == AppPickerPreference.MODE_SHORTCUT) {
                        mAppName = mIntent.getStringExtra("label");
                    }
                    if (appIcon != null) {
                        int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mSizeDp, 
                                mResources.getDisplayMetrics());
                        Bitmap scaledIcon = Bitmap.createScaledBitmap(appIcon, sizePx, sizePx, true);
                        mAppIcon = new BitmapDrawable(mResources, scaledIcon);
                        if (mDialogTheme != DialogTheme.DARK && 
                                iconResName != null && iconResName.startsWith("ic_shortcut")) {
                            mAppIcon.setTint(0xFF707070);
                        }
                    }
                }

                if (DEBUG) log("AppInfo initialized for: " + getAppName() + " [" + mPkgName + "]");
            } catch (NameNotFoundException e) {
                GravityBox.log(TAG, "App not found: " + mIntent);
                reset();
            } catch (Exception e) {
                GravityBox.log(TAG, "Unexpected error: ", e);
                reset();
            }
        }
    }
}
