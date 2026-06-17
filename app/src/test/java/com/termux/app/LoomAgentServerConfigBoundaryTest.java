package com.termux.app;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilderFactory;

public class LoomAgentServerConfigBoundaryTest {

    @Test
    public void loomPageDoesNotExposeAgentServerConnectionFields() throws Exception {
        Document doc = parseLayout("fragment_loom.xml");
        String xml = readLayout("fragment_loom.xml");

        Assert.assertNull(findById(doc, "loom_agentserver_url"));
        Assert.assertNull(findById(doc, "loom_workspace_id"));
        Assert.assertFalse(xml.contains("AgentServer URL"));
        Assert.assertFalse(xml.contains("Workspace ID"));
        Assert.assertNotNull(findById(doc, "loom_observer_api_key"));
        Assert.assertTrue(xml.contains("Observer API Key"));
    }

    @Test
    public void agentServerPageOwnsWorkspaceConnectionWording() throws Exception {
        String xml = readLayout("fragment_agent_server.xml");

        Assert.assertTrue(xml.contains("工作空间 / 沙盒 ID"));
        Assert.assertTrue(xml.contains("Driver 绑定是主要协作入口"));
        Assert.assertTrue(xml.contains("Codex Connector 是可选 Web 连接"));
    }

    @Test
    public void loomFragmentDerivesConnectionFromAgentServerPrefs() throws Exception {
        String source = readSource("src/main/java/com/termux/app/LoomFragment.java");

        Assert.assertTrue(source.contains("AGENTSERVER_PREFS_NAME"));
        Assert.assertTrue(source.contains("agentServerBackedSettings"));
        Assert.assertTrue(source.contains("KEY_AGENTSERVER_URL"));
        Assert.assertTrue(source.contains("KEY_AGENTSERVER_SANDBOX_ID"));
        Assert.assertFalse(source.contains("R.id.loom_agentserver_url"));
        Assert.assertFalse(source.contains("R.id.loom_workspace_id"));
        Assert.assertFalse(source.contains("putString(LoomSettings.KEY_AGENTSERVER_URL"));
        Assert.assertFalse(source.contains("putString(LoomSettings.KEY_WORKSPACE_ID"));
    }

    private static Document parseLayout(String fileName) throws Exception {
        File file = resolveProjectFile("src/main/res/layout/" + fileName);
        Assert.assertTrue("Missing layout file: " + file.getAbsolutePath(), file.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(file);
    }

    private static String readLayout(String fileName) throws Exception {
        File file = resolveProjectFile("src/main/res/layout/" + fileName);
        Assert.assertTrue("Missing layout file: " + file.getAbsolutePath(), file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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
