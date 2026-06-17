package com.termux.app;

import android.app.Activity;

import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
public class TermuxAPIAppSharedPreferencesTest {

    @Test
    public void mergedApiPreferencesDoNotShowMissingCompanionDialog() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();

        ShadowAlertDialog.reset();

        Assert.assertNotNull(TermuxAPIAppSharedPreferences.build(activity, true));
        Assert.assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }
}
