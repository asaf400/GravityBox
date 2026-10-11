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

package com.ceco.r.gravitybox.ledcontrol;

import com.ceco.r.gravitybox.ActivityContentInsets;
import com.ceco.r.gravitybox.R;
import com.ceco.r.gravitybox.GravityBoxActivity;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.TextView;

public class LedMainActivity extends GravityBoxActivity {
    public static final String EXTRA_UUID_REGISTERED = "uuidRegistered";
    public static final String EXTRA_TRIAL_COUNTDOWN = "uncTrialCountdown";

    private boolean mUuidRegistered;
    private int mTrialCountdown;
    private TextView mTrialInfoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent() == null || !getIntent().hasExtra(EXTRA_UUID_REGISTERED) ||
                !getIntent().hasExtra(EXTRA_TRIAL_COUNTDOWN)) {
            finish();
            return;
        } else {
            mUuidRegistered = getIntent().getBooleanExtra(EXTRA_UUID_REGISTERED, false);
            mTrialCountdown = getIntent().getIntExtra(EXTRA_TRIAL_COUNTDOWN, 0);
            if (!mUuidRegistered && mTrialCountdown == 0) {
                finish();
                return;
            }
        }

        setContentView(R.layout.led_control_main_activity);

        mTrialInfoView = findViewById(R.id.trial_info);
        if (!mUuidRegistered) {
            mTrialInfoView.setText(String.format(getString(R.string.trial_info), mTrialCountdown));
            mTrialInfoView.setVisibility(View.VISIBLE);
        }
    }

    public static class PrefsFragment extends PreferenceFragment {
        protected static final String PREF_KEY_DEFAULT_SETTINGS = "pref_unc_default_settings";
        protected static final String PREF_KEY_PERAPP_SETTINGS = "pref_unc_perapp_settings";
        protected static final String PREF_KEY_ACTIVE_SCREEN = "pref_unc_active_screen";
        protected static final String PREF_KEY_QUIET_HOURS = "pref_unc_quiet_hours";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setStorageDeviceProtected();
            addPreferencesFromResource(R.xml.led_control_main_activity_prefs);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen prefScreen, Preference pref) {
            ActivityContentInsets.onPreferenceTreeClick(pref);
            final String key = pref.getKey();
            if (PREF_KEY_DEFAULT_SETTINGS.equals(key)) {
                Intent intent = new Intent(getActivity(), LedSettingsActivity.class);
                intent.putExtra(LedSettingsActivity.EXTRA_PACKAGE_NAME, "default");
                intent.putExtra(LedSettingsActivity.EXTRA_APP_NAME, 
                        getString(R.string.lc_activity_menu_default_settings));
                startActivity(intent);
            } else if (PREF_KEY_PERAPP_SETTINGS.equals(key)) {
                startActivity(new Intent(getActivity(), LedControlActivity.class));
            } else if (PREF_KEY_ACTIVE_SCREEN.equals(key)) {
                startActivity(new Intent(getActivity(), ActiveScreenActivity.class));
            } else if (PREF_KEY_QUIET_HOURS.equals(key)) {
                startActivity(new Intent(getActivity(), QuietHoursActivity.class));
            }
            return super.onPreferenceTreeClick(prefScreen, pref);
        }
    }
}
