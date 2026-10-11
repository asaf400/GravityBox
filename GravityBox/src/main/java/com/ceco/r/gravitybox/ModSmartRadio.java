/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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

import com.ceco.r.gravitybox.managers.BroadcastMediator;
import com.ceco.r.gravitybox.managers.SysUiManagers;
import com.ceco.r.gravitybox.shortcuts.AShortcut;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModSmartRadio {
    private static final String TAG = "GB:SmartRadio";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final boolean DEBUG = false;

    public static final String SETTING_SMART_RADIO_ENABLED = "gb_smart_radio_enabled";
    public static final String SETTING_SMART_RADIO_STATE = "gb_smart_radio_state";
    public static final String ACTION_TOGGLE_SMART_RADIO = "gravitybox.intent.action.TOGGLE_SMART_RADIO";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public enum State { UNKNOWN, NORMAL, POWER_SAVING }

    private static Context mContext;
    private static int mNormalMode;
    private static int mPowerSavingMode;
    private static ConnectivityManager mConnManager;
    private static State mCurrentState = State.UNKNOWN;
    private static boolean mIsScreenOff;
    private static boolean mPowerSaveWhenScreenOff;
    private static boolean mIgnoreWhileLocked;
    private static NetworkModeChanger mNetworkModeChanger;
    private static int mModeChangeDelay;
    private static KeyguardManager mKeyguardManager;
    private static int mScreenOffDelay;
    private static boolean mSmartRadioEnabled;
    private static boolean mIgnoreMobileDataAvailability;
    private static boolean mIsPhoneIdle = true;
    private static int mAdaptiveDelayThreshold;

    private static BroadcastMediator.Receiver mBroadcastReceiver = (context, intent) -> {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_SMART_RADIO_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_NORMAL_MODE)) {
                setNewModeValue(State.NORMAL,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_SR_NORMAL_MODE, -1));
                if (DEBUG) log("mNormalMode = " + mNormalMode);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_POWER_SAVING_MODE)) {
                setNewModeValue(State.POWER_SAVING,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_SR_POWER_SAVING_MODE, -1));
                if (DEBUG) log("mPowerSavingMode = " + mPowerSavingMode);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_SCREEN_OFF)) {
                mPowerSaveWhenScreenOff = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SR_SCREEN_OFF, false);
                if (DEBUG) log("mPowerSaveWhenScreenOff = " + mPowerSaveWhenScreenOff);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_IGNORE_LOCKED)) {
                mIgnoreWhileLocked = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SR_IGNORE_LOCKED, true);
                if (DEBUG) log("mIgnoreWhileLocked = " + mIgnoreWhileLocked);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_MODE_CHANGE_DELAY)) {
                mModeChangeDelay = intent.getIntExtra(GravityBoxSettings.EXTRA_SR_MODE_CHANGE_DELAY, 5);
                if (DEBUG) log("mModeChangeDelay = " + mModeChangeDelay);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_SCREEN_OFF_DELAY)) {
                mScreenOffDelay = intent.getIntExtra(GravityBoxSettings.EXTRA_SR_SCREEN_OFF_DELAY, 0);
                if (DEBUG) log("mScreenOffDelay = " + mScreenOffDelay);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_MDA_IGNORE)) {
                mIgnoreMobileDataAvailability = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_SR_MDA_IGNORE, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_ADAPTIVE_DELAY)) {
                mAdaptiveDelayThreshold = intent.getIntExtra(GravityBoxSettings.EXTRA_SR_ADAPTIVE_DELAY, 0);
                if (DEBUG) log("mAdaptiveDelay = " + mAdaptiveDelayThreshold);
            }
        } else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            int nwType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
            if (DEBUG) log("Received CONNECTIVITY_ACTION broadcast; networkType=" + nwType);
            if (nwType == ConnectivityManager.TYPE_WIFI ||
                    nwType == ConnectivityManager.TYPE_MOBILE) {
                if (shouldSwitchToNormalState()) {
                    switchToState(State.NORMAL);
                } else {
                    switchToState(State.POWER_SAVING);
                }
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            if (DEBUG) log("Screen turning off");
            mIsScreenOff = true;
            if (mPowerSaveWhenScreenOff && !isTetheringViaMobileNetwork()) {
                switchToState(State.POWER_SAVING, true);
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            if (DEBUG) log("Screen turning on");
            mIsScreenOff = false;
            if (shouldSwitchToNormalState()) {
                switchToState(State.NORMAL);
            }
        } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
            if (DEBUG) log("Keyguard unlocked");
            if (shouldSwitchToNormalState()) {
                switchToState(State.NORMAL);
            }
        } else if (intent.getAction().equals(ACTION_TOGGLE_SMART_RADIO)) {
            changeSmartRadioState(intent);
        } else if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            final boolean wasPhoneBusy = !mIsPhoneIdle;
            mIsPhoneIdle = TelephonyManager.EXTRA_STATE_IDLE.equals(
                    intent.getStringExtra(TelephonyManager.EXTRA_STATE));
            if (DEBUG) log("ACTION_PHONE_STATE_CHANGED: mIsPhoneIdle=" + mIsPhoneIdle);
            if (wasPhoneBusy && mIsPhoneIdle) {
                if (shouldSwitchToNormalState()) {
                    switchToState(State.NORMAL);
                } else {
                    switchToState(State.POWER_SAVING);
                }
            }
        }
    };

    private static boolean isMobileDataEnabled() {
        try {
            return (Boolean) XposedHelpers.callMethod(mConnManager, "getMobileDataEnabled");
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private static boolean isMobileNetworkAvailable() {
        if (mIgnoreMobileDataAvailability) {
            return true;
        }
        try {
            Network[] networkInfos= mConnManager.getAllNetworks();
            for (Network net : networkInfos) {
                NetworkInfo netInfo = mConnManager.getNetworkInfo(net);
                if (netInfo.getType() == ConnectivityManager.TYPE_MOBILE &&
                        netInfo.isAvailable()) {
                    if (DEBUG) log("isMobileNetworkAvailable: true");
                    return true;
                }
            }
            if (DEBUG) log("isMobileNetworkAvailable: false");
            return false;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private static boolean isWifiConnected() {
        try {
            Network[] networkInfos= mConnManager.getAllNetworks();
            for (Network net : networkInfos) {
                NetworkInfo netInfo = mConnManager.getNetworkInfo(net);
                if (netInfo.getType() == ConnectivityManager.TYPE_WIFI &&
                        netInfo.isConnected()) {
                    if (DEBUG) log("isWifiConnected: true");
                    return true;
                }
            }
            if (DEBUG) log("isWifiConnected: false");
            return false;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return false;
        }
    }

    private static boolean isTetheringViaMobileNetwork() {
        try {
            String[] wifiRegexs = (String[]) XposedHelpers.callMethod(mConnManager, "getTetherableWifiRegexs");
            String[] usbRegexs = (String[]) XposedHelpers.callMethod(mConnManager, "getTetherableUsbRegexs");
            String[] btRegexes = (String[]) XposedHelpers.callMethod(mConnManager, "getTetherableBluetoothRegexs");
            String[] tetheredIfaces = (String[]) XposedHelpers.callMethod(mConnManager, "getTetheredIfaces");

            for (String tiface : tetheredIfaces) {
                // if wifi tethering active it's obvious it goes via mobile network
                for (String regex : wifiRegexs) {
                    if (tiface.matches(regex)) {
                        if (DEBUG) log("isTetheringViaMobileNetwork: WiFi tethering enabled");
                        return true;
                    }
                }

                // if not WiFi connected check for USB and BT tethering
                if (!isWifiConnected()) {
                    for (String regex : usbRegexs) {
                        if (tiface.matches(regex)) {
                            if (DEBUG) log("isTetheringViaMobileNetwork: USB tethering enabled and WiFi not connected");
                            return true;
                        }
                    }
                    for (String regex : btRegexes) {
                        if (tiface.matches(regex)) {
                            if (DEBUG) log("isTetheringViaMobileNetwork: BT tethering enabled and WiFi not connected");
                            return true;
                        }
                    }
                }
            }

            if (DEBUG) log("isTetheringViaMobileNetwork: nope");
            return false;
        } catch (Throwable t) {
            GravityBox.log(TAG, "isTetheringViaMobileNetwork: ", t);
            return false;
        }
    }

    private static boolean isKeyguardLocked() {
        try {
            return mKeyguardManager.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean shouldSwitchToNormalState() {
        // basic rules
        boolean shouldSwitch = isMobileNetworkAvailable() && 
                                   isMobileDataEnabled() &&
                                   !isWifiConnected();
        // additional rules
        if (mPowerSaveWhenScreenOff && !isTetheringViaMobileNetwork()) {
            shouldSwitch &= !mIsScreenOff;
            shouldSwitch &= !(isKeyguardLocked() && mIgnoreWhileLocked);
        }

        return shouldSwitch;
    }

    private static void switchToState(State newState) {
        switchToState(newState, false);
    }

    private static void switchToState(State newState, boolean force) {
        if (!mSmartRadioEnabled) {
            if (DEBUG) log("switchToState: Smart Radio is disabled - ignoring");
            return;
        } else if (mCurrentState == newState && !force) {
            if (DEBUG) log("switchToState: new state == previous state - ignoring");
            return;
        } else if (!mIsPhoneIdle) {
            if (DEBUG) log("switchToState: phone is not idle - ignoring");
            return;
        } else if (!isMobileNetworkAvailable()) {
            // force power saving state no matter what so we start with it when mobile network is available again
            if (DEBUG) log("switchToState: mobile network unavailable - resetting to POWER_SAVING state");
            newState = State.POWER_SAVING;
        } else if (DEBUG) {
            log("Switching to state: " + newState);
        }

        try {
            int networkMode = -1;
            switch (newState) {
                case NORMAL: networkMode = mNormalMode; break;
                case POWER_SAVING: networkMode = mPowerSavingMode; break;
                default: break;
            }
            mCurrentState = newState;
            Settings.System.putString(mContext.getContentResolver(),
                    SETTING_SMART_RADIO_STATE, mCurrentState.toString());
            mNetworkModeChanger.changeNetworkMode(networkMode);
        } catch (Throwable t) {
            GravityBox.log(TAG, "switchToState: ", t);
        }
    }

    private static void setNewModeValue(State state, int mode) {
        int currentMode = state == State.NORMAL ? mNormalMode : mPowerSavingMode;
        if (mode != currentMode) {
            if (state == State.NORMAL) {
                mNormalMode = mode; 
            } else {
                mPowerSavingMode = mode;
            }
            if (mCurrentState == state) {
                switchToState(state, true);
            }
        }
    }

    private static void changeSmartRadioState(Intent intent) {
        try {
            if (intent.hasExtra(AShortcut.EXTRA_ENABLE)) {
                mSmartRadioEnabled = intent.getBooleanExtra(AShortcut.EXTRA_ENABLE, false);
            } else {
                mSmartRadioEnabled = !mSmartRadioEnabled;
            }
            Settings.System.putInt(mContext.getContentResolver(),
                    SETTING_SMART_RADIO_ENABLED, mSmartRadioEnabled ? 1 : 0);
            if (mSmartRadioEnabled) {
                if (shouldSwitchToNormalState()) {
                    switchToState(State.NORMAL);
                } else {
                    switchToState(State.POWER_SAVING);
                }
            }
            if (intent.getBooleanExtra(AShortcut.EXTRA_SHOW_TOAST, false)) {
                Utils.postToast(mContext, mSmartRadioEnabled ? R.string.smart_radio_on :
                    R.string.smart_radio_off);
            }
            if (DEBUG) log("mSmartRadioEnabled=" + mSmartRadioEnabled);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static class NetworkModeChanger implements Runnable, BroadcastMediator.Receiver {
        public static final String ACTION_CHANGE_MODE_ALARM = "gravitybox.smartradio.intent.action.CHANGE_MODE_ALARM";

        private Context mContext;
        private Handler mHandler;
        private int mNextNetworkMode;
        private int mCurrentNetworkMode;
        private WakeLock mWakeLock;
        private AlarmManager mAlarmManager;
        private PendingIntent mPendingIntent;
        private LinkActivity mLinkActivity;

        class LinkActivity {
            long timestamp;
            long rxBytes;
            long txBytes;
        }

        public NetworkModeChanger(Context context, Handler handler) {
            mContext = context;
            mHandler = handler;
            mNextNetworkMode = -1;
            mCurrentNetworkMode = -1;
            mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            mLinkActivity = new LinkActivity();

            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GB:SmartRadio");

            SysUiManagers.BroadcastMediator.subscribe(this,
                    ACTION_CHANGE_MODE_ALARM,
                    PhoneWrapper.ACTION_NETWORK_TYPE_CHANGED);
        }

        @Override
        public void run() {
            if (mContext == null || mNextNetworkMode == mCurrentNetworkMode) {
                releaseWakeLockIfHeld();
                return;
            }

            if (DEBUG) log("NetworkModeChanger: sending intent to change network mode to: " + mNextNetworkMode);
            Intent intent = new Intent(PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE);
            intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mNextNetworkMode);
            mContext.sendBroadcast(intent);
            releaseWakeLockIfHeld();
        }

        public void changeNetworkMode(int networkMode) {
            mHandler.removeCallbacks(this);
            releaseWakeLockIfHeld();
            cancelPendingAlarm();
            if (networkMode == -1) {
                if (DEBUG) log("NetworkModeChanger: ignoring request to change to undefined mode (-1)");
                return;
            }

            mNextNetworkMode = networkMode;
            if (mIsScreenOff && mNextNetworkMode == mPowerSavingMode && mScreenOffDelay != 0) {
                if (DEBUG) log("NetworkModeChanger: scheduling alarm for switching to power saving mode");
                scheduleAlarm();
            } else {
                if (mModeChangeDelay == 0) {
                    run();
                } else {
                    if (DEBUG) log("NetworkModeChanger: scheduling network mode change");
                    if (mIsScreenOff) {
                        mWakeLock.acquire(mModeChangeDelay*1000+1000);
                        if (DEBUG) log("NetworkModeChanger: Wake Lock acquired");
                    }
                    mHandler.postDelayed(this, mModeChangeDelay*1000);
                }
            }
        }

        private void releaseWakeLockIfHeld() {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
                if (DEBUG) log("NetworkModeChanger: Wake Lock released");
            }
        }

        private void scheduleAlarm() {
            Intent intent = new Intent(ACTION_CHANGE_MODE_ALARM);
            mPendingIntent = PendingIntent.getBroadcast(mContext, 1, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            long triggerAtMillis = System.currentTimeMillis() + mScreenOffDelay*60*1000;
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, mPendingIntent);
            mLinkActivity.timestamp = System.currentTimeMillis();
            mLinkActivity.rxBytes = TrafficStats.getMobileRxBytes();
            mLinkActivity.txBytes = TrafficStats.getMobileTxBytes();
        }

        private void cancelPendingAlarm() {
            if (mAlarmManager != null && mPendingIntent != null) {
                mAlarmManager.cancel(mPendingIntent);
                mPendingIntent = null;
            }
        }

        private boolean shouldPostponeAlarm() {
            boolean postpone = false;
            if (mAdaptiveDelayThreshold > 0) {
                // if there's link activity higher than defined threshold
                long rxDelta = TrafficStats.getMobileRxBytes() - mLinkActivity.rxBytes;
                long txDelta = TrafficStats.getMobileTxBytes() - mLinkActivity.txBytes;
                long timeDelta = System.currentTimeMillis() - mLinkActivity.timestamp;
                long speedRxKBs = (long)(rxDelta / (timeDelta / 1000f)) / 1024;
                long speedTxKBs = (long)(txDelta / (timeDelta / 1000f)) / 1024;
                postpone |= speedTxKBs >= mAdaptiveDelayThreshold || speedRxKBs >= mAdaptiveDelayThreshold;
                if (DEBUG) log("shouldPostponeAlarm: speedRxKBs=" + speedRxKBs +
                        "; speedTxKBs=" + speedTxKBs + "; threshold=" + mAdaptiveDelayThreshold);
            }
            return postpone;
        }

        @Override
        public void onBroadcastReceived(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_CHANGE_MODE_ALARM)) {
                if (DEBUG) log("ACTION_CHANGE_MODE_ALARM received");
                mPendingIntent = null;
                if (shouldPostponeAlarm()) {
                    if (DEBUG) log("NetworkModeChanger: postponing alarm for switching to power saving mode");
                    scheduleAlarm();
                } else {
                    run();
                }
            } else if (intent.getAction().equals(PhoneWrapper.ACTION_NETWORK_TYPE_CHANGED)) {
                String tag = intent.getStringExtra(PhoneWrapper.EXTRA_RECEIVER_TAG);
                if (tag == null) {
                    mCurrentNetworkMode = intent.getIntExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, 0);
                    if (DEBUG) log("NetworkModeChanger: onChange; mCurrentNetworkMode=" + mCurrentNetworkMode);
                }
            }
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classSystemUIService = XposedHelpers.findClass(
                    "com.android.systemui.SystemUIService", classLoader);

            mNormalMode = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_NORMAL_MODE, -1);
            mPowerSavingMode = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_POWER_SAVING_MODE, -1);
            mPowerSaveWhenScreenOff = prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_SCREEN_OFF, false);
            mIgnoreWhileLocked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_IGNORE_LOCKED, true);
            mModeChangeDelay = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_MODE_CHANGE_DELAY, 5);
            mScreenOffDelay = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_SCREEN_OFF_DELAY, 0);
            mIgnoreMobileDataAvailability = prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_MDA_IGNORE, false);
            mAdaptiveDelayThreshold = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_ADAPTIVE_DELAY, 0);

            XposedHelpers.findAndHookMethod(classSystemUIService, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    mContext = (Context) param.thisObject;
                    if (mContext != null) {
                        if (DEBUG) log("Initializing SmartRadio");

                        mSmartRadioEnabled = Settings.System.getInt(mContext.getContentResolver(),
                                SETTING_SMART_RADIO_ENABLED, 1) == 1;
                        mConnManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                        mNetworkModeChanger = new NetworkModeChanger(mContext, new Handler());
                        Settings.System.putString(mContext.getContentResolver(), 
                                SETTING_SMART_RADIO_STATE, mCurrentState.toString());

                        SysUiManagers.BroadcastMediator.subscribe(mBroadcastReceiver,
                                GravityBoxSettings.ACTION_PREF_SMART_RADIO_CHANGED,
                                ConnectivityManager.CONNECTIVITY_ACTION,
                                Intent.ACTION_SCREEN_ON,
                                Intent.ACTION_SCREEN_OFF,
                                Intent.ACTION_USER_PRESENT,
                                ACTION_TOGGLE_SMART_RADIO,
                                TelephonyManager.ACTION_PHONE_STATE_CHANGED);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    } 
}
