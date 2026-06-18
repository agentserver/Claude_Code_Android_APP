package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AppSettingsEmbeddedNavigationTest {

    @Test
    public void settingsHubOpensEmbeddedAppSettingsInsteadOfActivity() throws Exception {
        String source = readSource("src/main/java/com/termux/app/SettingsHubFragment.java");

        Assert.assertTrue(source.contains("showAppSettingsMode()"));
        Assert.assertFalse(source.contains("SettingsActivity.class"));
        Assert.assertFalse(source.contains("startActivity(new Intent(requireContext()"));
    }

    @Test
    public void homeSettingsButtonUsesSettingsTabInsteadOfActivity() throws Exception {
        String source = readSource("src/main/java/com/termux/app/HomeFragment.java");

        Assert.assertTrue(source.contains("navigateBackToSettingsHub()"));
        Assert.assertFalse(source.contains("com.termux.app.activities.SettingsActivity.class"));
    }

    @Test
    public void termuxActivityHostsAppSettingsInBottomNavContainer() throws Exception {
        String source = readSource("src/main/java/com/termux/app/TermuxActivity.java");

        Assert.assertTrue(source.contains("public void showAppSettingsMode()"));
        Assert.assertTrue(source.contains("new AppSettingsFragment()"));
        Assert.assertTrue(source.contains("\"app_settings\""));
        Assert.assertTrue(source.contains("isFragmentVisible(\"app_settings\")"));
        Assert.assertTrue(source.contains("handleVisibleAppSettingsBack()"));
        Assert.assertTrue(source.contains("if (appSettingsF != null) ft.hide(appSettingsF);"));
        Assert.assertFalse(extractMethod(source, "showSettingsHubMode")
            .contains("setSelectedItemId(R.id.nav_settings)"));
    }

    @Test
    public void appSettingsFragmentReusesSettingsChromeAndPreferences() throws Exception {
        String source = readSource("src/main/java/com/termux/app/AppSettingsFragment.java");

        Assert.assertTrue(source.contains("R.layout.activity_settings"));
        Assert.assertTrue(source.contains("SettingsActivity.RootPreferencesFragment"));
        Assert.assertTrue(source.contains("PreferenceFragmentCompat.OnPreferenceStartFragmentCallback"));
        Assert.assertTrue(source.contains("getChildFragmentManager()"));
        Assert.assertTrue(source.contains("navigateBackToSettingsHub()"));
    }

    private static String readSource(String relativePath) throws Exception {
        File file = resolveProjectFile(relativePath);
        Assert.assertTrue("Missing source file: " + file.getAbsolutePath(), file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File resolveProjectFile(String relativePath) {
        File file = new File(relativePath);
        if (!file.isFile()) {
            file = new File("app/" + relativePath);
        }
        return file;
    }

    private static String extractMethod(String source, String methodName) {
        String marker = "public void " + methodName + "()";
        int start = source.indexOf(marker);
        Assert.assertTrue("Missing method: " + methodName, start >= 0);
        int brace = source.indexOf('{', start);
        Assert.assertTrue("Missing method body: " + methodName, brace >= 0);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') depth++;
            if (ch == '}') {
                depth--;
                if (depth == 0) return source.substring(start, i + 1);
            }
        }
        Assert.fail("Unclosed method body: " + methodName);
        return "";
    }
}
