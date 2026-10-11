/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
 *
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

import java.lang.reflect.Method;

import com.ceco.r.gravitybox.ledcontrol.QuietHours;
import com.ceco.r.gravitybox.ledcontrol.QuietHoursActivity;
import com.ceco.r.gravitybox.preference.IncreasingRingPreference;
import com.ceco.r.gravitybox.preference.IncreasingRingPreference.ConfigStore;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.net.Uri;
import android.os.Handler;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModRinger {
    public static final String PACKAGE_NAME = "com.android.server.telecom";
    private static final String TAG = "GB:ModRinger";
    private static final boolean DEBUG = false;

    private static ConfigStore mRingerConfig;
    private static float mIncrementAmount;
    private static float mCurrentIncrementVolume;
    private static Ringtone mRingtone;
    private static Handler mHandler;
    private static QuietHours mQuietHours;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           if (intent.getAction().equals(IncreasingRingPreference.ACTION_INCREASING_RING_CHANGED) &&
                   intent.getIntExtra(IncreasingRingPreference.EXTRA_STREAM_TYPE, -1) ==
                       AudioManager.STREAM_RING) {
               mRingerConfig.enabled = intent.getBooleanExtra(
                       IncreasingRingPreference.EXTRA_ENABLED, false);
               mRingerConfig.minVolume = intent.getFloatExtra(
                       IncreasingRingPreference.EXTRA_MIN_VOLUME, 0.1f);
               mRingerConfig.rampUpDuration = intent.getIntExtra(
                       IncreasingRingPreference.EXTRA_RAMP_UP_DURATION, 10);
               if (DEBUG) log(mRingerConfig.toString());
           } else if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
               mQuietHours = new QuietHours(intent.getExtras());
           }
        }
    };

    private static Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRingtone == null) return;
            mCurrentIncrementVolume += mIncrementAmount;
            if (mCurrentIncrementVolume > 1f) mCurrentIncrementVolume = 1f;
            if (DEBUG) log("Increasing ringtone volume to " +
                    Math.round(mCurrentIncrementVolume * 100f) + "%");
            setVolume(mCurrentIncrementVolume);
            if (mCurrentIncrementVolume < 1f) {
                mHandler.postDelayed(this, 1000);
            }
        }
    };

    public static void init(final XSharedPreferences prefs, final XSharedPreferences qhPrefs, final ClassLoader classLoader) {
        try {
            final String CLASS_RINGTONE_PLAYER = Utils.isSamsungRom() ? 
                    "com.android.server.telecom.secutils.SecAsyncRingtonePlayer" :
                    "com.android.server.telecom.AsyncRingtonePlayer";

            final Class<?> clsRingtonePlayer = XposedHelpers.findClass(CLASS_RINGTONE_PLAYER, classLoader);
            final Class<?> clsTelecomServiceImpl = XposedHelpers.findClass(
                    "com.android.server.telecom.TelecomServiceImpl", classLoader);

            Method mtdHandlePlay = null;
            try {
                mtdHandlePlay = clsRingtonePlayer.getDeclaredMethod("handlePlay", 
                        XposedHelpers.findClass("com.android.internal.os.SomeArgs", classLoader));
                if (DEBUG) log("handlePlay found");
            } catch (NoSuchMethodException nme) {
                try {
                    mtdHandlePlay = clsRingtonePlayer.getDeclaredMethod("access$000",
                            clsRingtonePlayer, XposedHelpers.findClass("com.android.internal.os.SomeArgs", classLoader));
                    if (DEBUG) log("handlePlay found as access$000");
                } catch (NoSuchMethodException ignore) { }
            }

            if (mtdHandlePlay == null) {
                GravityBox.log(TAG, "Cannot find handlePlay method in " + CLASS_RINGTONE_PLAYER + ". Increasing ringtone disabled");
                return;
            }

            mRingerConfig = new ConfigStore(prefs.getString(
                    GravityBoxSettings.PREF_KEY_INCREASING_RING, null));
            if (DEBUG) log(mRingerConfig.toString());

            mQuietHours = new QuietHours(qhPrefs);

            XposedBridge.hookAllConstructors(clsTelecomServiceImpl, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(IncreasingRingPreference.ACTION_INCREASING_RING_CHANGED);
                    intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                    context.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
                    if (DEBUG) log("TelecomServiceImpl created; broadcast receiver registered");
                }
            });

            XposedBridge.hookMethod(mtdHandlePlay, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.RINGER)) {
                        Object call = XposedHelpers.getObjectField(param.args[0], "arg2");
                        if (call != null) {
                            Uri contactUri = (Uri) XposedHelpers.callMethod(call, "getContactUri");
                            if (contactUri != null) {
                                if (DEBUG) log("Contact URI: " + contactUri);
                                String key = Utils.getContactLookupKey(
                                        (Context)XposedHelpers.getObjectField(call, "mContext"),
                                        contactUri);
                                if (DEBUG) log("Contact lookup key: " + key);
                                if (key != null && mQuietHours.getRingerWhitelist().contains(key)) {
                                    return;
                                }
                            }
                        }
                        param.setResult(null);
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!mRingerConfig.enabled) return;

                    mRingtone = (Ringtone) XposedHelpers.getObjectField(param.thisObject, "mRingtone");
                    if (mRingtone == null) {
                        if (DEBUG) log("handlePlay called but ringtone is null");
                        return;
                    }

                    setVolume(mRingerConfig.minVolume);
                    mIncrementAmount = (1f - mRingerConfig.minVolume) / (float) mRingerConfig.rampUpDuration;
                    mCurrentIncrementVolume = mRingerConfig.minVolume;
                    mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
                    mHandler.postDelayed(mRunnable, 1000);
                    if (DEBUG) log("Starting increasing ring");
                }
            });

            XposedHelpers.findAndHookMethod(clsRingtonePlayer, "handleStop", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mHandler != null) {
                        if (DEBUG) log("Removing increasing ring callback");
                        mHandler.removeCallbacks(mRunnable);
                    }
                }
            });

        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void setVolume(float volume) {
        MediaPlayer player = (MediaPlayer) XposedHelpers.getObjectField(mRingtone, "mLocalPlayer");
        if (player != null) {
            player.setVolume(volume, volume);
        } else if (XposedHelpers.getBooleanField(mRingtone, "mAllowRemote")) {
            Object remotePlayer = XposedHelpers.getObjectField(mRingtone, "mRemotePlayer");
            if (remotePlayer != null) {
                try {
                    XposedHelpers.callMethod(remotePlayer, "setPlaybackProperties",
                            XposedHelpers.getObjectField(mRingtone, "mRemoteToken"),
                            volume, false);
                } catch (Throwable t) {
                    GravityBox.log(TAG, "setVolume: ", t);
                }
            }
        }
    }
}
