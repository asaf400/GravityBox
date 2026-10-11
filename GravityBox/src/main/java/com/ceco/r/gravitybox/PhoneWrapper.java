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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PhoneWrapper {
    private static final String TAG = "GB:PhoneWrapper";
    private static final boolean DEBUG = false;

    public static final int NT_WCDMA_PREFERRED = 0;             // GSM/WCDMA (WCDMA preferred) (2g/3g)
    public static final int NT_GSM_ONLY = 1;                    // GSM Only (2g)
    public static final int NT_WCDMA_ONLY = 2;                  // WCDMA ONLY (3g)
    public static final int NT_GSM_WCDMA_AUTO = 3;              // GSM/WCDMA Auto (2g/3g)
    public static final int NT_CDMA_EVDO = 4;                   // CDMA/EVDO Auto (2g/3g)
    public static final int NT_CDMA_ONLY = 5;                   // CDMA Only (2G)
    public static final int NT_EVDO_ONLY = 6;                   // Evdo Only (3G)
    public static final int NT_GLOBAL = 7;                      // GSM/WCDMA/CDMA Auto (2g/3g)
    public static final int NT_LTE_CDMA_EVDO = 8; 
    public static final int NT_LTE_GSM_WCDMA = 9;
    public static final int NT_LTE_CMDA_EVDO_GSM_WCDMA = 10;
    public static final int NT_LTE_ONLY = 11;
    public static final int NT_LTE_WCDMA = 12;
    public static final int NT_MODE_UNKNOWN = 100;

    // TD-SCDMA
    public static final int NT_TDSCDMA_ONLY = 13;                       // 3G only
    public static final int NT_TDSCDMA_WCDMA = 14;                      // 3G only
    public static final int NT_LTE_TDSCDMA = 15;                        // LTE
    public static final int NT_TDSCDMA_GSM = 16;                        // 2G/3G
    public static final int NT_LTE_TDSCDMA_GSM = 17;                    // LTE
    public static final int NT_TDSCDMA_GSM_WCDMA = 18;                  // 2G/3G
    public static final int NT_LTE_TDSCDMA_WCDMA = 19;                  // LTE
    public static final int NT_LTE_TDSCDMA_GSM_WCDMA = 20;              // LTE
    public static final int NT_TDSCDMA_CDMA_EVDO_GSM_WCDMA = 21;        // 2G/3G
    public static final int NT_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA = 22;    // LTE

    private static final String PREFERRED_NETWORK_MODE = "preferred_network_mode";

    public static final String ACTION_CHANGE_NETWORK_TYPE = "gravitybox.intent.action.CHANGE_NETWORK_TYPE";
    public static final String ACTION_NETWORK_TYPE_CHANGED = "gravitybox.intent.action.NETWORK_TYPE_CHANGED";
    public static final String ACTION_GET_CURRENT_NETWORK_TYPE = "gravitybox.intent.action.GET_CURRENT_NETWORK_TYPE";
    public static final String EXTRA_NETWORK_TYPE = "networkType";
    public static final String EXTRA_PHONE_ID = "phoneId";
    public static final String EXTRA_RECEIVER_TAG = "receiverTag";

    private static Class<?> mClsPhoneFactory;
    private static Class<?> mPhoneBaseClass;
    private static Class<?> mPhoneProxyClass;
    private static Class<?> mSystemProperties;
    private static Context mContext;
    private static int mSimSlot = 0;
    private static int mPhoneCount = -1;
    private static Boolean mHasMsimSupport = null;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public static String getNetworkModeNameFromValue(int networkMode) {
        switch(networkMode) {
            case NT_GSM_ONLY: return "GSM (2G)";
            case NT_WCDMA_PREFERRED: return "GSM/WCDMA Preferred (3G/2G)";
            case NT_GSM_WCDMA_AUTO: return "GSM/WCDMA Auto (2G/3G)";
            case NT_WCDMA_ONLY: return "WCDMA (3G)";
            case NT_CDMA_EVDO: return "CDMA/EvDo Auto";
            case NT_CDMA_ONLY: return "CDMA";
            case NT_EVDO_ONLY: return "EvDo";
            case NT_GLOBAL: return "GSM/WCDMA/CDMA Auto (2G/3G)";
            case NT_LTE_CDMA_EVDO: return "LTE (CDMA)";
            case NT_LTE_GSM_WCDMA: return "LTE (GSM)";
            case NT_LTE_CMDA_EVDO_GSM_WCDMA: return "LTE (Global)";
            default: return "Undefined";
        }
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_CHANGE_NETWORK_TYPE) &&
                    intent.hasExtra(EXTRA_NETWORK_TYPE)) {
                int networkType = intent.getIntExtra(EXTRA_NETWORK_TYPE, NT_WCDMA_PREFERRED);
                if (DEBUG) log("received ACTION_CHANGE_NETWORK_TYPE broadcast: networkType = " + networkType);
                setPreferredNetworkType(networkType);
            }
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED)) {
                mSimSlot = intent.getIntExtra(GravityBoxSettings.EXTRA_SIM_SLOT, 0);
                if (DEBUG) log("received ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED broadcast: " +
                                    "mSimSlot = " + mSimSlot);
                setPreferredNetworkType(getCurrentNetworkType(mSimSlot));
            }
            if (intent.getAction().equals(ACTION_GET_CURRENT_NETWORK_TYPE)) {
                int simSlot = intent.getIntExtra(EXTRA_PHONE_ID, mSimSlot);
                broadcastCurrentNetworkType(simSlot, getCurrentNetworkType(simSlot),
                        intent.getStringExtra(EXTRA_RECEIVER_TAG));
            }
        }
    };

    private static Class<?> getPhoneFactoryClass(ClassLoader classLoader) {
        return XposedHelpers.findClassIfExists("com.android.internal.telephony.PhoneFactory", classLoader);
    }

    private static Class<?> getPhoneBaseClass(ClassLoader classLoader) {
        return XposedHelpers.findClassIfExists("com.android.internal.telephony.Phone", classLoader);
    }

    private static Class<?> getPhoneProxyClass(ClassLoader classLoader) {
        return XposedHelpers.findClassIfExists("com.android.internal.telephony.PhoneProxy", classLoader);
    }

    private static Class<?> getTelephonyManagerClass() {
        return XposedHelpers.findClass("android.telephony.TelephonyManager", null);
    }

    private static String getMakePhoneMethodName() {
        if (Utils.hasGeminiSupport()) {
            return "makeDefaultPhones";
        } else if (hasMsimSupport()) {
            return "makeDefaultPhones";
        } else {
            return "makeDefaultPhone";
        }
    }

    private static Object getPhone() {
        if (mClsPhoneFactory == null) {
            return null;
        } else if (hasMsimSupport()) {
            return XposedHelpers.callStaticMethod(mClsPhoneFactory, "getPhone", mSimSlot);
        } else {
            return XposedHelpers.callStaticMethod(mClsPhoneFactory, "getDefaultPhone");
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("Entering init state");

        try {
            final Class<?> phoneFactoryClass = getPhoneFactoryClass(classLoader);
            final Class<?> phoneBaseClass = getPhoneBaseClass(classLoader);
            if (phoneFactoryClass == null || phoneBaseClass == null) return;

            mClsPhoneFactory = phoneFactoryClass;
            mPhoneBaseClass = phoneBaseClass;
            mPhoneProxyClass = getPhoneProxyClass(classLoader);
            mSystemProperties = XposedHelpers.findClass("android.os.SystemProperties", null);

            mSimSlot = 0;
            try {
                mSimSlot = Integer.valueOf(prefs.getString(
                        GravityBoxSettings.PREF_KEY_QS_NETWORK_MODE_SIM_SLOT, "0"));
            } catch (NumberFormatException nfe) {
                GravityBox.log(TAG, "Invalid value for SIM Slot preference: ", nfe);
            }
            if (DEBUG) log("mSimSlot = " + mSimSlot);

            XposedHelpers.findAndHookMethod(mClsPhoneFactory, getMakePhoneMethodName(), 
                    Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    mContext = (Context) param.args[0];
                    if (DEBUG) log("PhoneFactory makeDefaultPhones - phone wrapper initialized");
                    onInitialize();
                }
            });

            XC_MethodHook spntHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    int phoneId = XposedHelpers.getIntField(param.thisObject, "mPhoneId");
                    if (DEBUG) log("setPreferredNetworkType: networkType=" + param.args[0] +
                            "; phoneId=" + phoneId);
                    broadcastCurrentNetworkType(phoneId, (int)param.args[0], null);
                }
            };
            XposedHelpers.findAndHookMethod(mPhoneBaseClass, "setPreferredNetworkType",
                    int.class, Message.class, spntHook);
            if (mPhoneProxyClass != null) {
                XposedHelpers.findAndHookMethod(mPhoneProxyClass, "setPreferredNetworkType",
                        int.class, Message.class, spntHook);
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void onInitialize() {
        if (mContext != null) {
            IntentFilter intentFilter = new IntentFilter(ACTION_CHANGE_NETWORK_TYPE);
            intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED);
            intentFilter.addAction(ACTION_GET_CURRENT_NETWORK_TYPE);
            mContext.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        }
    }

    private static void setPreferredNetworkType(int networkType) {
        try {
            Object defPhone = getPhone();
            if (DEBUG) log("setPreferredNetworkType: phone=" + defPhone);
            if (defPhone == null) return;
            if (Utils.hasGeminiSupport()) {
                mSimSlot = (Integer) XposedHelpers.callMethod(defPhone, "get3GSimId");
                if (DEBUG) log("Gemini 3G SIM ID: " + mSimSlot);
                Class<?>[] paramArgs = new Class<?>[3];
                paramArgs[0] = int.class;
                paramArgs[1] = Message.class;
                paramArgs[2] = int.class;
                XposedHelpers.callMethod(defPhone, "setPreferredNetworkTypeGemini", 
                        paramArgs, networkType, null, mSimSlot);
            } else {
                int subId = (int) XposedHelpers.callMethod(defPhone, "getSubId");
                Settings.Global.putInt(mContext.getContentResolver(),
                        PREFERRED_NETWORK_MODE + subId, networkType);
                Class<?>[] paramArgs = new Class<?>[2];
                paramArgs[0] = int.class;
                paramArgs[1] = Message.class;
                XposedHelpers.callMethod(defPhone, "setPreferredNetworkType", paramArgs, networkType, null);
            }
            broadcastCurrentNetworkType(mSimSlot, networkType, null);
        } catch (Throwable t) {
            GravityBox.log(TAG, "setPreferredNetworkType failed: ", t);
        }
    }

    public static int getDefaultNetworkType() {
        try {
            int mode = (Integer) XposedHelpers.callStaticMethod(mSystemProperties, 
                "getInt", "ro.telephony.default_network", NT_WCDMA_PREFERRED);
            if (DEBUG) log("getDefaultNetworkMode: mode=" + mode);
            return mode;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return NT_WCDMA_PREFERRED;
        }
    }

    private static int getCurrentNetworkType(int phoneId) {
        try {
            int networkType = getDefaultNetworkType();
            Object[] phones = (Object[])XposedHelpers.callStaticMethod(mClsPhoneFactory, "getPhones");
            if (phoneId < phones.length) {
                int subId = (int) XposedHelpers.callMethod(phones[phoneId], "getSubId");
                if (DEBUG) log("getCurrentNetworkType: calculating network type for subId=" + subId);
                networkType = (int) XposedHelpers.callStaticMethod(mClsPhoneFactory,
                        "calculatePreferredNetworkType", mContext, subId);
            }
            if (DEBUG) log("getCurrentNetworkType: phoneId=" + phoneId +
                    "; networkType=" + getNetworkModeNameFromValue(networkType));
            return networkType;
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return NT_WCDMA_PREFERRED;
        }
    }

    private static void broadcastCurrentNetworkType(int phoneId, int networkType, String receiverTag) {
        try {
            Intent intent = new Intent(ACTION_NETWORK_TYPE_CHANGED);
            intent.putExtra(EXTRA_PHONE_ID, phoneId);
            intent.putExtra(EXTRA_NETWORK_TYPE, networkType);
            if (receiverTag != null) {
                intent.putExtra(EXTRA_RECEIVER_TAG, receiverTag);
            }
            mContext.sendBroadcast(intent);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    public static boolean isLteNetworkType(int networkType) {
        return (networkType >= NT_LTE_CDMA_EVDO &&
                networkType <= NT_LTE_WCDMA) ||
                networkType == NT_LTE_TDSCDMA ||
                networkType == NT_LTE_TDSCDMA_GSM ||
                networkType == NT_LTE_TDSCDMA_WCDMA ||
                networkType == NT_LTE_TDSCDMA_GSM_WCDMA ||
                networkType == NT_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA;
    }

    public static int getPhoneCount() {
        if (mPhoneCount != -1) return mPhoneCount;

        try {
            Object mtm = XposedHelpers.callStaticMethod(getTelephonyManagerClass(), "getDefault");
            mPhoneCount = (int) XposedHelpers.callMethod(mtm, "getPhoneCount");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            mPhoneCount = -1;
        }

        if (DEBUG) log("getPhoneCount: " + mPhoneCount);
        return mPhoneCount;
    }

    public static boolean hasMsimSupport() {
        if (mHasMsimSupport != null) return mHasMsimSupport;

        try {
            Object mtm = XposedHelpers.callStaticMethod(getTelephonyManagerClass(), "getDefault");
            mHasMsimSupport = (Boolean) XposedHelpers.callMethod(mtm, "isMultiSimEnabled") &&
                    getPhoneCount() > 1;
            if (DEBUG) log("isMultiSimEnabled: " +
                    XposedHelpers.callMethod(mtm, "isMultiSimEnabled"));
            if (DEBUG) log("getPhoneCount: " + getPhoneCount());
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            mHasMsimSupport = false;
        }

        if (DEBUG) log("hasMsimSupport: " + mHasMsimSupport);
        return mHasMsimSupport;
    }
}
