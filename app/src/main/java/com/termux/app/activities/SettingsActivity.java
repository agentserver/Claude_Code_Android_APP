package com.termux.app.activities;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences;
import com.termux.shared.activity.media.AppCompatActivityUtils;

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

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }

        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    private void applySettingsChrome() {
        Window window = getWindow();
        int backgroundColor = ContextCompat.getColor(this, R.color.app_bg_secondary);
        int surfaceColor = ContextCompat.getColor(this, R.color.app_card_bg);
        int primaryColor = ContextCompat.getColor(this, R.color.app_primary);
        int textColor = ContextCompat.getColor(this, R.color.app_text_primary);

        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(surfaceColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(surfaceColor);
            toolbar.setTitleTextColor(textColor);
            toolbar.setSubtitleTextColor(ContextCompat.getColor(this, R.color.app_text_secondary));
            Drawable navigationIcon = toolbar.getNavigationIcon();
            if (navigationIcon != null) {
                Drawable wrappedIcon = DrawableCompat.wrap(navigationIcon).mutate();
                DrawableCompat.setTint(wrappedIcon, primaryColor);
                toolbar.setNavigationIcon(wrappedIcon);
            }
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
                        stylePreferenceFragment((PreferenceFragmentCompat) fragment, view);
                    }
                }
            },
            true
        );
    }

    private static void stylePreferenceFragment(@NonNull PreferenceFragmentCompat fragment,
                                                @NonNull View view) {
        Context context = view.getContext();
        int backgroundColor = ContextCompat.getColor(context, R.color.app_bg_secondary);
        view.setBackgroundColor(backgroundColor);

        RecyclerView list = fragment.getListView();
        if (list != null) {
            list.setBackgroundColor(backgroundColor);
            list.setClipToPadding(false);
            list.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 18));
            list.setOverScrollMode(View.OVER_SCROLL_NEVER);
            RecyclerView.Adapter<?> adapter = list.getAdapter();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null) return;

            setPreferencesFromResource(R.xml.root_preferences, rootKey);
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
