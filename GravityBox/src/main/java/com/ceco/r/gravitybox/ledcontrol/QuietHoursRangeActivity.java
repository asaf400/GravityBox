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
package com.ceco.r.gravitybox.ledcontrol;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.ceco.r.gravitybox.ActivityContentInsets;
import com.ceco.r.gravitybox.GravityBoxActivity;
import com.ceco.r.gravitybox.R;
import com.ceco.r.gravitybox.SettingsManager;
import com.ceco.r.gravitybox.preference.TimePreference;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.widget.Button;

public class QuietHoursRangeActivity extends GravityBoxActivity {

    public static final String PREF_QH_RANGE_DAYS = "pref_lc_qh_range_days";
    public static final String PREF_QH_RANGE_START = "pref_lc_qh_range_start";
    public static final String PREF_QH_RANGE_END = "pref_lc_qh_range_end";
    public static final String PREF_QH_RANGE_MUTE_LED = "pref_lc_qh_range_mute_led";
    public static final String PREF_QH_RANGE_MUTE_VIBE = "pref_lc_qh_range_mute_vibe";
    public static final String PREF_QH_RANGE_MUTE_SYSTEM_VIBE = "pref_lc_qh_range_mute_system_vibe";
    public static final String PREF_QH_RANGE_MUTE_SYSTEM_SOUNDS = "pref_lc_qh_range_mute_system_sounds";
    public static final String PREF_QH_RANGE_RINGER_WHITELIST = "pref_lc_qh_range_ringer_whitelist";
    public static final String EXTRA_QH_RANGE = "qhRange";

    private Button mBtnCancel;
    private Button mBtnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Set<String> rangeValue = null;
        final Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_QH_RANGE)) {
            rangeValue = new HashSet<>(intent.getStringArrayListExtra(EXTRA_QH_RANGE));
        }

        setContentView(R.layout.quiet_hours_range_activity);

        mBtnCancel = findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        mBtnSave = findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(v -> {
            Intent intent1 = new Intent();
            intent1.putStringArrayListExtra(EXTRA_QH_RANGE, new ArrayList<>(
                    getFragment().getRange().getValue()));
            setResult(Activity.RESULT_OK, intent1);
            finish();
        });

        getFragment().setRangeValue(rangeValue);
    }

    private PrefsFragment getFragment() {
        return (PrefsFragment) getFragmentManager()
                .findFragmentById(R.id.prefs_fragment);
    }

    public static class PrefsFragment extends PreferenceFragment
                                      implements OnPreferenceChangeListener {

        private MultiSelectListPreference mPrefDays;
        private TimePreference mPrefStartTime;
        private TimePreference mPrefEndTime;
        private CheckBoxPreference mPrefMuteLed;
        private CheckBoxPreference mPrefMuteVibe;
        private CheckBoxPreference mPrefMuteSystemVibe;
        private MultiSelectListPreference mPrefMuteSystemSounds;
        private Preference mPrefRingerWhitelist;
        private QuietHours.Range mRange;
        private boolean mIsNew;

        public PrefsFragment() { }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setStorageDeviceProtected();
            addPreferencesFromResource(R.xml.quiet_hours_range_settings);

            mPrefDays = (MultiSelectListPreference) findPreference(PREF_QH_RANGE_DAYS); 
            String[] days = new DateFormatSymbols(Locale.getDefault()).getWeekdays();
            CharSequence[] entries = new CharSequence[7];
            CharSequence[] entryValues = new CharSequence[7];
            for (int i=1; i<=7; i++) {
                entries[i-1] = days[i];
                entryValues[i-1] = String.valueOf(i);
            }
            mPrefDays.setEntries(entries);
            mPrefDays.setEntryValues(entryValues);
            mPrefDays.setOnPreferenceChangeListener(this);

            mPrefStartTime = (TimePreference) findPreference(PREF_QH_RANGE_START);
            mPrefStartTime.setOnPreferenceChangeListener(this);

            mPrefEndTime = (TimePreference) findPreference(PREF_QH_RANGE_END);
            mPrefEndTime.setOnPreferenceChangeListener(this);

            mPrefMuteLed = (CheckBoxPreference) findPreference(PREF_QH_RANGE_MUTE_LED);
            mPrefMuteLed.setOnPreferenceChangeListener(this);

            mPrefMuteVibe = (CheckBoxPreference) findPreference(PREF_QH_RANGE_MUTE_VIBE);
            mPrefMuteVibe.setOnPreferenceChangeListener(this);

            mPrefMuteSystemVibe = (CheckBoxPreference) findPreference(PREF_QH_RANGE_MUTE_SYSTEM_VIBE);
            mPrefMuteSystemVibe.setOnPreferenceChangeListener(this);

            mPrefMuteSystemSounds = (MultiSelectListPreference) findPreference(PREF_QH_RANGE_MUTE_SYSTEM_SOUNDS);
            mPrefMuteSystemSounds.setOnPreferenceChangeListener(this);

            mPrefRingerWhitelist = findPreference(PREF_QH_RANGE_RINGER_WHITELIST);
        }

        void setRangeValue(Set<String> rangeValue) {
            mIsNew = (rangeValue == null);
            mRange = QuietHours.Range.parse(rangeValue);
            mPrefDays.setValues(mRange.days);
            mPrefStartTime.setValue(mRange.startTime);
            mPrefEndTime.setValue(mRange.endTime);
            mPrefMuteLed.setChecked(mRange.muteLED);
            mPrefMuteVibe.setChecked(mRange.muteVibe);
            mPrefMuteSystemVibe.setChecked(mRange.muteSystemVibe);
            mPrefMuteSystemSounds.setValues(mRange.muteSystemSounds);
            updateSummaries();
        }

        QuietHours.Range getRange() {
            return mRange;
        }

        private void updateSummaries() {
            String[] days = new DateFormatSymbols(Locale.getDefault()).getWeekdays();
            Set<String> values = new TreeSet<>(mRange.days);
            String summary = "";
            for (String wday : values) {
                if (!summary.isEmpty()) summary += ", ";
                try {
                    summary += days[Integer.valueOf(wday)];
                } catch (NumberFormatException ignored) { }
            }
            mPrefDays.setSummary(summary);

            mPrefEndTime.setSummarySuffix(mRange.endsNextDay() ?
                    getString(R.string.next_day) : null);

            CharSequence[] entries = mPrefMuteSystemSounds.getEntries();
            CharSequence[] entryValues = mPrefMuteSystemSounds.getEntryValues();
            summary = "";
            for (String value : mRange.muteSystemSounds) {
                for (int i=0; i<entryValues.length; i++) {
                    if (entryValues[i].equals(value)) {
                        if (!summary.isEmpty()) summary += ", ";
                        summary += entries[i];
                        break;
                    }
                }
            }
            mPrefMuteSystemSounds.setSummary(summary);

            mPrefRingerWhitelist.setEnabled(mRange.muteSystemSounds.contains("ringer"));
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == mPrefDays) {
                mRange.days = (Set<String>) newValue;
            } else if (preference == mPrefStartTime) {
                mRange.startTime = (int) newValue;
            } else if (preference == mPrefEndTime) {
                mRange.endTime = (int) newValue;
            } else if (preference == mPrefMuteLed) {
                mRange.muteLED = (boolean) newValue;
            } else if (preference == mPrefMuteVibe) {
                mRange.muteVibe = (boolean) newValue;
            } else if (preference == mPrefMuteSystemVibe) {
                mRange.muteSystemVibe = (boolean) newValue;
            } else if (preference == mPrefMuteSystemSounds) {
                mRange.muteSystemSounds = new HashSet<>((Collection<? extends String>) newValue);
            }
            updateSummaries();
            return true;
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            ActivityContentInsets.onPreferenceTreeClick(preference);
            if (preference == mPrefRingerWhitelist) {
                Set<String> whiteList = mRange.ringerWhitelist;
                if (mIsNew) {
                    whiteList = SettingsManager.getInstance(getActivity()).getQuietHoursPrefs()
                        .getStringSet(QuietHoursActivity.PREF_KEY_QH_RINGER_WHITELIST,
                                new HashSet<>());
                }
                Intent intent = new Intent(getActivity(), RingerWhitelistActivity.class);
                intent.putStringArrayListExtra(QuietHoursActivity.EXTRA_QH_RINGER_WHITELIST,
                        new ArrayList<>(whiteList));
                startActivityForResult(intent, 0);
                return true;
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (data != null && data.hasExtra(QuietHoursActivity.EXTRA_QH_RINGER_WHITELIST)) {
                mRange.ringerWhitelist = new HashSet<>(
                        data.getStringArrayListExtra(QuietHoursActivity.EXTRA_QH_RINGER_WHITELIST));
            }
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
