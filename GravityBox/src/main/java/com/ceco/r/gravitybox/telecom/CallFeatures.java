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
package com.ceco.r.gravitybox.telecom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ceco.r.gravitybox.GravityBox;
import com.ceco.r.gravitybox.GravityBoxSettings;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.PowerManager.WakeLock;
import android.telecom.TelecomManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallFeatures {
    private static final String TAG = "GB:CallFeatures";
    private static final boolean DEBUG = false;

    private static final String CLASS_CALLS_MANAGER = "com.android.server.telecom.CallsManager";
    private static final String CLASS_CALL = "com.android.server.telecom.Call";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static CallFeatures init(XSharedPreferences prefs, ClassLoader classLoader) {
        return new CallFeatures(prefs, classLoader);
    }

    private int mFlipAction = GravityBoxSettings.PHONE_FLIP_ACTION_NONE;
    private Set<String> mCallVibrations;
    private Context mContext;
    private SensorManager mSensorManager;
    private boolean mSensorListenerAttached = false;
    private Object mIncomingCall;
    private Object mOutgoingCall;
    private List<Object> mActiveCallList = new ArrayList<>();
    private Vibrator mVibrator;
    private Handler mHandler;
    private WakeLock mWakeLock;

    private CallFeatures() { /* must be created by calling init() */ }

    private CallFeatures(XSharedPreferences prefs, ClassLoader classLoader) {
        initPrefs(prefs);
        createHooks(classLoader);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_PHONE_FLIP)) {
                mFlipAction = Integer.valueOf(intent.getStringExtra(GravityBoxSettings.EXTRA_PHONE_FLIP));
                if (DEBUG) log("mFlipAction=" + mFlipAction);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CALL_VIBRATIONS)) {
                mCallVibrations = new HashSet<>(intent.getStringArrayListExtra(
                        GravityBoxSettings.EXTRA_CALL_VIBRATIONS));
                if (DEBUG) log("mCallVibrations=" + mCallVibrations);
            }
        }
    };

    private void initPrefs(XSharedPreferences prefs) {
        mCallVibrations = prefs.getStringSet(
                GravityBoxSettings.PREF_KEY_CALL_VIBRATIONS, new HashSet<>());
        if (DEBUG) log("mCallVibrations = " + mCallVibrations.toString());

        mFlipAction = GravityBoxSettings.PHONE_FLIP_ACTION_NONE;
        try {
            mFlipAction = Integer.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_PHONE_FLIP, "0"));
            if (DEBUG) log("mFlipAction = " + mFlipAction);
        } catch (NumberFormatException e) {
            GravityBox.log(TAG, e);
        }
    }

    private void createHooks(ClassLoader classLoader) {
        Class<?> clsCallsManager = XposedHelpers.findClass(CLASS_CALLS_MANAGER, classLoader);

        XposedBridge.hookAllConstructors(clsCallsManager, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                onCallsManagerCreated(param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(clsCallsManager, "addCall", CLASS_CALL, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                onCallAdded(param.args[0]);
            }
        });

        XposedHelpers.findAndHookMethod(clsCallsManager, "setCallState",
                CLASS_CALL, int.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                onCallStateChanged(param.args[0], (int)param.args[1]);
            }
        });
    }

    private void onCallsManagerCreated(Object callsManager) {
        if (DEBUG) log("onCallsManagerCreated()");
        mContext = (Context) XposedHelpers.getObjectField(callsManager, "mContext");
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHandler = new Handler();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock  = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        IntentFilter intentFilter = new IntentFilter(GravityBoxSettings.ACTION_PREF_CALL_FEATURES_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    private void onCallAdded(Object call) {
        int state = (int) XposedHelpers.callMethod(call, "getState");
        if (DEBUG) log("onCallAdded: state = " + CallState.toString(state));
        onCallStateChanged(call, state);
    }

    public void onCallStateChanged(Object call, int state) {
        if (DEBUG) log("onStateChanged: " +
                "is our incoming call: " + (call == mIncomingCall) +
                "; is our outgoing call: " + (call == mOutgoingCall) +
                "; state=" + CallState.toString(state));
        // keep track of active calls
        if (state == CallState.ACTIVE && !mActiveCallList.contains(call)) {
            mActiveCallList.add(call);
        }
        // flip actions for incoming call
        if (state == CallState.RINGING) {
            if (mIncomingCall == null) {
                mIncomingCall = call;
                attachSensorListener();
            }
        } else if (call == mIncomingCall) {
            mIncomingCall = null;
            detachSensorListener();
        }
        // vibrate for waiting call
        if (state == CallState.RINGING && mActiveCallList.size() > 0 &&
                mCallVibrations.contains(GravityBoxSettings.CV_WAITING)) {
            if (DEBUG) log("Vibrating for waiting incoming call");
            vibrate(200, 300, 500);
        }
        // register outgoing call
        if (state == CallState.DIALING && mOutgoingCall == null) {
            mOutgoingCall = call;
        }
        // vibrate on outgoing connected and periodic
        if (state == CallState.ACTIVE && call == mOutgoingCall) {
            if (mCallVibrations.contains(GravityBoxSettings.CV_CONNECTED)) {
                if (DEBUG) log("Outgoing call connected; executing vibrate on call connected");
                vibrate(100, 0, 0);
            }
            if (mCallVibrations.contains(GravityBoxSettings.CV_PERIODIC) &&
                    mHandler != null) {
                if (DEBUG) log("Outgoing call connected; starting periodic vibrations");
                mHandler.postDelayed(mPeriodicVibrator, 45000);
                if (mWakeLock != null) {
                    mWakeLock.acquire(46000);
                    if (DEBUG) log("Partial Wake Lock acquired");
                }
            }
        }
        // handle call disconnected
        if (state == CallState.DISCONNECTED) {
            mActiveCallList.remove(call);
            if (mCallVibrations.contains(GravityBoxSettings.CV_DISCONNECTED)) {
                if (DEBUG) log("Call disconnected; executing vibrate on call disconnected");
                vibrate(50, 100, 50);
            }
            if (call == mOutgoingCall) {
                if (DEBUG) log("Our outgoing call disconnected");
                mOutgoingCall = null;
                if (mHandler != null) {
                    mHandler.removeCallbacks(mPeriodicVibrator);
                }
                if (mWakeLock != null && mWakeLock.isHeld()) {
                    mWakeLock.release();
                    if (DEBUG) log("Partial Wake Lock released");
                }
            }
        }
    }

    private PhoneSensorEventListener mPhoneSensorEventListener = 
            new PhoneSensorEventListener(new PhoneSensorEventListener.ActionHandler() {
        @Override
        public void onFaceUp() {
            if (DEBUG) log("PhoneSensorEventListener.onFaceUp");
            // do nothing
        }

        @Override
        public void onFaceDown() {
            if (DEBUG) log("PhoneSensorEventListener.onFaceDown");

            try {
                switch (mFlipAction) {
                    case GravityBoxSettings.PHONE_FLIP_ACTION_MUTE:
                        if (DEBUG) log("Muting call");
                        silenceRinger();
                        break;
                    case GravityBoxSettings.PHONE_FLIP_ACTION_DISMISS:
                        if (DEBUG) log("Rejecting call");
                        rejectCall(mIncomingCall);
                        break;
                    case GravityBoxSettings.PHONE_FLIP_ACTION_NONE:
                    default:
                        // do nothing
                }
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    });

    private void attachSensorListener() {
        if (mSensorManager == null || 
                mSensorListenerAttached ||
                mFlipAction == GravityBoxSettings.PHONE_FLIP_ACTION_NONE) return;

        mPhoneSensorEventListener.reset();
        mSensorManager.registerListener(mPhoneSensorEventListener, 
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 
                SensorManager.SENSOR_DELAY_NORMAL);
        mSensorListenerAttached = true;

        if (DEBUG) log("Sensor listener attached");
    }

    private void detachSensorListener() {
        if (mSensorManager == null || !mSensorListenerAttached) return;

        mSensorManager.unregisterListener(mPhoneSensorEventListener);
        mSensorListenerAttached = false;

        if (DEBUG) log("Sensor listener detached");
    }

    @SuppressLint("MissingPermission")
    private void silenceRinger() {
        try {
            TelecomManager tm = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            tm.silenceRinger();
        } catch(Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void rejectCall(Object call) {
        if (call == null) return;

        try {
            XposedHelpers.callMethod(call, "reject", false, null);
            if (DEBUG) log("Call rejected");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void vibrate(int v1, int p1, int v2) {
        if (mVibrator == null) return;

        long[] pattern = new long[] { 0, v1, p1, v2 };
        mVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    private Runnable mPeriodicVibrator = new Runnable() {
        @Override
        public void run() {
            if (mWakeLock != null) {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                mWakeLock.acquire(61000);
                if (DEBUG) log("Partial Wake Lock timeout extended");
            }
            vibrate(50, 0, 0);
            mHandler.postDelayed(this, 60000);
        }
    };
}
