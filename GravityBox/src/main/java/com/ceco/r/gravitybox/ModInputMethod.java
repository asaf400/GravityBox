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

import static android.content.Context.RECEIVER_EXPORTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModInputMethod {
    public static final String TAG = "GB:ModVolKeyCursor";
    public static final String CLASS_IME_SERVICE = "android.inputmethodservice.InputMethodService";
    private static final boolean DEBUG = false;

    private static InputMethodService mService;
    private static int mVolKeyCursorControl;
    private static boolean mFullscreenImeDisabled;

    private static void log (String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_IME_VOL_KEY_CURSOR_CONTROL)) {
                mVolKeyCursorControl = intent.getIntExtra(
                        GravityBoxSettings.EXTRA_IME_VOL_KEY_CURSOR_CONTROL, 0);
                if (DEBUG) log("onReceive: mVolKeyCursorControl=" + mVolKeyCursorControl);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_IME_FULLSCREEN_DISABLE)) {
                mFullscreenImeDisabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_IME_FULLSCREEN_DISABLE, false);
                if (DEBUG) log("onReceive: mFullscreenImeDisabled=" + mFullscreenImeDisabled);
            }
        }
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("init");

        try {
            final Class<?> imeClass = XposedHelpers.findClassIfExists(CLASS_IME_SERVICE, classLoader);
            if (imeClass == null) return;

            mVolKeyCursorControl = Integer.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_VOL_KEY_CURSOR_CONTROL, "0"));
            mFullscreenImeDisabled = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_IME_FULLSCREEN_DISABLE, false);

            XposedHelpers.findAndHookMethod(imeClass, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    mService = (InputMethodService) param.thisObject;
                    mService.registerReceiver(mReceiver, new IntentFilter(
                            GravityBoxSettings.ACTION_PREF_IME_CHANGED), RECEIVER_EXPORTED);
                    if (DEBUG) log("IME service created");
                }
            });

            XposedHelpers.findAndHookMethod(imeClass, "onDestroy", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (mService != null) {
                        mService.unregisterReceiver(mReceiver);
                        mService = null;
                        if (DEBUG) log("IME service destroyed");
                    }
                }
            });

            XposedHelpers.findAndHookMethod(imeClass, "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    if (mService == null) {
                        if (DEBUG) log("onKeyDown: mService is null; exiting");
                        return;
                    }

                    int keyCode = ((KeyEvent) param.args[1]).getKeyCode();
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        if (mService.isInputViewShown() &&
                                mVolKeyCursorControl != GravityBoxSettings.VOL_KEY_CURSOR_CONTROL_OFF) {
                            int newKeyCode = mVolKeyCursorControl == GravityBoxSettings.VOL_KEY_CURSOR_CONTROL_ON_REVERSE ?
                                    KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                            mService.sendDownUpKeyEvents(newKeyCode);
                            param.setResult(true);
                            return;
                        }
                        param.setResult(false);
                        return;
                    }

                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        if (mService.isInputViewShown() &&
                                mVolKeyCursorControl != GravityBoxSettings.VOL_KEY_CURSOR_CONTROL_OFF) {
                            int newKeyCode = mVolKeyCursorControl == GravityBoxSettings.VOL_KEY_CURSOR_CONTROL_ON_REVERSE ?
                                    KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                            mService.sendDownUpKeyEvents(newKeyCode);
                            param.setResult(true);
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(imeClass, "onKeyUp", int.class, KeyEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    if (mService == null) {
                        if (DEBUG) log("onKeyUp: mService is null; exiting");
                        return;
                    }
                    
                    int keyCode = ((KeyEvent) param.args[1]).getKeyCode();
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        if (mService.isInputViewShown() &&
                            mVolKeyCursorControl != GravityBoxSettings.VOL_KEY_CURSOR_CONTROL_OFF) {
                            param.setResult(true);
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(imeClass, "onEvaluateFullscreenMode", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    if (mFullscreenImeDisabled) {
                        param.setResult(false);
                        if (DEBUG) log("onEvaluateFullscreenMode: IME fullscreen mode disabled");
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }
}
