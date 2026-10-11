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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import de.robv.android.xposed.XposedBridge;

public class BroadcastMediator {
    public static final String TAG="GB:BroadcastMediator";
    private static boolean DEBUG = false;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public interface Receiver {
        void onBroadcastReceived(Context context, Intent intent);
    }

    private static class Subscriber {
        Receiver receiver;
        List<String> actions;
        Subscriber(Receiver receiver, List<String> actions) {
            this.receiver = receiver;
            this.actions = actions;
        }
    }

    private Context mContext;
    private final List<Subscriber> mSubscribers;
    private final IntentFilter mIntentFilter;
    private boolean mInternalReceiverRegistered;

    BroadcastMediator() {
        mSubscribers = new ArrayList<>();
        mIntentFilter = new IntentFilter();
        if (DEBUG) log("BroadcastMediator created");
    }

    void setContext(Context context) {
        if (DEBUG) log("Received context");
        mContext = context;
        if (mIntentFilter.countActions() > 0) {
            registerReceiverInternal();
        }
    }

    /**
     * Subscribes receiver to receive broadcasts represented by actions of interest
     * @param receiver - listener for receiving broadcast
     * @param actions - actions of interest
     */
    public void subscribe(Receiver receiver, List<String> actions) {
        synchronized (mSubscribers) {
            final int oldActionCount = mIntentFilter.countActions();
            for (String action : actions) {
                if (!mIntentFilter.hasAction(action)) {
                    mIntentFilter.addAction(action);
                }
            }
            mSubscribers.add(new Subscriber(receiver, actions));
            if (DEBUG) log("subscribing receiver: " + receiver);
            if (oldActionCount != mIntentFilter.countActions()) {
                registerReceiverInternal();
            }
        }
    }

    private void registerReceiverInternal() {
        if (mContext == null) return;
        if (mInternalReceiverRegistered) {
            mContext.unregisterReceiver(mReceiverInternal);
            mInternalReceiverRegistered = false;
            if (DEBUG) log("reisterReceiverInternal: old internal receiver unregistered");
        }
        mContext.registerReceiver(mReceiverInternal, mIntentFilter, Context.RECEIVER_EXPORTED);
        mInternalReceiverRegistered = true;
        if (DEBUG) log("reisterReceiverInternal: new internal receiver registered");
    }

    /**
     * Subscribes receiver to receive broadcasts represented by actions of interest
     * @param receiver - to receive broadcast
     * @param actions - actions of interest
     */
    public void subscribe(Receiver receiver, String... actions) {
        subscribe(receiver, Arrays.asList(actions));
    }

    /**
     * Unsubscribes receiver
     * @param receiver - receiver to unsubscribe
     */
    public void unsubscribe(Receiver receiver) {
        if (DEBUG) log("unsubscribing receiver: " + receiver);
        synchronized (mSubscribers) {
            List<Subscriber> toRemove = mSubscribers.stream()
                    .filter(s -> s.receiver == receiver)
                    .collect(Collectors.toList());
            if (!toRemove.isEmpty()) {
                mSubscribers.removeAll(toRemove);
            }
        }
    }

    private BroadcastReceiver mReceiverInternal = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mSubscribers) {
                List<Receiver> toNotify = mSubscribers.stream()
                        .filter(s -> s.actions.contains(intent.getAction()))
                        .map(s -> s.receiver)
                        .collect(Collectors.toList());
                toNotify.forEach(r -> {
                    if (DEBUG) log("Notifying listener: " + r +
                            "; action=" + intent.getAction());
                    r.onBroadcastReceived(context, intent);
                });
            }
        }
    };

}
