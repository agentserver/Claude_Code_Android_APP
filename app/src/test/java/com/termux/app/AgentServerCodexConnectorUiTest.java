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

public class AgentServerCodexConnectorUiTest {

    @Test
    public void agentServerPageHasSeparateCodexConnectorCommandField() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_agent_server.xml");

        Assert.assertNotNull(findById(doc, "agentserver_code_label"));
        Assert.assertNotNull(findById(doc, "agentserver_codex_command_label"));
        Element command = findById(doc, "agentserver_codex_command");
        Assert.assertNotNull(command);
        Assert.assertEquals("textMultiLine", command.getAttribute("android:inputType"));
        Assert.assertEquals("4", command.getAttribute("android:minLines"));
    }

    @Test
    public void agentServerFragmentUsesCodexConnectorCommandInsteadOfCodexBackendProbe() throws Exception {
        String source = readSource("src/main/java/com/termux/app/AgentServerFragment.java");

        Assert.assertTrue(source.contains("KEY_CODEX_CONNECT_COMMAND"));
        Assert.assertTrue(source.contains("mCodexCommandEdit"));
        Assert.assertTrue(source.contains("mProvider == AssistantProvider.CLAUDE && apiKey.isEmpty()"));
        Assert.assertTrue(source.contains("Codex Connector"));
        Assert.assertTrue(source.contains("new AgentServerCommandBuilder.Config("));
        Assert.assertTrue(source.contains("codexConnectCommand"));
        Assert.assertFalse(source.contains("当前 AgentServer 不支持 Codex 后端"));
        Assert.assertFalse(source.contains("请先激活 \" + profile.displayName + \" API Key"));
        Assert.assertTrue(source.contains("mProvider == AssistantProvider.CLAUDE && (line.contains(\"Failed to load session\")"));
    }

    @Test
    public void commandBuilderDoesNotRetainLegacyCodexSubcommandProbe() throws Exception {
        String source = readSource("src/main/java/com/termux/app/AgentServerCommandBuilder.java");

        Assert.assertTrue(source.contains("codexConnectScript"));
        Assert.assertTrue(source.contains("exec-server"));
        Assert.assertTrue(source.contains("--remote"));
        Assert.assertFalse(source.contains("agentserver help"));
        Assert.assertFalse(source.contains("supportProbeFor"));
        Assert.assertFalse(source.contains("不支持 Codex"));
        Assert.assertFalse(source.contains("does not support Codex"));
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
