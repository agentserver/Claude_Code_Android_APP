package com.portalagent.settings;

import com.termux.app.TermuxActivity;
import com.portalagent.apitools.ApiToolsFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.portalagent.activities.SettingsActivity;

public class AppSettingsFragment extends Fragment
    implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private CharSequence rootTitle;
    private CharSequence rootSubtitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootTitle = getString(R.string.title_activity_termux_settings);
        rootSubtitle = getString(R.string.app_settings_subtitle);
        registerPreferenceStyler();
        view.findViewById(R.id.settings_activity_back_button).setOnClickListener(v -> {
            if (!handleBackPressed()) {
                TermuxActivity a = act();
                if (a != null) a.navigateBackToSettingsHub();
            }
        });
        setSettingsTitle(rootTitle);
        setSettingsSubtitle(rootSubtitle);

        FragmentManager fm = getChildFragmentManager();
        if (savedInstanceState == null && fm.findFragmentByTag("root_preferences") == null) {
            fm.beginTransaction()
                .replace(R.id.settings,
                    new SettingsActivity.RootPreferencesFragment(),
                    "root_preferences")
                .commit();
        }
        fm.addOnBackStackChangedListener(() -> {
            int count = fm.getBackStackEntryCount();
            CharSequence title = rootTitle;
            if (count > 0) {
                title = fm.getBackStackEntryAt(count - 1).getName();
            }
            setSettingsTitle(title);
            setSettingsSubtitle(isApiToolsTitle(title)
                ? getString(R.string.api_tools_description)
                : rootSubtitle);
        });
    }

    public boolean handleBackPressed() {
        FragmentManager fm = getChildFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return true;
        }
        return false;
    }

    public void showApiToolsPage() {
        CharSequence title = getString(R.string.title_activity_api_tools);
        getChildFragmentManager()
            .beginTransaction()
            .replace(R.id.settings, new ApiToolsFragment())
            .addToBackStack(title.toString())
            .commit();
        setSettingsTitle(title);
        setSettingsSubtitle(getString(R.string.api_tools_description));
    }

    public void requestEnvironmentRepair() {
        TermuxActivity a = act();
        if (a != null) a.requestEnvironmentRepairFromSettings();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                                             @NonNull Preference preference) {
        String fragmentName = preference.getFragment();
        if (fragmentName == null || fragmentName.isEmpty()) return false;

        Fragment fragment = getChildFragmentManager()
            .getFragmentFactory()
            .instantiate(requireContext().getClassLoader(), fragmentName);
        fragment.setArguments(preference.getExtras());

        CharSequence title = preference.getTitle() == null ? rootTitle : preference.getTitle();
        getChildFragmentManager()
            .beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(title == null ? "" : title.toString())
            .commit();
        setSettingsTitle(title);
        return true;
    }

    private void setSettingsTitle(CharSequence title) {
        View view = getView();
        if (view == null) return;
        TextView titleView = view.findViewById(R.id.settings_activity_title);
        if (titleView != null) {
            titleView.setText(title);
        }
    }

    private void setSettingsSubtitle(CharSequence subtitle) {
        View view = getView();
        if (view == null) return;
        TextView subtitleView = view.findViewById(R.id.settings_activity_subtitle);
        if (subtitleView != null) {
            subtitleView.setText(subtitle);
        }
    }

    private boolean isApiToolsTitle(CharSequence title) {
        return title != null && title.toString().contentEquals(getString(R.string.title_activity_api_tools));
    }

    private void registerPreferenceStyler() {
        getChildFragmentManager().registerFragmentLifecycleCallbacks(
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

    @Nullable
    private TermuxActivity act() {
        return getActivity() instanceof TermuxActivity ? (TermuxActivity) getActivity() : null;
    }
}
