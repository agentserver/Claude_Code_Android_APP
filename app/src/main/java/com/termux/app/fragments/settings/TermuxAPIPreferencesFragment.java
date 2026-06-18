package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.AppSettingsFragment;
import com.termux.app.activities.ApiToolsActivity;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;

@Keep
public class TermuxAPIPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxAPIPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_api_preferences, rootKey);

        Preference apiToolsPreference = findPreference("termux_api_tools");
        if (apiToolsPreference != null) {
            apiToolsPreference.setOnPreferenceClickListener(preference -> {
                Fragment parent = getParentFragment();
                if (parent instanceof AppSettingsFragment) {
                    ((AppSettingsFragment) parent).showApiToolsPage();
                } else {
                    startActivity(new Intent(context, ApiToolsActivity.class));
                }
                return true;
            });
        }
    }

}

class TermuxAPIPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;
    private final TermuxAPIAppSharedPreferences mPreferences;

    private static TermuxAPIPreferencesDataStore mInstance;

    private TermuxAPIPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAPIAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxAPIPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxAPIPreferencesDataStore(context);
        }
        return mInstance;
    }

}
