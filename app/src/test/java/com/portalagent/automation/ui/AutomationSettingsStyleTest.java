package com.portalagent.automation.ui;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

public class AutomationSettingsStyleTest {

    @Test
    public void automationSettingsUsesBlueWhiteTheme() throws Exception {
        Document doc = parseLayout("fragment_automation_settings.xml");

        Element root = doc.getDocumentElement();
        Assert.assertEquals("@color/app_surface", root.getAttribute("android:background"));

        Element boostSwitch = findById(doc, "automation_boost_enabled");
        Assert.assertNotNull(boostSwitch);
        Assert.assertEquals("@color/automation_switch_thumb",
            boostSwitch.getAttribute("app:thumbTint"));
        Assert.assertEquals("@color/automation_switch_track",
            boostSwitch.getAttribute("app:trackTint"));

        Element summary = findById(doc, "automation_runtime_summary");
        Assert.assertNotNull(summary);
        Assert.assertEquals("@color/app_primary", summary.getAttribute("android:textColor"));
    }

    private static Document parseLayout(String fileName) throws Exception {
        File file = new File("src/main/res/layout/" + fileName);
        if (!file.isFile()) {
            file = new File("app/src/main/res/layout/" + fileName);
        }
        Assert.assertTrue("Missing layout file: " + file.getAbsolutePath(), file.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(file);
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
