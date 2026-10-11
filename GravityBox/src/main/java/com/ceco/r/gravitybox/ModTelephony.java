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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModTelephony {
    private static final String TAG = "GB:ModTelephony";

    private static final String CLASS_SERVICE_STATE_TRACKER = 
            "com.android.internal.telephony.ServiceStateTracker";
    private static final String CLASS_SERVICE_STATE = "android.telephony.ServiceState";
    private static final String CLASS_SERVICE_STATE_EXT = "com.mediatek.op.telephony.ServiceStateExt";
    private static final String CLASS_PHONE_BASE = "com.android.internal.telephony.Phone";
    private static final boolean DEBUG = false;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    private static boolean mNationalRoamingEnabled;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_TELEPHONY_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_TELEPHONY_NATIONAL_ROAMING)) {
                    mNationalRoamingEnabled = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_TELEPHONY_NATIONAL_ROAMING, false);
                    if (DEBUG) log("mNationalRoamingEnabled: " + mNationalRoamingEnabled);
                }
            }
        }
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classServiceStateTracker = 
                    XposedHelpers.findClassIfExists(CLASS_SERVICE_STATE_TRACKER, classLoader);
            if (classServiceStateTracker == null) return;

            mNationalRoamingEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NATIONAL_ROAMING, false);

            XposedBridge.hookAllConstructors(classServiceStateTracker, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object phone = XposedHelpers.getObjectField(param.thisObject, "mPhone");
                    if (phone != null) {
                        Context context = (Context) XposedHelpers.callMethod(phone, "getContext");
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_TELEPHONY_CHANGED);
                        context.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
                        if (DEBUG) log("ServiceStateTracker constructed; broadcast receiver registered");
                    }
                }
            });

            if (Utils.hasGeminiSupport()) {
                XposedHelpers.findAndHookMethod(CLASS_SERVICE_STATE_EXT, classLoader, "ignoreDomesticRoaming", 
                        new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        if (DEBUG) log("ignoreDomesticRoaming: " + mNationalRoamingEnabled);
                        return mNationalRoamingEnabled;
                    }
                });
            } else {
                XposedHelpers.findAndHookMethod(classServiceStateTracker, "isOperatorConsideredNonRoaming",
                        CLASS_SERVICE_STATE, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!mNationalRoamingEnabled) return;

                        boolean result = (Boolean) param.getResult();
                        result = result || equalsMcc(param.args[0], result);
                        if (DEBUG) log("isOperatorConsideredNonRoaming: " + result);
                        param.setResult(result);
                    }
                });

                XposedHelpers.findAndHookMethod(classServiceStateTracker, "isOperatorConsideredRoaming",
                        CLASS_SERVICE_STATE, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!mNationalRoamingEnabled) return;

                        boolean result = (boolean) param.getResult();
                        result = result && !equalsMcc(param.args[0], result);
                        if (DEBUG) log("isOperatorConsideredRoaming: " + result);
                        param.setResult(result);
                    }
                });

                XposedHelpers.findAndHookMethod(CLASS_PHONE_BASE, classLoader,
                        "isMccMncMarkedAsRoaming", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!mNationalRoamingEnabled) return;

                        boolean result = (boolean) param.getResult();
                        result = result && !equalsMcc((String)param.args[0], result);
                        if (DEBUG) log("isMccMncMarkedAsRoaming: " + result);
                        param.setResult(result);
                    }
                });
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static boolean equalsMcc(Object serviceState, boolean defaultRetVal) {
        try {
            String operatorNumeric = (String) XposedHelpers.callMethod(
                    serviceState, "getOperatorNumeric");
            return equalsMcc(operatorNumeric, defaultRetVal);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return defaultRetVal;
        }
    }

    private static boolean equalsMcc(String operatorNumeric, boolean defaultRetVal) {
        try {
            String simNumeric = SystemPropertyProvider.get("gsm.sim.operator.numeric", "");

            boolean equalsMcc = defaultRetVal;
            try {
                equalsMcc = simNumeric.substring(0, 3).equals(operatorNumeric.substring(0, 3));
                if (DEBUG) log("equalsMcc: simNumeric=" + simNumeric +
                        "; operatorNumeric=" + operatorNumeric + "; equalsMcc=" + equalsMcc);
            } catch (Exception ignored) { }

            return equalsMcc;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return defaultRetVal;
        }
    }
}
