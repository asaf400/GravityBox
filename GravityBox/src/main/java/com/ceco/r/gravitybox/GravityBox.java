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

import android.os.Build;

import com.ceco.r.gravitybox.managers.FrameworkManagers;
import com.ceco.r.gravitybox.managers.SysUiManagers;
import com.ceco.r.gravitybox.managers.SysUiTunerManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GravityBox implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    public static final String PACKAGE_NAME = GravityBox.class.getPackage().getName();
    public static String MODULE_PATH = null;
    private static final File prefsFileProt = new File("/data/user_de/0/com.ceco.r.gravitybox/shared_prefs/com.ceco.r.gravitybox_preferences.xml");
    private static final File qhPrefsFileProt = new File("/data/user_de/0/com.ceco.r.gravitybox/shared_prefs/quiet_hours.xml");
    private static final File uncPrefsFileProt = new File("/data/user_de/0/com.ceco.r.gravitybox/shared_prefs/ledcontrol.xml");
    private static final File tunerPrefsFileProt = new File("/data/user_de/0/com.ceco.r.gravitybox/shared_prefs/tuner.xml");
    private static XSharedPreferences prefs;
    private static XSharedPreferences qhPrefs;
    private static XSharedPreferences uncPrefs;
    private static XSharedPreferences tunerPrefs;
    private static boolean LOG_ERRORS;

    public static void log(String tag, String message, Throwable t) {
        if (LOG_ERRORS) {
            if (message != null) {
                XposedBridge.log(tag + ": " + message);
            }
            if (t != null) {
                XposedBridge.log(t);
            }
        }
    }

    public static void log(String tag, String message) {
        log(tag, message, null);
    }

    public static void log(String tag, Throwable t) {
        log(tag, null, t);
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        if (Build.VERSION.SDK_INT != 33) {
            XposedBridge.log("!!! GravityBox you are running is not designed for "
                    + "Android SDK " + Build.VERSION.SDK_INT + " !!!");
            return;
        }

        MODULE_PATH = startupParam.modulePath;
        if (XposedBridge.getXposedVersion() < 93) {
            prefs = new XSharedPreferences(prefsFileProt);
            uncPrefs = new XSharedPreferences(uncPrefsFileProt);
            qhPrefs = new XSharedPreferences(qhPrefsFileProt);
            tunerPrefs = new XSharedPreferences(tunerPrefsFileProt);
        } else {
            prefs = new XSharedPreferences(PACKAGE_NAME);
            prefs.makeWorldReadable();
            prefs.reload();
            uncPrefs = new XSharedPreferences(PACKAGE_NAME, "ledcontrol");
            uncPrefs.makeWorldReadable();
            uncPrefs.reload();
            qhPrefs = new XSharedPreferences(PACKAGE_NAME, "quiet_hours");
            qhPrefs.makeWorldReadable();
            qhPrefs.reload();
            tunerPrefs = new XSharedPreferences(PACKAGE_NAME, "tuner");
            tunerPrefs.makeWorldReadable();
            tunerPrefs.reload();
        }
        LOG_ERRORS = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LOG_ERRORS, false);

        if (startupParam.startsSystemServer) {
            XposedBridge.log("GB:Hardware: " + Build.HARDWARE);
            XposedBridge.log("GB:Product: " + Build.PRODUCT);
            XposedBridge.log("GB:Device manufacturer: " + Build.MANUFACTURER);
            XposedBridge.log("GB:Device brand: " + Build.BRAND);
            XposedBridge.log("GB:Device model: " + Build.MODEL);
            XposedBridge.log("GB:Device: " + Build.DEVICE);
            XposedBridge.log("GB:Device type: " + (Utils.isTablet() ? "tablet" : "phone"));
            XposedBridge.log("GB:Is MTK device: " + Utils.isMtkDevice());
            XposedBridge.log("GB:Is Xperia device: " + Utils.isXperiaDevice());
            XposedBridge.log("GB:Is Moto XT device: " + Utils.isMotoXtDevice());
            XposedBridge.log("GB:Is OxygenOS ROM: " + Utils.isOxygenOsRom());
            XposedBridge.log("GB:Has telephony support: " + Utils.hasTelephonySupport());
            XposedBridge.log("GB:Has Gemini support: " + Utils.hasGeminiSupport());
            XposedBridge.log("GB:Android SDK: " + Build.VERSION.SDK_INT);
            XposedBridge.log("GB:Android Release: " + Build.VERSION.RELEASE);
            XposedBridge.log("GB:ROM: " + Build.DISPLAY);
            XposedBridge.log("GB:Preferences: " + prefs.getFile().getParent());
            XposedBridge.log("GB:Error logging: " + LOG_ERRORS);
        }

        SysUiTunerManager.initUserItemsCache(tunerPrefs);
        SystemWideResources.initResources(prefs, tunerPrefs);

        // Common
        ModInputMethod.initZygote(prefs);
        PhoneWrapper.initZygote(prefs);
        ModTelephony.initZygote(prefs);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT != 33) {
            return;
        }

        if (lpparam.packageName.equals("android") &&
                lpparam.processName.equals("android")) {
            FrameworkManagers.initAndroid(lpparam.classLoader);
            ModVolumeKeySkipTrack.initAndroid(prefs, lpparam.classLoader);
            ModHwKeys.initAndroid(prefs, lpparam.classLoader);
            ModExpandedDesktop.initAndroid(prefs, lpparam.classLoader);
            ModAudio.initAndroid(prefs, qhPrefs, lpparam.classLoader);
            PermissionGranter.initAndroid(lpparam.classLoader);
            ModLowBatteryWarning.initAndroid(prefs, qhPrefs, lpparam.classLoader);
            ModDisplay.initAndroid(prefs, lpparam.classLoader);
            ConnectivityServiceWrapper.initAndroid(lpparam.classLoader);
            ModViewConfig.initAndroid(prefs, lpparam.classLoader);
            ModPower.initAndroid(prefs, qhPrefs, lpparam.classLoader);
            ModLedControl.initAndroid(prefs, uncPrefs, qhPrefs, lpparam.classLoader);
            ModTrustManager.initAndroid(prefs, lpparam.classLoader);
            ModFingerprint.initAndroid(prefs, lpparam.classLoader);
            ModActivityManager.initAndroid(lpparam.classLoader);
        }

        // Force reloading of preferences for SystemUI and prepare Resource interceptor
        if (lpparam.packageName.equals(ModStatusBar.PACKAGE_NAME)) {
            prefs.reload();
            qhPrefs.reload();
            uncPrefs.reload();
            tunerPrefs.reload();
            SysUiManagers.init();
        }

        if (lpparam.packageName.equals(SystemPropertyProvider.PACKAGE_NAME)) {
            SystemPropertyProvider.init(prefs, qhPrefs, tunerPrefs, lpparam.classLoader);
        }

        // Common
        if (lpparam.packageName.equals(ModLowBatteryWarning.PACKAGE_NAME)) {
            ModLowBatteryWarning.init(prefs, qhPrefs, lpparam.classLoader);
        }

        if (ModDialer.PACKAGE_NAMES.contains(lpparam.packageName) && !Utils.isOxygenOsRom()) {
            ModDialer.init(prefs, qhPrefs, lpparam.classLoader,
                    lpparam.packageName, lpparam.appInfo.targetSdkVersion);
        }

        if (lpparam.packageName.equals(ModQsTiles.PACKAGE_NAME) &&
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_ENABLE, false)) {
            ModQsTiles.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModStatusbarColor.PACKAGE_NAME)) {
            ModStatusbarColor.init(lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModStatusBar.PACKAGE_NAME)) {
            ModStatusBar.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModPowerMenu.PACKAGE_NAME) && !Utils.isSamsungRom()) {
            ModPowerMenu.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModPieControls.PACKAGE_NAME)) {
            ModPieControls.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModNavigationBar.PACKAGE_NAME)
                && prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_OVERRIDE, false)) {
            ModNavigationBar.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModLockscreen.PACKAGE_NAME)) {
            ModLockscreen.init(prefs, qhPrefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModSmartRadio.PACKAGE_NAME) &&
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false)) {
            ModSmartRadio.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModDownloadProvider.PACKAGE_NAME)) {
            ModDownloadProvider.init(prefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModRinger.PACKAGE_NAME) && !Utils.isSamsungRom()) {
            ModRinger.init(prefs, qhPrefs, lpparam.classLoader);
        }

        if (lpparam.packageName.equals(ModLedControl.PACKAGE_NAME_SYSTEMUI)) {
            ModLedControl.init(prefs, lpparam.classLoader);
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_MASTER_SWITCH, false)) {
                ModLedControl.initHeadsUp(prefs, uncPrefs, lpparam.classLoader);
            }
        }

        if (lpparam.packageName.equals(ModTelecom.PACKAGE_NAME)) {
            ModTelecom.init(prefs, lpparam.classLoader);
        }

        if (Utils.isOxygenOsRom()) {
            if (lpparam.packageName.equals((ModDialerOOS.PACKAGE_NAME_DIALER))) {
                ModDialerOOS.initDialer(prefs, lpparam.classLoader);
            }
        }

        if (lpparam.packageName.equals(ModVolumePanel.PACKAGE_NAME)) {
            ModVolumePanel.init(prefs, lpparam.classLoader);
        }
    }
}

