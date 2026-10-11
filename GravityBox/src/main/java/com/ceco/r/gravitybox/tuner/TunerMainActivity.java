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

package com.ceco.r.gravitybox.tuner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.TextView;

import com.ceco.r.gravitybox.ActivityContentInsets;
import com.ceco.r.gravitybox.GravityBoxActivity;
import com.ceco.r.gravitybox.R;
import com.ceco.r.gravitybox.SettingsManager;
import com.ceco.r.gravitybox.WorldReadablePrefs;
import com.ceco.r.gravitybox.managers.SysUiTunerManager;

public class TunerMainActivity extends GravityBoxActivity {
    public static final String PREF_KEY_LOCKED = "tunerLocked";
    public static final String PREF_KEY_ENABLED = "pref_tuner_enabled";
    public static final String EXTRA_UUID_REGISTERED = "uuidRegistered";
    public static final String EXTRA_TRIAL_COUNTDOWN = "tunerTrialCountdown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean uuidRegistered;
        int trialCountdown;

        if (getIntent() == null || !getIntent().hasExtra(EXTRA_UUID_REGISTERED) ||
                !getIntent().hasExtra(EXTRA_TRIAL_COUNTDOWN)) {
            finish();
            return;
        } else {
            uuidRegistered = getIntent().getBooleanExtra(EXTRA_UUID_REGISTERED, false);
            trialCountdown = getIntent().getIntExtra(EXTRA_TRIAL_COUNTDOWN, 0);
            if (!uuidRegistered && trialCountdown == 0) {
                finish();
                return;
            }
        }

        setContentView(R.layout.tuner_main_activity);

        TextView trialInfoView = findViewById(R.id.trial_info);
        if (!uuidRegistered) {
            trialInfoView.setText(String.format(getString(R.string.trial_info), trialCountdown));
            trialInfoView.setVisibility(View.VISIBLE);
        }
    }

    public static boolean isTunerLocked(Context context) {
        try {
            SharedPreferences prefs = SettingsManager.getInstance(context).getTunerPrefs();
            return prefs.getBoolean(PREF_KEY_LOCKED, false);
        } catch (Throwable t) {
            t.printStackTrace();
            return true;
        }
    }

    public static void lockTuner(final Context context, final boolean lock) {
        try {
            if (isTunerLocked(context) != lock) {
                final WorldReadablePrefs prefs = SettingsManager.getInstance(context).getTunerPrefs();
                prefs.edit().putBoolean(PREF_KEY_LOCKED, lock).commit();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static class PrefsFragment extends PreferenceFragment {
        protected static final String PREF_KEY_FRAMEWORK = "pref_tuner_framework";
        protected static final String PREF_KEY_SYSTEMUI = "pref_tuner_systemui";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesName("tuner");
            getPreferenceManager().setStorageDeviceProtected();
            addPreferencesFromResource(R.xml.tuner_main_activity_prefs);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen prefScreen, Preference pref) {
            ActivityContentInsets.onPreferenceTreeClick(pref);
            final String key = pref.getKey();
            if (PREF_KEY_FRAMEWORK.equals(key)) {
                Intent intent = new Intent(getActivity(), TunerCategoryActivity.class);
                intent.putExtra(SysUiTunerManager.EXTRA_TUNER_CATEGORY, "FRAMEWORK");
                startActivity(intent);
            } else if (PREF_KEY_SYSTEMUI.equals(key)) {
                Intent intent = new Intent(getActivity(), TunerCategoryActivity.class);
                intent.putExtra(SysUiTunerManager.EXTRA_TUNER_CATEGORY, "SYSTEMUI");
                startActivity(intent);
            }
            return super.onPreferenceTreeClick(prefScreen, pref);
        }
    }
}
