package com.portalagent.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

public final class SettingsPreferenceStyler {

    private SettingsPreferenceStyler() {
    }

    public static void stylePreferenceFragment(@NonNull PreferenceFragmentCompat fragment,
                                               @NonNull View view) {
        Context context = view.getContext();
        int backgroundColor = ContextCompat.getColor(context, R.color.app_bg_secondary);
        view.setBackgroundColor(backgroundColor);

        Preference preferenceScreen = fragment.getPreferenceScreen();
        if (preferenceScreen != null) {
            applyPreferenceLayouts(preferenceScreen);
        }

        RecyclerView list = fragment.getListView();
        if (list != null) {
            list.setBackgroundColor(backgroundColor);
            list.setClipToPadding(false);
            list.setPadding(0, 0, 0, dp(context, 18));
            list.setOverScrollMode(View.OVER_SCROLL_NEVER);
            installRowStyler(list);
            RecyclerView.Adapter<?> adapter = list.getAdapter();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private static void applyPreferenceLayouts(@NonNull Preference preference) {
        preference.setIconSpaceReserved(false);
        if (preference instanceof PreferenceCategory) {
            preference.setLayoutResource(R.layout.preference_settings_category);
        } else {
            preference.setLayoutResource(R.layout.preference_settings_item);
        }

        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                applyPreferenceLayouts(group.getPreference(i));
            }
        }
    }

    private static void installRowStyler(@NonNull RecyclerView list) {
        Object installed = list.getTag(R.id.settings);
        if (Boolean.TRUE.equals(installed)) {
            styleAttachedRows(list);
            return;
        }
        list.setTag(R.id.settings, Boolean.TRUE);
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                stylePreferenceRow(view);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
        styleAttachedRows(list);
    }

    private static void styleAttachedRows(@NonNull RecyclerView list) {
        for (int i = 0; i < list.getChildCount(); i++) {
            stylePreferenceRow(list.getChildAt(i));
        }
    }

    private static void stylePreferenceRow(@NonNull View row) {
        Context context = row.getContext();
        row.setBackgroundResource(R.drawable.bg_settings_preference_item);
        row.setMinimumHeight(dp(context, 64));
        row.setPadding(dp(context, 16), dp(context, 10), dp(context, 14), dp(context, 10));

        ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.topMargin = dp(context, 5);
            mlp.bottomMargin = dp(context, 5);
            row.setLayoutParams(mlp);
        }

        TextView title = row.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setSingleLine(false);
        }

        TextView summary = row.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTextColor(ContextCompat.getColor(context, R.color.app_text_muted));
            summary.setTextSize(12);
            summary.setMaxLines(3);
        }
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
