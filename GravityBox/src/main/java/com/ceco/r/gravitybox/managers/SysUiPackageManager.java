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
package com.ceco.r.gravitybox.managers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PatternMatcher;

import com.ceco.r.gravitybox.GravityBox;

import de.robv.android.xposed.XposedBridge;

public class SysUiPackageManager {
    private static final String TAG="GB:PackageManager";
    private static final String PKG_UNLOCKER = "com.ceco.gravitybox.unlocker";
    private static final String ACTION_UNLOCKER_INSTALLED = "gravitybox.intent.action.UNLOCKER_INSTALLED";
    private static boolean DEBUG = false;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    SysUiPackageManager(Context context) {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart(PKG_UNLOCKER, PatternMatcher.PATTERN_LITERAL);

        BroadcastReceiver broadcasReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) &&
                        intent.getData() != null &&
                        PKG_UNLOCKER.equals(intent.getData().getSchemeSpecificPart()) &&
                        !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    Intent i = new Intent(ACTION_UNLOCKER_INSTALLED);
                    i.setPackage(GravityBox.PACKAGE_NAME);
                    if (DEBUG) log("GravityBox unlocker installed. Sending: " + i);
                    context.sendBroadcast(i);
                }
            }
        };
        context.registerReceiver(broadcasReceiver, intentFilter, Context.RECEIVER_EXPORTED);

        if (DEBUG) log("PACKAGE_ADDED receiver registered");
    }
}
