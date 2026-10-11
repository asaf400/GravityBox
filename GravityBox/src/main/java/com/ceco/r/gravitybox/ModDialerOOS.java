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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;

import com.ceco.r.gravitybox.ledcontrol.QuietHours;
import com.ceco.r.gravitybox.ledcontrol.QuietHoursActivity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialerOOS {
    public static final String PACKAGE_NAME_DIALER = "com.oneplus.dialer";
    public static final String CLASS_DIALER_SETTINGS_ACTIVITY = "com.android.dialer.oneplus.activity.OPDialerSettingsActivity";
    public static final String CLASS_IN_CALL_ACTIVITY = "com.android.incallui.InCallActivity";
    public static final String CLASS_DIALPAD_FRAGMENT = "com.android.dialer.dialpadview.OPDialerDialpadFragment";
    private static final String TAG = "GB:ModDialerOOS";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static QuietHours mQuietHours;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQuietHours = new QuietHours(intent.getExtras());
                if (DEBUG) log("QuietHours updated");
            }
        }
    };

    public static void initDialer(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("initDialer");

        try {
            XposedHelpers.findAndHookMethod(CLASS_DIALER_SETTINGS_ACTIVITY, classLoader,
                    "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_OOS_CALL_RECORDING, false)) {
                        enableCallRecording((Context) param.thisObject);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_IN_CALL_ACTIVITY, classLoader,
                    "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_OOS_CALL_RECORDING, false)) {
                        enableCallRecording((Context) param.thisObject);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_DIALPAD_FRAGMENT, classLoader,
                    "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (DEBUG) log("DialpadFragment: onResume");
                    Context ctx = (Context) XposedHelpers.callMethod(param.thisObject, "getActivity");
                    ctx.registerReceiver(mBroadcastReceiver,
                            new IntentFilter(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED),
                            Context.RECEIVER_EXPORTED);
                    Intent i = new Intent(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                    i.setComponent(new ComponentName(GravityBox.PACKAGE_NAME,
                            GravityBoxService.class.getName()));
                    ctx.startService(i);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_DIALPAD_FRAGMENT, classLoader,
                    "onPause", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (DEBUG) log("DialpadFragment: onPause");
                    Context ctx = (Context) XposedHelpers.callMethod(param.thisObject, "getActivity");
                    ctx.unregisterReceiver(mBroadcastReceiver);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_DIALPAD_FRAGMENT, classLoader,
                    "c", int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (DEBUG) log("DialpadFragment: playTone");
                    if (mQuietHours != null &&
                            mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.DIALPAD)) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void enableCallRecording(Context ctx) {
        if (ctx != null) {
            Settings.Global.putInt(ctx.getContentResolver(),
                    "op_voice_recording_supported_by_mcc", 1);
            if (DEBUG) log(ctx.getPackageName() + ": forcing op_voice_recording_supported_by_mcc");
        }
    }
}
