package com.portalagent.activities;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilderFactory;

public class SettingsActivityChromeTest {

    @Test
    public void settingsActivityUsesInAppSettingsChrome() throws Exception {
        Document doc = parseXml("src/main/res/layout/activity_settings.xml");
        String xml = readSource("src/main/res/layout/activity_settings.xml");
        String source = readSource("src/main/java/com/portalagent/activities/SettingsActivity.java");

        Element root = doc.getDocumentElement();
        Assert.assertEquals("ScrollView", root.getTagName());
        Assert.assertEquals("@color/app_bg_secondary", root.getAttribute("android:background"));
        Assert.assertEquals("true", root.getAttribute("android:fillViewport"));
        Assert.assertNotNull(findById(doc, "settings_activity_back_button"));
        Assert.assertNotNull(findById(doc, "settings_activity_title"));
        Assert.assertNotNull(findById(doc, "settings_activity_subtitle"));
        Assert.assertNotNull(findById(doc, "settings"));
        Assert.assertFalse(xml.contains("partial_primary_toolbar"));
        Assert.assertTrue(xml.contains("@drawable/ic_arrow_back_24"));
        Assert.assertTrue(xml.contains("应用设置"));
        Assert.assertTrue(xml.contains("@string/app_settings_subtitle"));
        Assert.assertTrue(source.contains("R.id.settings_activity_back_button"));
        Assert.assertTrue(source.contains("R.id.settings_activity_title"));
        Assert.assertTrue(source.contains("window.setNavigationBarColor(backgroundColor)"));
        Assert.assertFalse(source.contains("AppCompatActivityUtils.setToolbar"));
    }

    @Test
    public void appSettingsPreferencesUseLightCardStyles() throws Exception {
        String themes = readSource("src/main/res/values/themes.xml");

        Assert.assertTrue(themes.contains("<item name=\"preferenceTheme\">@style/ThemeOverlay.ClaudeSettings.Preference</item>"));
        Assert.assertTrue(themes.contains("<item name=\"android:textColorPrimary\">@color/app_text_primary</item>"));
        Assert.assertTrue(themes.contains("<item name=\"android:textColorSecondary\">@color/app_text_secondary</item>"));
        Assert.assertTrue(themes.contains("<item name=\"layout\">@layout/preference_settings_item</item>"));
        Assert.assertTrue(themes.contains("<item name=\"layout\">@layout/preference_settings_category</item>"));
    }

    private static Document parseXml(String relativePath) throws Exception {
        File file = resolveProjectFile(relativePath);
        Assert.assertTrue("Missing XML file: " + file.getAbsolutePath(), file.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(file);
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

    private static Element findById(Document doc, String id) {
        NodeList all = doc.getElementsByTagName("*");
        String suffix = "/" + id;
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            if (element.getAttribute("android:id").endsWith(suffix)) {
                return element;
            }
        }
        return null;
    }
}
