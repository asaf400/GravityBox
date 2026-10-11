/*
 * Copyright (C) 2026 GravityBox contributors
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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Insets;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Android 15+ draws window content behind the action bar. Pad the content so
 * lists stay below the title and above the navigation bar.
 */
public final class ActivityContentInsets {
    private ActivityContentInsets() {}

    static void install(Activity activity) {
        install(activity.getWindow(), showsActionBar(activity));
    }

    /**
     * Nested PreferenceScreens open in their own full-screen dialog after
     * onPreferenceTreeClick returns, so the dialog window needs the same padding.
     */
    public static void onPreferenceTreeClick(Preference pref) {
        if (!(pref instanceof PreferenceScreen)) {
            return;
        }
        final PreferenceScreen screen = (PreferenceScreen) pref;
        final View anchor = screen.getContext() instanceof Activity
                ? ((Activity) screen.getContext()).getWindow().getDecorView() : null;
        final Runnable apply = () -> {
            final Dialog dialog = screen.getDialog();
            if (dialog != null && dialog.getWindow() != null) {
                install(dialog.getWindow(), true);
            }
        };
        if (anchor != null) {
            anchor.post(apply);
        } else {
            apply.run();
        }
    }

    private static void install(Window window, boolean hasActionBar) {
        if (window == null) {
            return;
        }
        final View content = window.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        content.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> apply(window, content, hasActionBar));
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            apply(window, content, hasActionBar);
            return insets;
        });
        content.post(() -> apply(window, content, hasActionBar));
    }

    private static void apply(Window window, View content, boolean hasActionBar) {
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;

        final WindowInsets insets = window.getDecorView().getRootWindowInsets();
        if (insets != null) {
            final Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            final int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
            left = bars.left;
            right = bars.right;
            bottom = Math.max(bars.bottom, imeBottom);
            top = bars.top;
        }

        // The action bar already includes the status-bar inset and is drawn
        // over the content, so the list has to start at the bar's bottom edge.
        final View actionBar = actionBarContainer(window);
        if (actionBar != null && actionBar.getHeight() > 0) {
            top = actionBar.getBottom();
        } else if (hasActionBar) {
            top += actionBarSize(window.getContext());
        }

        if (content.getPaddingLeft() != left || content.getPaddingTop() != top
                || content.getPaddingRight() != right || content.getPaddingBottom() != bottom) {
            content.setPadding(left, top, right, bottom);
        }
    }

    private static View actionBarContainer(Window window) {
        // android.R.id.action_bar_container is hidden from the SDK.
        final int frameworkId = window.getContext().getResources().getIdentifier(
                "action_bar_container", "id", "android");
        View bar = frameworkId != 0 ? window.findViewById(frameworkId) : null;
        if (bar == null) {
            bar = window.findViewById(androidx.appcompat.R.id.action_bar_container);
        }
        return bar;
    }

    private static boolean showsActionBar(Activity activity) {
        if (activity instanceof AppCompatActivity
                && ((AppCompatActivity) activity).getSupportActionBar() != null) {
            return true;
        }
        if (activity.getActionBar() != null) {
            return true;
        }
        final TypedValue tv = new TypedValue();
        final boolean noTitle = activity.getTheme().resolveAttribute(
                android.R.attr.windowNoTitle, tv, true) && tv.data != 0;
        final boolean actionBar = activity.getTheme().resolveAttribute(
                android.R.attr.windowActionBar, tv, true) && tv.data != 0;
        return actionBar && !noTitle;
    }

    private static int actionBarSize(Context context) {
        final TypedValue tv = new TypedValue();
        if (!context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)
                && !context.getTheme().resolveAttribute(
                        androidx.appcompat.R.attr.actionBarSize, tv, true)) {
            return 0;
        }
        return TypedValue.complexToDimensionPixelSize(
                tv.data, context.getResources().getDisplayMetrics());
    }
}
