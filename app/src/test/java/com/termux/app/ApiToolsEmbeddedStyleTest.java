package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ApiToolsEmbeddedStyleTest {

    @Test
    public void apiToolsPreferenceOpensEmbeddedPageWhenInAppSettings() throws Exception {
        String source = readSource("src/main/java/com/termux/app/fragments/settings/TermuxAPIPreferencesFragment.java");

        Assert.assertTrue(source.contains("parent instanceof AppSettingsFragment"));
        Assert.assertTrue(source.contains("showApiToolsPage()"));
        Assert.assertTrue(source.contains("startActivity(new Intent(context, ApiToolsActivity.class))"));
    }

    @Test
    public void appSettingsHostsApiToolsInsideExistingSettingsShell() throws Exception {
        String source = readSource("src/main/java/com/termux/app/AppSettingsFragment.java");

        Assert.assertTrue(source.contains("public void showApiToolsPage()"));
        Assert.assertTrue(source.contains("new ApiToolsFragment()"));
        Assert.assertTrue(source.contains("R.string.api_tools_description"));
        Assert.assertTrue(source.contains("setSettingsSubtitle"));
    }

    @Test
    public void apiToolsUsesSharedControllerForActivityAndFragment() throws Exception {
        String activity = readSource("src/main/java/com/termux/app/activities/ApiToolsActivity.java");
        String fragment = readSource("src/main/java/com/termux/app/ApiToolsFragment.java");
        String controller = readSource("src/main/java/com/termux/app/ApiToolsController.java");

        Assert.assertTrue(activity.contains("implements ApiToolsController.Host"));
        Assert.assertTrue(fragment.contains("implements ApiToolsController.Host"));
        Assert.assertTrue(controller.contains("BatteryStatusAPI::getBatteryStatusJson"));
        Assert.assertTrue(controller.contains("WifiAPI::getWifiConnectionInfoJson"));
    }

    @Test
    public void apiToolsLayoutMatchesLightSettingsCards() throws Exception {
        String xml = readSource("src/main/res/layout/activity_api_tools.xml");

        Assert.assertTrue(xml.contains("@color/app_bg_secondary"));
        Assert.assertTrue(xml.contains("@+id/api_tools_back_button"));
        Assert.assertTrue(xml.contains("@+id/api_tools_header_container"));
        Assert.assertTrue(xml.contains("@drawable/bg_section_header"));
        Assert.assertTrue(xml.contains("MaterialCardView"));
        Assert.assertTrue(xml.contains("@color/app_card_bg"));
        Assert.assertFalse(xml.contains("partial_primary_toolbar"));
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
}
