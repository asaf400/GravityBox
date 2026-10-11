/*
 * Copyright (C) 2016 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.r.gravitybox.telecom;

import com.ceco.r.gravitybox.GravityBox;
import com.ceco.r.gravitybox.ModTelecom;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MissedCallNotifier {
    private static final String TAG = "GB:MissedCallNotifier";
    private static final boolean DEBUG = false;
    private static final int MISSED_CALL_NOTIF_ID = 1;
    private static final String EXTRA_FROM_GB = "fromGb";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static MissedCallNotifier init(ClassLoader classLoader) {
        return new MissedCallNotifier(classLoader);
    }

    private Notification mNotifOnNextScreenOff;

    private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mNotifOnNextScreenOff != null) {
                try {
                    NotificationManager nm = 
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotifOnNextScreenOff.extras.putBoolean(EXTRA_FROM_GB, true);
                    nm.notify(MISSED_CALL_NOTIF_ID, mNotifOnNextScreenOff);
                } catch (Throwable t) {
                    GravityBox.log(TAG, t);
                }
                mNotifOnNextScreenOff = null;
            }
            context.unregisterReceiver(this);
        }
    };

    private MissedCallNotifier() { /* must be created by calling init() */ }

    private MissedCallNotifier(ClassLoader classLoader) {
        createHooks(classLoader);
    }

    private void createHooks(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notify",
                    String.class, int.class, Notification.class, notifyHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notifyAsUser",
                    String.class, int.class, Notification.class, UserHandle.class, notifyHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "cancel",
                    String.class, int.class, cancelHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "cancelAsUser",
                    String.class, int.class, UserHandle.class, cancelHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "cancelAll", cancelHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private XC_MethodHook notifyHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) {
            // workaround for AOSP bug preventing LED from working for missed calls
            try {
                final Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                final String pkgName = context.getPackageName();
                final Notification n = (Notification) param.args[2];
                if (DEBUG) log("notifyHookPkg: " + pkgName + "; ID=" + param.args[1]);
                if (!n.extras.containsKey(EXTRA_FROM_GB) && pkgName.equals(ModTelecom.PACKAGE_NAME) && 
                        (Integer)param.args[1] == MISSED_CALL_NOTIF_ID) {
                    if (mNotifOnNextScreenOff == null) {
                        context.registerReceiver(mScreenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF),
                                Context.RECEIVER_EXPORTED);
                        if (DEBUG) log("Scheduled missed call notification for next screen off");
                    }
                    mNotifOnNextScreenOff = n;
                }
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    };

    private XC_MethodHook cancelHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) {
            try {
                final Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                final String pkgName = context.getPackageName();
                final boolean isMissedCallNotifOrAll = pkgName.equals(ModTelecom.PACKAGE_NAME) &&
                        (param.args.length == 0 || (Integer) param.args[1] == MISSED_CALL_NOTIF_ID);
                if (isMissedCallNotifOrAll && mNotifOnNextScreenOff != null) {
                    mNotifOnNextScreenOff = null;
                    context.unregisterReceiver(mScreenOffReceiver);
                    if (DEBUG) log("Pending missed call notification canceled");
                }
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    };
}
