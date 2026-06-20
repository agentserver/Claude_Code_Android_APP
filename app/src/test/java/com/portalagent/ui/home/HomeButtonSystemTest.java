package com.portalagent.ui.home;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

public class HomeButtonSystemTest {

    @Test
    public void homePrimaryControlsUseMobileTouchTargets() throws Exception {
        Document doc = parseLayout("fragment_home.xml");

        assertHeight(doc, "btn_stop_claude", "44dp");
        assertHeight(doc, "btn_new_session", "44dp");
        assertHeight(doc, "home_send_btn", "44dp");
        assertHeight(doc, "btn_attach_file", "44dp");
        assertHeight(doc, "btn_clear_attachment", "44dp");
        assertHeight(doc, "btn_enter", "44dp");
    }

    @Test
    public void permissionStatusChipsAreClickableControls() throws Exception {
        Document doc = parseLayout("fragment_home.xml");

        assertAttribute(doc, "accessibility_status", "android:clickable", "true");
        assertAttribute(doc, "accessibility_status", "android:focusable", "true");
        assertAttribute(doc, "accessibility_status", "android:minHeight", "44dp");
        assertAttribute(doc, "screen_capture_status", "android:clickable", "true");
        assertAttribute(doc, "screen_capture_status", "android:focusable", "true");
        assertAttribute(doc, "screen_capture_status", "android:minHeight", "44dp");
        Assert.assertNull("Accessibility should be controlled by the status chip",
            findById(doc, "btn_accessibility"));
        Assert.assertNull("Screen capture should be controlled by the status chip",
            findById(doc, "btn_screen_capture"));
    }

    @Test
    public void providerSwitchIsCenteredBorderedControl() throws Exception {
        Document doc = parseLayout("fragment_home.xml");

        assertAttribute(doc, "home_session_title", "android:layout_width", "wrap_content");
        assertAttribute(doc, "home_session_title", "android:layout_height", "44dp");
        assertAttribute(doc, "home_session_title", "android:layout_gravity", "center");
        assertAttribute(doc, "home_session_title", "android:background", "@drawable/bg_provider_switch");
        assertAttribute(doc, "home_session_title", "android:clickable", "true");
        assertAttribute(doc, "home_session_title", "android:focusable", "true");
        assertAttribute(doc, "home_session_title", "android:gravity", "center");
        assertAttribute(doc, "home_status_text", "android:layout_gravity", "center_horizontal");
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

    private static void assertHeight(Document doc, String id, String expected) {
        assertAttribute(doc, id, "android:layout_height", expected);
    }

    private static void assertAttribute(Document doc, String id, String attribute, String expected) {
        Element element = findById(doc, id);
        Assert.assertNotNull("Missing view id " + id, element);
        Assert.assertEquals("Unexpected " + attribute + " for " + id,
            expected, element.getAttribute(attribute));
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
