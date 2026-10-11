/*
 * Copyright (C) 2020 Peter Gregus for GravityBox Project (C3C076@xda)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.r.gravitybox;

import java.lang.reflect.Method;

import com.ceco.r.gravitybox.ProgressBarController.Mode;
import com.ceco.r.gravitybox.ProgressBarController.ProgressInfo;
import com.ceco.r.gravitybox.managers.BroadcastMediator;
import com.ceco.r.gravitybox.managers.SysUiStatusBarIconManager;
import com.ceco.r.gravitybox.managers.SysUiStatusBarIconManager.ColorInfo;
import com.ceco.r.gravitybox.managers.SysUiStatusBarIconManager.IconManagerListener;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

@SuppressLint("AppCompatCustomView")
public abstract class TrafficMeterAbstract extends TextView
                        implements BroadcastMediator.Receiver, IconManagerListener,
                                   ProgressBarController.ProgressStateListener {
    protected static final String PACKAGE_NAME = "com.android.systemui";
    protected static final String TAG = "GB:NetworkTraffic";
    protected static final boolean DEBUG = false;

    public enum TrafficMeterMode { OFF, SIMPLE, OMNI }

    public enum DisplayMode { ALWAYS, DOWNLOAD_MANAGER, PROGRESS_TRACKING }

    protected Context mGbContext;
    protected boolean mAttached;
    protected int mInterval = 1000;
    protected int mPosition;
    protected int mSize;
    protected int mMarginStartRight;
    protected boolean mIsScreenOn = true;
    protected DisplayMode mDisplayMode;
    protected boolean mIsDownloadActive;
    private PhoneStateListener mPhoneStateListener;
    private TelephonyManager mPhone;
    protected boolean mMobileDataConnected;
    protected boolean mShowOnlyForMobileData;
    protected boolean mIsTrackingProgress;
    protected boolean mAllowInLockscreen;
    private boolean mHiddenByPolicy;
    private boolean mHiddenByHeadsUp;
    private Method mGetRxBytesMethod;
    private Method mGetTxBytesMethod;
    private final ConnectivityManager mConManager;

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static TrafficMeterAbstract create(Context context, TrafficMeterMode mode) {
        if (mode == TrafficMeterMode.SIMPLE) {
            return new TrafficMeter(context);
        } else if (mode == TrafficMeterMode.OMNI) {
            return new TrafficMeterOmni(context);
        } else {
            throw new IllegalArgumentException("Invalid traffic meter mode supplied");
        }
    }

    protected TrafficMeterAbstract(Context context) {
        super(context);

        mConManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        mMarginStartRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                context.getResources().getDisplayMetrics());
        setLayoutParams(lParams);
        setTextAppearance(context.getResources().getIdentifier(
                "TextAppearance.StatusBar.Clock", "style", PACKAGE_NAME));
        setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (!Utils.isWifiOnly(getContext())) {
            mPhone = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
            mPhoneStateListener = new PhoneStateListener() {
                @Override
                public void onDataConnectionStateChanged(int state, int networkType) {
                    final boolean connected = state == TelephonyManager.DATA_CONNECTED;
                    if (mMobileDataConnected != connected) {
                        mMobileDataConnected = connected;
                        if (DEBUG) log("onDataConnectionStateChanged: mMobileDataConnected=" + mMobileDataConnected);
                        updateState();
                    }
                    
                }
            };
        }
    }

    public void initialize(XSharedPreferences prefs) throws Throwable {
        prefs.reload();
        try {
            mSize = Integer.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_DATA_TRAFFIC_SIZE, "14"));
        } catch (NumberFormatException nfe) {
            GravityBox.log(TAG, "Invalid preference value for PREF_KEY_DATA_TRAFFIC_SIZE");
        }

        try {
            setTrafficMeterPosition(Integer.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_DATA_TRAFFIC_POSITION, "0")));
        } catch (NumberFormatException nfe) {
            GravityBox.log(TAG, "Invalid preference value for PREF_KEY_DATA_TRAFFIC_POSITION");
        }

        mDisplayMode = DisplayMode.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_DATA_TRAFFIC_DISPLAY_MODE, "ALWAYS"));

        if (mPhone != null) {
            mShowOnlyForMobileData = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_DATA_TRAFFIC_ACTIVE_MOBILE_ONLY, false);
        }

        mAllowInLockscreen = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_DATA_TRAFFIC_LOCKSCREEN, true);

        onInitialize(prefs);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateState();
            } else if (ModDownloadProvider.ACTION_DOWNLOAD_STATE_CHANGED.equals(action)
                    && intent.hasExtra(ModDownloadProvider.EXTRA_ACTIVE)) {
                mIsDownloadActive = intent.getBooleanExtra(ModDownloadProvider.EXTRA_ACTIVE, false);
                if (DEBUG) log("ACTION_DOWNLOAD_STATE_CHANGED; active=" + mIsDownloadActive);
                if (mDisplayMode == DisplayMode.DOWNLOAD_MANAGER) {
                    updateState();
                }
            }
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            if (DEBUG) log("attached to window");
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(ModDownloadProvider.ACTION_DOWNLOAD_STATE_CHANGED);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler(),
                    Context.RECEIVER_EXPORTED);
 
            if (mPhone != null) {
                mPhone.listen(mPhoneStateListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
            }

            updateState();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            if (DEBUG) log("detached from window");
            getContext().unregisterReceiver(mIntentReceiver);

            if (mPhone != null) {
                mPhone.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            }

            updateState();
        }
    }

    public int getTrafficMeterPosition() {
        return mPosition;
    }

    public void setTrafficMeterPosition(int position) {
        mPosition = position;
        LinearLayout.LayoutParams lParams = (LinearLayout.LayoutParams) getLayoutParams();
        lParams.setMarginStart(mPosition == GravityBoxSettings.DT_POSITION_RIGHT_EDGE ?
                mMarginStartRight : 0);
        setLayoutParams(lParams);
    }

    public boolean isAllowedInLockscreen() {
        return mAllowInLockscreen;
    }

    public void setHiddenByPolicy(boolean hidden) {
        mHiddenByPolicy = hidden;
        updateState();
    }

    private void setHiddenByHeadsUp(boolean hidden) {
        mHiddenByHeadsUp = hidden;
        updateState();
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(GravityBoxSettings.ACTION_PREF_DATA_TRAFFIC_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_MODE)) {
                return;
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_POSITION)) {
                return;
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_SIZE)) {
                mSize = intent.getIntExtra(GravityBoxSettings.EXTRA_DT_SIZE, 14);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_DISPLAY_MODE)) {
                mDisplayMode = DisplayMode.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_DT_DISPLAY_MODE));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_ACTIVE_MOBILE_ONLY)) {
                mShowOnlyForMobileData = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_DT_ACTIVE_MOBILE_ONLY, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_LOCKSCREEN)) {
                mAllowInLockscreen = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_DT_LOCKSCREEN, false);
            }

            onPreferenceChanged(intent);
            updateState();
        } else if (action.equals(Intent.ACTION_SCREEN_ON) ||
                action.equals(Intent.ACTION_SCREEN_OFF)) {
            mIsScreenOn = action.equals(Intent.ACTION_SCREEN_ON);
            updateState();
        }
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & SysUiStatusBarIconManager.FLAG_ICON_TINT_CHANGED) != 0) {
            setTextColor(colorInfo.iconTint);
        }
        if ((flags & SysUiStatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaSignalCluster);
        }
        if ((flags & SysUiStatusBarIconManager.FLAG_HEADS_UP_VISIBILITY_CHANGED) != 0) {
            setHiddenByHeadsUp(colorInfo.headsUpVisible &&
                    getTrafficMeterPosition() != GravityBoxSettings.DT_POSITION_RIGHT);
        }
    }

    private boolean shoudStartTrafficUpdates() {
        boolean shouldStart = mAttached && mIsScreenOn && !mHiddenByPolicy && !mHiddenByHeadsUp;
        if (mDisplayMode == DisplayMode.DOWNLOAD_MANAGER) {
            shouldStart &= mIsDownloadActive;
        } else if (mDisplayMode == DisplayMode.PROGRESS_TRACKING) {
            shouldStart &= mIsTrackingProgress;
        }
        if (mShowOnlyForMobileData) {
            shouldStart &= mMobileDataConnected;
        }
        return shouldStart;
    }

    protected void updateState() {
        if (shoudStartTrafficUpdates()) {
            startTrafficUpdates();
            setVisibility(View.VISIBLE);
            if (DEBUG) log("traffic updates started");
        } else {
            stopTrafficUpdates();
            setVisibility(View.GONE);
            setText("");
            if (DEBUG) log("traffic updates stopped");
        }
    }

    @Override
    public void onProgressTrackingStarted(ProgressBarController.Mode mode) {
        mIsTrackingProgress = true;
        if (mDisplayMode == DisplayMode.PROGRESS_TRACKING) {
            updateState();
        }
    }

    @Override
    public void onProgressTrackingStopped() {
        mIsTrackingProgress = false;
        if (mDisplayMode == DisplayMode.PROGRESS_TRACKING) {
            updateState();
        }
    }

    @Override
    public void onProgressAdded(ProgressInfo pi) { }

    @Override
    public void onProgressUpdated(ProgressInfo pInfo) { }

    @Override
    public void onProgressRemoved(String id) { }

    @Override
    public void onProgressModeChanged(Mode mode) { }

    @Override
    public void onProgressPreferencesChanged(Intent intent) { }

    protected abstract void onInitialize(XSharedPreferences prefs) throws Throwable;
    protected abstract void onPreferenceChanged(Intent intent);
    protected abstract void startTrafficUpdates();
    protected abstract void stopTrafficUpdates();

    @SuppressLint("DiscouragedPrivateApi")
    protected boolean canUsePrimaryMethod() {
        if (mGetRxBytesMethod == null) {
            try {
                mGetRxBytesMethod = TrafficStats.class.getDeclaredMethod("getRxBytes", String.class);
                mGetRxBytesMethod.setAccessible(true);
            } catch (Throwable t) {
                if (DEBUG) log("canUsePrimaryMethod: error resolving getRxBytes method: " + t.getMessage());
            }
        }
        if (mGetTxBytesMethod == null) {
            try {
                mGetTxBytesMethod = TrafficStats.class.getDeclaredMethod("getTxBytes", String.class);
                mGetTxBytesMethod.setAccessible(true);
            } catch (Throwable t) {
                if (DEBUG) log("canUsePrimaryMethod: error resolving getTxBytes method: " + t.getMessage());
            }
        }
        return (mGetRxBytesMethod != null && mGetTxBytesMethod != null);
    }

    protected long[] getTotalRxTxBytes() {
        return (canUsePrimaryMethod() ? getTotalRxTxBytesPrimary() :
                getTotalRxTxBytesSecondary());
    }

    private static boolean isCountedInterface(String iface) {
        return (iface != null &&
                !iface.equals("ifname") &&
                !iface.equals("lo") &&
                !iface.startsWith("tun"));
    }

    private static long tryParseLong(String obj) {
        try {
            return Long.parseLong(obj);
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressLint("MissingPermission")
    private long[] getTotalRxTxBytesPrimary() {
        try {
            long[] bytes = new long[] {0, 0};
            Network[] networks = mConManager.getAllNetworks();
            for (Network network : networks){
                NetworkCapabilities nCap = mConManager.getNetworkCapabilities(network);
                if (nCap == null) continue;
                if (nCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        nCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND) &&
                        nCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    LinkProperties lp = mConManager.getLinkProperties(network);
                    if (lp == null || lp.getInterfaceName() == null) continue;
                    long bytesRx = (long) mGetRxBytesMethod.invoke(null, lp.getInterfaceName());
                    long bytesTx = (long) mGetTxBytesMethod.invoke(null, lp.getInterfaceName());
                    if (DEBUG) log("getTotalRxTxBytesPrimary: iface=" + lp.getInterfaceName() +
                            "; bytesRx=" + bytesRx + "; bytesTx=" + bytesTx);
                    bytes[0] += bytesRx;
                    bytes[1] += bytesTx;
                }
            }
            return bytes;
        } catch (Throwable t) {
            if (DEBUG) log("getTotalRxTxBytesPrimary: error: " + t.getMessage());
            return getTotalRxTxBytesSecondary();
        }
    }

    private static long[] getTotalRxTxBytesSecondary() {
        return new long[] { TrafficStats.getTotalRxBytes(),
                TrafficStats.getTotalTxBytes() };
    }
}
