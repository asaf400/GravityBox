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
package com.ceco.r.gravitybox;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import com.ceco.r.gravitybox.ledcontrol.QuietHours;
import com.ceco.r.gravitybox.ledcontrol.QuietHoursActivity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialer {
    private static final String TAG = "GB:ModDialer";
    public static final List<String> PACKAGE_NAMES = new ArrayList<>(Arrays.asList(
            "com.google.android.dialer", "com.android.dialer"));

    private static final String CLASS_MAIN_ACTIVITY = "com.android.dialer.main.impl.MainActivity";

    private static final boolean DEBUG = false;

    private static QuietHours mQuietHours;

    private static final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQuietHours = new QuietHours(intent.getExtras());
                if (DEBUG) log("QuietHours updated");
            }
        }
    };

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static Method resolvePlayToneMethod(ClassLoader cl) {
        final String[] CLASS_NAMES = new String[] {
                "com.android.dialer.app.dialpad.DialpadFragment",
                "com.android.dialer.dialpadview.DialpadFragment",
                "ehl",
                "dxh",
                "dqc",
                "emy",
                "egh",
                "dyv"
        };
        for (String className : CLASS_NAMES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null) continue;
            if (DEBUG) log("resolvePlayToneMethod: found class=" + className);
            for (String realMethodName : new String[] { "playTone", "bd", "bm", "be", "s", "a" }) {
                Method m = XposedHelpers.findMethodExactIfExists(clazz, realMethodName,
                    int.class, int.class);
                if (m == null) continue;
                if (DEBUG) log("resolvePlayToneMethod: playTone=" + realMethodName);
                return m;
            }
        }
        return null;
    }

    public static void init(final XSharedPreferences prefs, final XSharedPreferences qhPrefs,
            final ClassLoader classLoader, final String packageName, int sdkVersion) {

        if (sdkVersion < 29) {
            log("SDK of Dialer app too old: " + sdkVersion);
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_MAIN_ACTIVITY, classLoader, "onResume",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (DEBUG) log("MainActivity: onResume");
                    Context ctx = (android.app.Activity) param.thisObject;
                    ctx.registerReceiver(mBroadcastReceiver,
                            new IntentFilter(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED),
                            Context.RECEIVER_EXPORTED);
                    Intent i = new Intent(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                    i.setComponent(new ComponentName(GravityBox.PACKAGE_NAME,
                            GravityBoxService.class.getName()));
                    ctx.startService(i);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_MAIN_ACTIVITY, classLoader, "onPause",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (DEBUG) log("MainActivity: onPause");
                    Context ctx = (android.app.Activity) param.thisObject;
                    ctx.unregisterReceiver(mBroadcastReceiver);
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, "MainActivity: incompatible version of Dialer app", t);
        }

        final Method playToneMethod = resolvePlayToneMethod(classLoader);
        if (playToneMethod != null) {
            XposedBridge.hookMethod(playToneMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (DEBUG) log("playTone");
                    if (mQuietHours != null &&
                            mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.DIALPAD)) {
                        param.setResult(null);
                    }
                }
            });
        } else {
            GravityBox.log(TAG, "playToneMethod: incompatible version of Dialer app");
        }
    }
}
