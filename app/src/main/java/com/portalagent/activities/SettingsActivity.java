package com.portalagent.activities;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.portalagent.settings.AppSettingsFragment;
import com.portalagent.settings.SettingsPreferenceStyler;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences;

public class SettingsActivity extends AppCompatActivity
    implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private CharSequence mRootTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        mRootTitle = getTitle();
        registerPreferenceStyler();

        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new RootPreferencesFragment())
                .commit();
        }

        View backButton = findViewById(R.id.settings_activity_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackPressed());
        }
        applySettingsChrome();
        setSettingsTitle(mRootTitle);

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            int count = getSupportFragmentManager().getBackStackEntryCount();
            CharSequence title = mRootTitle;
            if (count > 0) {
                title = getSupportFragmentManager().getBackStackEntryAt(count - 1).getName();
            }
            setSettingsTitle(title);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                                             @NonNull Preference preference) {
        String fragmentName = preference.getFragment();
        if (fragmentName == null || fragmentName.isEmpty()) return false;

        Fragment fragment = getSupportFragmentManager()
            .getFragmentFactory()
            .instantiate(getClassLoader(), fragmentName);
        fragment.setArguments(preference.getExtras());

        CharSequence title = preference.getTitle() == null ? mRootTitle : preference.getTitle();
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(title == null ? "" : title.toString())
            .commit();
        setSettingsTitle(title);
        return true;
    }

    private void setSettingsTitle(CharSequence title) {
        setTitle(title);
        TextView titleView = findViewById(R.id.settings_activity_title);
        if (titleView != null) {
            titleView.setText(title);
        }
    }

    private void applySettingsChrome() {
        Window window = getWindow();
        int backgroundColor = ContextCompat.getColor(this, R.color.app_bg_secondary);

        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(backgroundColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void registerPreferenceStyler() {
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(@NonNull FragmentManager fm,
                                                  @NonNull Fragment fragment,
                                                  @NonNull View view,
                                                  Bundle savedInstanceState) {
                    if (fragment instanceof PreferenceFragmentCompat) {
                        SettingsPreferenceStyler.stylePreferenceFragment(
                            (PreferenceFragmentCompat) fragment, view);
                    }
                }
            },
            true
        );
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {
        private static final String KEY_ENVIRONMENT_REPAIR = "portalagent_environment_repair";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null) return;

            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            configureEnvironmentRepairPreference(context);
            new Thread() {
                @Override
                public void run() {
                    configureTermuxAPIPreference(context);
                    configureTermuxFloatPreference(context);
                    configureTermuxTaskerPreference(context);
                    configureTermuxWidgetPreference(context);
                }
            }.start();
        }

        private void configureEnvironmentRepairPreference(@NonNull Context context) {
            Preference environmentRepairPreference = findPreference(KEY_ENVIRONMENT_REPAIR);
            if (environmentRepairPreference == null) return;

            environmentRepairPreference.setOnPreferenceClickListener(preference -> {
                Fragment parent = getParentFragment();
                if (parent instanceof AppSettingsFragment) {
                    ((AppSettingsFragment) parent).requestEnvironmentRepair();
                    return true;
                }
                if (getActivity() instanceof TermuxActivity) {
                    ((TermuxActivity) getActivity()).requestEnvironmentRepairFromSettings();
                    return true;
                }
                Toast.makeText(context,
                    R.string.environment_repair_open_from_app_settings,
                    Toast.LENGTH_LONG).show();
                return true;
            });
        }

        private void configureTermuxAPIPreference(@NonNull Context context) {
            Preference termuxAPIPreference = findPreference("termux_api");
            if (termuxAPIPreference != null) {
                TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxAPIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxFloatPreference(@NonNull Context context) {
            Preference termuxFloatPreference = findPreference("termux_float");
            if (termuxFloatPreference != null) {
                TermuxFloatAppSharedPreferences preferences = TermuxFloatAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxFloatPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxTaskerPreference(@NonNull Context context) {
            Preference termuxTaskerPreference = findPreference("termux_tasker");
            if (termuxTaskerPreference != null) {
                TermuxTaskerAppSharedPreferences preferences = TermuxTaskerAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxTaskerPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxWidgetPreference(@NonNull Context context) {
            Preference termuxWidgetPreference = findPreference("termux_widget");
            if (termuxWidgetPreference != null) {
                TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxWidgetPreference.setVisible(preferences != null);
            }
        }
    }

}
