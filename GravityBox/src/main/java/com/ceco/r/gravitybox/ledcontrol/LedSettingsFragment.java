/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

import net.margaritov.preference.colorpicker.ColorPickerPreference;

import java.util.Arrays;
import java.util.HashSet;

import com.ceco.r.gravitybox.ActivityContentInsets;
import com.ceco.r.gravitybox.R;
import com.ceco.r.gravitybox.ProgressBarController;
import com.ceco.r.gravitybox.ledcontrol.LedSettings.ActiveScreenMode;
import com.ceco.r.gravitybox.ledcontrol.LedSettings.HeadsUpMode;
import com.ceco.r.gravitybox.ledcontrol.LedSettings.LedMode;
import com.ceco.r.gravitybox.ledcontrol.LedSettings.Visibility;
import com.ceco.r.gravitybox.ledcontrol.LedSettings.VisibilityLs;
import com.ceco.r.gravitybox.preference.SeekBarPreference;

import android.app.Activity;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.widget.Toast;

public class LedSettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener {
    private static final String PREF_KEY_LED_COLOR = "pref_lc_led_color";
    private static final String PREF_KEY_LED_TIME_ON = "pref_lc_led_time_on";
    private static final String PREF_KEY_LED_TIME_OFF = "pref_lc_led_time_off";
    private static final String PREF_KEY_ONGOING = "pref_lc_ongoing";
    private static final String PREF_KEY_NOTIF_SOUND_OVERRIDE = "pref_lc_notif_sound_override";
    private static final String PREF_KEY_NOTIF_SOUND = "pref_lc_notif_sound";
    private static final String PREF_KEY_NOTIF_SOUND_REPLACE = "pref_lc_sound_replace";
    private static final String PREF_KEY_NOTIF_SOUND_ONLY_ONCE = "pref_lc_notif_sound_only_once";
    private static final String PREF_KEY_NOTIF_SOUND_ONLY_ONCE_TIMEOUT = "pref_lc_notif_sound_only_once_timeout";
    private static final String PREF_KEY_NOTIF_INSISTENT = "pref_lc_notif_insistent";
    private static final String PREF_KEY_VIBRATE_OVERRIDE = "pref_lc_vibrate_override";
    private static final String PREF_KEY_VIBRATE_PATTERN = "pref_lc_vibrate_pattern";
    private static final String PREF_KEY_VIBRATE_REPLACE = "pref_lc_vibrate_replace";
    private static final String PREF_KEY_DEFAULT_SETTINGS = "pref_lc_default_settings";
    private static final String PREF_CAT_KEY_ACTIVE_SCREEN = "pref_cat_lc_active_screen";
    private static final String PREF_KEY_ACTIVE_SCREEN_MODE = "pref_lc_active_screen_mode";
    private static final String PREF_KEY_ACTIVE_SCREEN_IGNORE_UPDATE = "pref_lc_active_screen_ignore_update";
    private static final String PREF_KEY_LED_MODE = "pref_lc_led_mode";
    private static final String PREF_CAT_KEY_QH = "pref_cat_lc_quiet_hours";
    private static final String PREF_KEY_QH_IGNORE = "pref_lc_qh_ignore";
    private static final String PREF_KEY_QH_IGNORE_LIST = "pref_lc_qh_ignore_list";
    private static final String PREF_KEY_QH_IGNORE_INTERACTIVE = "pref_lc_qh_ignore_interactive";
    private static final String PREF_CAT_KEY_HEADS_UP = "pref_cat_lc_heads_up";
    private static final String PREF_KEY_HEADS_UP_MODE = "pref_lc_headsup_mode";
    private static final String PREF_KEY_HEADS_UP_DND = "pref_lc_headsup_dnd";
    private static final String PREF_KEY_HEADS_UP_TIMEOUT = "pref_lc_headsup_timeout";
    private static final String PREF_CAT_KEY_OTHER = "pref_cat_lc_other";
    private static final String PREF_KEY_PROGRESS_TRACKING = "pref_lc_progress_tracking";
    private static final String PREF_KEY_VISIBILITY = "pref_lc_notif_visibility";
    private static final String PREF_KEY_VISIBILITY_LS = "pref_lc_notif_visibility_ls";
    private static final String PREF_KEY_DISABLE_SOUND_TO_VIBRATE = "pref_lc_sound_vibrate";
    private static final String PREF_KEY_HIDE_PERSISTENT = "pref_lc_notif_hide_persistent";
    private static final String PREF_KEY_LED_DND = "pref_lc_led_dnd";
    private static final String PREF_KEY_LED_IGNORE_UPDATE = "pref_lc_led_ignore_update";

    private static final int REQ_PICK_SOUND = 101;

    private ColorPickerPreference mColorPref;
    private SeekBarPreference mLedOnMsPref;
    private SeekBarPreference mLedOffMsPref;
    private CheckBoxPreference mOngoingPref;
    private Preference mNotifSoundPref;
    private CheckBoxPreference mNotifSoundOverridePref;
    private Uri mSoundUri;
    private CheckBoxPreference mSoundReplacePref;
    private CheckBoxPreference mNotifSoundOnlyOncePref;
    private SeekBarPreference mNotifSoundOnlyOnceTimeoutPref;
    private CheckBoxPreference mNotifInsistentPref;
    private CheckBoxPreference mVibratePatternOverridePref;
    private EditTextPreference mVibratePatternPref;
    private CheckBoxPreference mVibrateReplacePref;
    private SwitchPreference mDefaultSettingsPref;
    private PreferenceCategory mActiveScreenCat;
    private ListPreference mActiveScreenModePref;
    private CheckBoxPreference mActiveScreenIgnoreUpdatePref;
    private ListPreference mLedModePref;
    private PreferenceCategory mQhCat;
    private CheckBoxPreference mQhIgnorePref;
    private EditTextPreference mQhIgnoreListPref;
    private CheckBoxPreference mQhIgnoreInteractivePref;
    private PreferenceCategory mHeadsUpCat;
    private ListPreference mHeadsUpModePref;
    private CheckBoxPreference mHeadsUpDndPref;
    private SeekBarPreference mHeadsUpTimeoutPref;
    private PreferenceCategory mOtherCat;
    private CheckBoxPreference mProgressTrackingPref;
    private ListPreference mVisibilityPref;
    private ListPreference mVisibilityLsPref;
    private CheckBoxPreference mDisableSoundToVibratePref;
    private CheckBoxPreference mHidePersistentPref;
    private MultiSelectListPreference mLedDndPref;
    private CheckBoxPreference mLedIgnoreUpdatePref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setStorageDeviceProtected();
        addPreferencesFromResource(R.xml.led_control_settings);

        mColorPref = (ColorPickerPreference) findPreference(PREF_KEY_LED_COLOR);
        mLedOnMsPref = (SeekBarPreference) findPreference(PREF_KEY_LED_TIME_ON);
        mLedOffMsPref = (SeekBarPreference) findPreference(PREF_KEY_LED_TIME_OFF);
        mOngoingPref = (CheckBoxPreference) findPreference(PREF_KEY_ONGOING);
        mNotifSoundOverridePref = (CheckBoxPreference) findPreference(PREF_KEY_NOTIF_SOUND_OVERRIDE);
        mNotifSoundPref = findPreference(PREF_KEY_NOTIF_SOUND);
        mSoundReplacePref = (CheckBoxPreference) findPreference(PREF_KEY_NOTIF_SOUND_REPLACE);
        mNotifSoundOnlyOncePref = (CheckBoxPreference) findPreference(PREF_KEY_NOTIF_SOUND_ONLY_ONCE);
        mNotifSoundOnlyOnceTimeoutPref = (SeekBarPreference) findPreference(PREF_KEY_NOTIF_SOUND_ONLY_ONCE_TIMEOUT);
        mNotifInsistentPref = (CheckBoxPreference) findPreference(PREF_KEY_NOTIF_INSISTENT);
        mVibratePatternOverridePref = (CheckBoxPreference) findPreference(PREF_KEY_VIBRATE_OVERRIDE);
        mVibratePatternPref = (EditTextPreference) findPreference(PREF_KEY_VIBRATE_PATTERN);
        mVibratePatternPref.setOnPreferenceChangeListener(this);
        mVibrateReplacePref = (CheckBoxPreference) findPreference(PREF_KEY_VIBRATE_REPLACE);
        mDefaultSettingsPref = (SwitchPreference) findPreference(PREF_KEY_DEFAULT_SETTINGS);
        mActiveScreenCat = (PreferenceCategory) findPreference(PREF_CAT_KEY_ACTIVE_SCREEN);
        mActiveScreenModePref = (ListPreference) findPreference(PREF_KEY_ACTIVE_SCREEN_MODE);
        mActiveScreenModePref.setOnPreferenceChangeListener(this);
        mActiveScreenIgnoreUpdatePref = (CheckBoxPreference) findPreference(PREF_KEY_ACTIVE_SCREEN_IGNORE_UPDATE);
        mLedModePref = (ListPreference) findPreference(PREF_KEY_LED_MODE);
        mLedModePref.setOnPreferenceChangeListener(this);
        mQhCat = (PreferenceCategory) findPreference(PREF_CAT_KEY_QH);
        mQhIgnorePref = (CheckBoxPreference) findPreference(PREF_KEY_QH_IGNORE);
        mQhIgnoreListPref = (EditTextPreference) findPreference(PREF_KEY_QH_IGNORE_LIST);
        mQhIgnoreInteractivePref = (CheckBoxPreference) findPreference(PREF_KEY_QH_IGNORE_INTERACTIVE);
        mHeadsUpCat = (PreferenceCategory) findPreference(PREF_CAT_KEY_HEADS_UP);
        mHeadsUpModePref = (ListPreference) findPreference(PREF_KEY_HEADS_UP_MODE);
        mHeadsUpModePref.setOnPreferenceChangeListener(this);
        mHeadsUpDndPref = (CheckBoxPreference) findPreference(PREF_KEY_HEADS_UP_DND);
        mHeadsUpTimeoutPref = (SeekBarPreference) findPreference(PREF_KEY_HEADS_UP_TIMEOUT);
        mOtherCat = (PreferenceCategory) findPreference(PREF_CAT_KEY_OTHER);
        mProgressTrackingPref = (CheckBoxPreference) findPreference(PREF_KEY_PROGRESS_TRACKING);
        mVisibilityPref = (ListPreference) findPreference(PREF_KEY_VISIBILITY);
        mVisibilityPref.setOnPreferenceChangeListener(this);
        mVisibilityLsPref = (ListPreference) findPreference(PREF_KEY_VISIBILITY_LS);
        mVisibilityLsPref.setOnPreferenceChangeListener(this);
        mDisableSoundToVibratePref = (CheckBoxPreference) findPreference(PREF_KEY_DISABLE_SOUND_TO_VIBRATE);
        mHidePersistentPref = (CheckBoxPreference) findPreference(PREF_KEY_HIDE_PERSISTENT);
        mLedDndPref = (MultiSelectListPreference) findPreference(PREF_KEY_LED_DND);
        mLedIgnoreUpdatePref = (CheckBoxPreference) findPreference(PREF_KEY_LED_IGNORE_UPDATE);
    }

    protected void initialize(LedSettings ledSettings) {
        mColorPref.setValue(ledSettings.getColor());
        mLedOnMsPref.setValue(ledSettings.getLedOnMs());
        mLedOffMsPref.setValue(ledSettings.getLedOffMs());
        mOngoingPref.setChecked(ledSettings.getOngoing());
        mNotifSoundOverridePref.setChecked(ledSettings.getSoundOverride());
        mSoundUri = ledSettings.getSoundUri();
        mSoundReplacePref.setChecked(ledSettings.getSoundReplace());
        mNotifSoundOnlyOncePref.setChecked(ledSettings.getSoundOnlyOnce());
        mNotifSoundOnlyOnceTimeoutPref.setValue((int)(ledSettings.getSoundOnlyOnceTimeout() / 1000));
        mNotifInsistentPref.setChecked(ledSettings.getInsistent());
        mVibratePatternOverridePref.setChecked(ledSettings.getVibrateOverride());
        if (ledSettings.getVibratePatternAsString() != null) {
            mVibratePatternPref.setText(ledSettings.getVibratePatternAsString());
        }
        mVibrateReplacePref.setChecked(ledSettings.getVibrateReplace());
        updateSoundPrefSummary();
        if (ledSettings.getPackageName().equals("default")) {
            mDefaultSettingsPref.setChecked(ledSettings.getEnabled());
            mHeadsUpCat.removePreference(mHeadsUpDndPref);
        } else {
            mDefaultSettingsPref.setChecked(false);
            getPreferenceScreen().removePreference(mDefaultSettingsPref);
        }
        if (!LedSettings.isActiveScreenMasterEnabled(getActivity())) {
            getPreferenceScreen().removePreference(mActiveScreenCat);
        } else {
            mActiveScreenModePref.setValue(ledSettings.getActiveScreenMode().toString());
            mActiveScreenModePref.setSummary(mActiveScreenModePref.getEntry());
            mActiveScreenIgnoreUpdatePref.setChecked(ledSettings.getActiveScreenIgnoreUpdate());
        }
        mLedModePref.setValue(ledSettings.getLedMode().toString());
        updateLedModeDependentState();
        if (!LedSettings.isQuietHoursEnabled(getActivity())) {
            getPreferenceScreen().removePreference(mQhCat);
        } else {
            mQhIgnorePref.setChecked(ledSettings.getQhIgnore());
            mQhIgnoreListPref.setText(ledSettings.getQhIgnoreList());
            mQhIgnoreInteractivePref.setChecked(ledSettings.getQhIgnoreInteractive());
        }
        if (!LedSettings.isHeadsUpEnabled(getActivity())) {
            getPreferenceScreen().removePreference(mHeadsUpCat);
        } else {
            mHeadsUpModePref.setValue(ledSettings.getHeadsUpMode().toString());
            mHeadsUpModePref.setSummary(mHeadsUpModePref.getEntry());
            mHeadsUpDndPref.setChecked(ledSettings.getHeadsUpDnd());
            mHeadsUpTimeoutPref.setValue(ledSettings.getHeadsUpTimeout());
            mHeadsUpTimeoutPref.setEnabled(ledSettings.getHeadsUpMode() != HeadsUpMode.OFF);
        }
        if (ProgressBarController.SUPPORTED_PACKAGES.contains(ledSettings.getPackageName())) {
            mOtherCat.removePreference(mProgressTrackingPref);
        } else {
            mProgressTrackingPref.setChecked(ledSettings.getProgressTracking());
        }
        mVisibilityPref.setValue(ledSettings.getVisibility().toString());
        mVisibilityPref.setSummary(String.format("%s (%s)",
                getString(R.string.pref_lc_notif_visibility_summary),
                mVisibilityPref.getEntry()));
        mVisibilityLsPref.setValue(ledSettings.getVisibilityLs().toString());
        mVisibilityLsPref.setSummary(mVisibilityLsPref.getEntry());
        mDisableSoundToVibratePref.setChecked(ledSettings.getSoundToVibrateDisabled());
        mHidePersistentPref.setChecked(ledSettings.getHidePersistent());
        mLedDndPref.setValues(new HashSet<>(Arrays.asList(ledSettings.getLedDnd().split(","))));
        mLedIgnoreUpdatePref.setChecked(ledSettings.getLedIgnoreUpdate());
    }

    private void updateSoundPrefSummary() {
        mNotifSoundPref.setSummary(getString(R.string.lc_notif_sound_none));
        if (mSoundUri != null) {
            Ringtone r = RingtoneManager.getRingtone(getActivity(), mSoundUri);
            if (r != null) {
                mNotifSoundPref.setSummary(r.getTitle(getActivity()));
            } else {
                mSoundUri = null;
            }
        }
    }

    private void updateLedModeDependentState() {
        mLedModePref.setSummary(mLedModePref.getEntry());
        LedMode lm = LedMode.valueOf(mLedModePref.getValue());
        mColorPref.setEnabled(lm == LedMode.OVERRIDE);
        mLedOnMsPref.setEnabled(lm == LedMode.OVERRIDE);
        mLedOffMsPref.setEnabled(lm == LedMode.OVERRIDE);
    }

    protected int getColor() {
        return mColorPref.getValue();
    }

    protected int getLedOnMs() {
        return mLedOnMsPref.getValue();
    }

    protected int getLedOffMs() {
        return mLedOffMsPref.getValue();
    }

    protected boolean getOngoing() {
        return mOngoingPref.isChecked();
    }

    protected boolean getSoundOverride() {
        return mNotifSoundOverridePref.isChecked();
    }

    protected Uri getSoundUri() {
        return mSoundUri;
    }

    protected boolean getSoundReplace() {
        return mSoundReplacePref.isChecked();
    }

    protected boolean getSoundOnlyOnce() {
        return mNotifSoundOnlyOncePref.isChecked();
    }

    protected long getSoundOnlyOnceTimeout() {
        return (mNotifSoundOnlyOnceTimeoutPref.getValue() * 1000);
    }

    protected boolean getInsistent() {
        return mNotifInsistentPref.isChecked();
    }

    protected boolean getVibrateOverride() {
        return mVibratePatternOverridePref.isChecked();
    }

    protected String getVibratePatternAsString() {
        return mVibratePatternPref.getText();
    }

    protected boolean getVibrateReplace() {
        return mVibrateReplacePref.isChecked();
    }

    protected boolean getDefaultSettingsEnabled() {
        return mDefaultSettingsPref.isChecked();
    }

    protected ActiveScreenMode getActiveScreenMode() {
        return ActiveScreenMode.valueOf(mActiveScreenModePref.getValue());
    }

    protected boolean getActiveScreenIgnoreUpdate() {
        return mActiveScreenIgnoreUpdatePref.isChecked();
    }

    protected LedMode getLedMode() {
        return LedMode.valueOf(mLedModePref.getValue());
    }

    protected boolean getQhIgnore() {
        return mQhIgnorePref.isChecked();
    }

    protected String getQhIgnoreList() {
        return mQhIgnoreListPref.getText();
    }

    protected boolean getQhIgnoreInteractive() {
        return mQhIgnoreInteractivePref.isChecked();
    }

    protected String getHeadsUpMode() {
        return mHeadsUpModePref.getValue();
    }

    protected boolean getHeadsUpDnd() {
        return mHeadsUpDndPref.isChecked();
    }

    protected int getHeadsUpTimeout() {
        return mHeadsUpTimeoutPref.getValue();
    }

    protected boolean getProgressTracking() {
        return mProgressTrackingPref.isChecked();
    }

    protected String getLedDnd() {
        String buf = "";
        if (mLedDndPref.getValues() == null)
            return buf;
        for (String s : mLedDndPref.getValues()) {
            if (!buf.isEmpty()) buf += ",";
            buf += s;
        }
        return buf;
    }

    protected boolean getLedIgnoreUpdate() {
        return mLedIgnoreUpdatePref.isChecked();
    }

    protected Visibility getVisibility() {
        return Visibility.valueOf(mVisibilityPref.getValue());
    }

    protected VisibilityLs getVisibilityLs() {
        return VisibilityLs.valueOf(mVisibilityLsPref.getValue());
    }

    protected boolean getSoundToVibrateDisabled() {
        return mDisableSoundToVibratePref.isChecked();
    }

    protected boolean getHidePersistent() {
        return mHidePersistentPref.isChecked();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen prefScreen, Preference pref) {
        ActivityContentInsets.onPreferenceTreeClick(pref);
        if (pref == mNotifSoundPref) {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, mSoundUri);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, 
                    Settings.System.DEFAULT_NOTIFICATION_URI);
            startActivityForResult(intent, REQ_PICK_SOUND);
            return true;
        }
        return super.onPreferenceTreeClick(prefScreen, pref);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_SOUND && resultCode == Activity.RESULT_OK && data != null) {
            mSoundUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            updateSoundPrefSummary();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mVibratePatternPref) {
            try {
                String val = (String)newValue;
                LedSettings.parseVibratePatternString(val);
                return true;
            } catch (Exception e) {
                Toast.makeText(getActivity(), getString(R.string.lc_vibrate_pattern_invalid),
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if (preference == mLedModePref) {
            mLedModePref.setValue((String)newValue);
            updateLedModeDependentState();
        } else if (preference == mHeadsUpModePref) {
            mHeadsUpModePref.setValue((String)newValue);
            mHeadsUpModePref.setSummary(mHeadsUpModePref.getEntry());
            final boolean enabled = !"OFF".equals(mHeadsUpModePref.getValue());
            mHeadsUpTimeoutPref.setEnabled(enabled);
        } else if (preference == mActiveScreenModePref) {
            mActiveScreenModePref.setValue((String)newValue);
            mActiveScreenModePref.setSummary(mActiveScreenModePref.getEntry());
        } else if (preference == mVisibilityPref) {
            mVisibilityPref.setValue((String)newValue);
            mVisibilityPref.setSummary(String.format("%s (%s)",
                    getString(R.string.pref_lc_notif_visibility_summary),
                    mVisibilityPref.getEntry()));
        } else if (preference == mVisibilityLsPref) {
            mVisibilityLsPref.setValue((String)newValue);
            mVisibilityLsPref.setSummary(mVisibilityLsPref.getEntry());
        }
        return true;
    }
}
