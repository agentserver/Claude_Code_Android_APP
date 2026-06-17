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

public class CollaborationNavigationStructureTest {

    @Test
    public void bottomNavHasCollaborationTabAfterHome() throws Exception {
        Document doc = parseXml("src/main/res/menu/bottom_nav_menu.xml");
        NodeList items = doc.getElementsByTagName("item");

        Assert.assertTrue("Bottom navigation should have five entries",
            items.getLength() >= 5);
        Assert.assertEquals("@+id/nav_home", ((Element) items.item(0)).getAttribute("android:id"));
        Assert.assertEquals("@+id/nav_collaboration", ((Element) items.item(1)).getAttribute("android:id"));
        Assert.assertEquals("协作", ((Element) items.item(1)).getAttribute("android:title"));
    }

    @Test
    public void collaborationDashboardExposesUnifiedRuntimeSections() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_collaboration.xml");

        Assert.assertNotNull(findById(doc, "collaboration_workspace_card"));
        Assert.assertNotNull(findById(doc, "collaboration_runtime_card"));
        Assert.assertNotNull(findById(doc, "collaboration_agentserver_card"));
        Assert.assertNotNull(findById(doc, "collaboration_android_capabilities_card"));
        Assert.assertNotNull(findById(doc, "collaboration_driver_binding_dot"));
        Assert.assertNotNull(findById(doc, "collaboration_driver_binding_status"));
        Assert.assertNotNull(findById(doc, "btn_collaboration_bind_driver"));
        Assert.assertNotNull(findById(doc, "btn_collaboration_agentserver_optional"));
        Assert.assertNull(findById(doc, "collaboration_loom_card"));
        Assert.assertNull(findById(doc, "collaboration_update_area"));
        Assert.assertNotNull(findById(doc, "btn_collaboration_switch_provider"));
        Assert.assertNotNull(findById(doc, "btn_collaboration_switch_role"));
        Assert.assertNotNull(findById(doc, "btn_collaboration_start_role"));
        Assert.assertNotNull(findById(doc, "btn_collaboration_stop_role"));
    }

    @Test
    public void collaborationDashboardUsesStatusListsForDetailSettings() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_collaboration.xml");
        Element workspaceSummary = findById(doc, "collaboration_workspace_summary");
        Element loomSummary = findById(doc, "collaboration_loom_summary");
        Element agentServerButton = findById(doc, "btn_collaboration_agentserver_optional");

        Assert.assertNotNull(workspaceSummary);
        Assert.assertNotEquals("true", workspaceSummary.getAttribute("android:clickable"));
        Assert.assertNotEquals("true", workspaceSummary.getAttribute("android:focusable"));
        Assert.assertNotNull(loomSummary);
        Assert.assertEquals("true", loomSummary.getAttribute("android:clickable"));
        Assert.assertEquals("true", loomSummary.getAttribute("android:focusable"));
        Assert.assertNull(findById(doc, "btn_collaboration_loom_settings"));
        Assert.assertNull(findById(doc, "btn_collaboration_workspace_settings"));
        Assert.assertEquals("连接 AgentServer", agentServerButton.getAttribute("android:text"));
        Assert.assertEquals("false", agentServerButton.getAttribute("android:textAllCaps"));
        Assert.assertEquals("0", agentServerButton.getAttribute("android:letterSpacing"));
    }

    @Test
    public void collaborationWorkspaceSummaryUsesDriverPrimaryWording() throws Exception {
        String xml = readSource("src/main/res/layout/fragment_collaboration.xml");
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertFalse(xml.contains("旧版 AgentServer 连接页中配置/授权"));
        Assert.assertFalse(source.contains("旧版 AgentServer 连接页中配置/授权"));
        Assert.assertTrue(xml.contains("绑定 Driver 后启动本机协作角色"));
        Assert.assertTrue(xml.contains("扫码绑定 Driver 到当前 Agent"));
        Assert.assertFalse(xml.contains("绑定后同步 workspace"));
        Assert.assertTrue(source.contains("工作区：绑定后自动同步"));
        Assert.assertFalse(xml.contains("工作空间：填写 workspace ID 后复用"));
        Assert.assertFalse(source.contains("工作空间：填写 workspace ID 后复用"));
    }

    @Test
    public void collaborationPutsDriverBindingAboveRuntimeAndAgentServerBelowLoom() throws Exception {
        String xml = readSource("src/main/res/layout/fragment_collaboration.xml");
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertTrue(xml.indexOf("btn_collaboration_bind_driver")
            < xml.indexOf("collaboration_runtime_card"));
        Assert.assertTrue(xml.indexOf("btn_collaboration_agentserver_optional")
            > xml.indexOf("collaboration_agentserver_card"));
        Assert.assertTrue(xml.contains("若不使用 Loom 编排，从这里连接 AgentServer"));
        Assert.assertTrue(source.contains("R.id.btn_collaboration_agentserver_optional"));
        Assert.assertFalse(source.contains("R.id.collaboration_workspace_summary).setOnClickListener"));
        Assert.assertFalse(source.contains("R.id.btn_collaboration_workspace_settings"));
    }

    @Test
    public void collaborationMergesRuntimeAndLoomAndRemovesNonUserUpdateCopy() throws Exception {
        String xml = readSource("src/main/res/layout/fragment_collaboration.xml");
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertTrue(xml.contains("本机运行时与 Loom"));
        Assert.assertTrue(xml.contains("AgentServer 连接"));
        Assert.assertFalse(xml.contains("更新区"));
        Assert.assertFalse(xml.contains("安装层保持分包"));
        Assert.assertFalse(source.contains("mUpdateSummary"));
        Assert.assertFalse(source.contains("安装层保持分包"));
    }

    @Test
    public void collaborationRuntimeActionButtonsAreOutlined() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_collaboration.xml");
        Element switchRoleButton = findById(doc, "btn_collaboration_switch_role");
        Element bindDriverButton = findById(doc, "btn_collaboration_bind_driver");
        Element switchProviderButton = findById(doc, "btn_collaboration_switch_provider");
        Element startRoleButton = findById(doc, "btn_collaboration_start_role");
        Element stopRoleButton = findById(doc, "btn_collaboration_stop_role");

        Assert.assertNotNull(switchRoleButton);
        Assert.assertNotNull(bindDriverButton);
        Assert.assertNotNull(switchProviderButton);
        Assert.assertNotNull(startRoleButton);
        Assert.assertNotNull(stopRoleButton);
        Assert.assertTrue(switchRoleButton.getAttribute("style").contains("OutlinedButton"));
        Assert.assertTrue(bindDriverButton.getAttribute("style").contains("OutlinedButton"));
        Assert.assertTrue(switchProviderButton.getAttribute("style").contains("OutlinedButton"));
        Assert.assertTrue(stopRoleButton.getAttribute("style").contains("OutlinedButton"));
        Assert.assertEquals("切换 Agent", switchProviderButton.getAttribute("android:text"));
        Assert.assertEquals("启动当前角色", startRoleButton.getAttribute("android:text"));
        Assert.assertEquals("停止当前角色", stopRoleButton.getAttribute("android:text"));
        Assert.assertEquals("false", switchRoleButton.getAttribute("android:textAllCaps"));
        Assert.assertEquals("false", bindDriverButton.getAttribute("android:textAllCaps"));
        Assert.assertEquals("false", switchProviderButton.getAttribute("android:textAllCaps"));
        Assert.assertEquals("false", startRoleButton.getAttribute("android:textAllCaps"));
        Assert.assertEquals("false", stopRoleButton.getAttribute("android:textAllCaps"));
        Assert.assertEquals("0", switchRoleButton.getAttribute("android:letterSpacing"));
        Assert.assertEquals("0", bindDriverButton.getAttribute("android:letterSpacing"));
        Assert.assertEquals("0", switchProviderButton.getAttribute("android:letterSpacing"));
        Assert.assertEquals("0", startRoleButton.getAttribute("android:letterSpacing"));
        Assert.assertEquals("0", stopRoleButton.getAttribute("android:letterSpacing"));
        Assert.assertEquals("112dp", switchRoleButton.getAttribute("android:layout_width"));
        Assert.assertEquals("112dp", switchProviderButton.getAttribute("android:layout_width"));
        Assert.assertEquals("match_parent", bindDriverButton.getAttribute("android:layout_width"));
        Assert.assertEquals("@color/app_primary_border",
            switchRoleButton.getAttribute("app:strokeColor"));
        Assert.assertEquals("@color/app_primary_border",
            bindDriverButton.getAttribute("app:strokeColor"));
        Assert.assertEquals("@color/app_primary_border",
            switchProviderButton.getAttribute("app:strokeColor"));
        Assert.assertEquals("@color/app_accent",
            startRoleButton.getAttribute("app:strokeColor"));
        Assert.assertEquals("@color/app_warning",
            stopRoleButton.getAttribute("app:strokeColor"));
    }

    @Test
    public void collaborationProviderRowSharesRuntimeActionColumn() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_collaboration.xml");
        Element providerRow = findById(doc, "collaboration_provider_card");

        Assert.assertNotNull(providerRow);
        Assert.assertEquals("wrap_content", providerRow.getAttribute("android:layout_height"));
        Assert.assertEquals("", providerRow.getAttribute("android:background"));
        Assert.assertEquals("", providerRow.getAttribute("android:paddingStart"));
        Assert.assertEquals("", providerRow.getAttribute("android:paddingEnd"));
    }

    @Test
    public void collaborationRuntimeOnlyExplicitControlsAreClickable() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_collaboration.xml");
        Element runtimeCard = findById(doc, "collaboration_runtime_card");
        Element providerRow = findById(doc, "collaboration_provider_card");
        Element localAgentStatus = findById(doc, "collaboration_local_agent_status");
        Element localSlaveStatus = findById(doc, "collaboration_local_slave_status");
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertNotEquals("true", runtimeCard.getAttribute("android:clickable"));
        Assert.assertNotEquals("true", providerRow.getAttribute("android:clickable"));
        Assert.assertNotEquals("true", localAgentStatus.getAttribute("android:clickable"));
        Assert.assertNotEquals("true", localSlaveStatus.getAttribute("android:clickable"));
        Assert.assertFalse(source.contains("R.id.collaboration_runtime_card).setOnClickListener"));
        Assert.assertFalse(source.contains("R.id.collaboration_provider_card)\n            .setOnClickListener"));
        Assert.assertTrue(source.contains("R.id.btn_collaboration_switch_provider"));
    }

    @Test
    public void collaborationRuntimeHidesInternalDefaultSlaveName() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_collaboration.xml");
        Element localSlaveStatus = findById(doc, "collaboration_local_slave_status");
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertNotNull(localSlaveStatus);
        Assert.assertFalse(localSlaveStatus.getAttribute("android:text").contains("slave-phone"));
        Assert.assertFalse(localSlaveStatus.getAttribute("android:text").contains("All-in-one"));
        Assert.assertTrue(localSlaveStatus.getAttribute("android:text").contains("本机身份"));
        Assert.assertTrue(source.contains("本机身份 · "));
    }

    @Test
    public void collaborationFragmentSwitchesProviderAndMarksDriverBindingStale() throws Exception {
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertTrue(source.contains("showProviderDialog()"));
        Assert.assertTrue(source.contains("switchProviderAndMarkDriverStale"));
        Assert.assertTrue(source.contains("markDriverBindingStale"));
        Assert.assertTrue(source.contains("updateDriverBindingDot"));
        Assert.assertFalse(source.contains("switchProviderAndBind"));
        Assert.assertTrue(source.contains("LoomCommandBuilder.bindDriverIfNeededScript"));
    }

    @Test
    public void collaborationFragmentSwitchesRoleWithoutBindingDriver() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_collaboration.xml");
        Element runtimeCard = findById(doc, "collaboration_runtime_card");
        Element switchRoleButton = findById(doc, "btn_collaboration_switch_role");
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertNotEquals("true", runtimeCard.getAttribute("android:clickable"));
        Assert.assertNotNull(switchRoleButton);
        Assert.assertEquals("切换身份", switchRoleButton.getAttribute("android:text"));
        Assert.assertTrue(source.contains("showRoleDialog()"));
        Assert.assertTrue(source.contains("btn_collaboration_switch_role"));
        Assert.assertTrue(source.contains("switchRoleOnly"));
        Assert.assertTrue(source.contains("LoomSettings.KEY_ROLE_MODE"));
        Assert.assertFalse(source.contains("switchRoleAndBind"));
    }

    @Test
    public void collaborationRoleSwitchOnlyOffersObserverAndSlave() throws Exception {
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java")
            .replace("\r\n", "\n");

        Assert.assertTrue(source.contains("private static final String[] ROLE_LABELS = {\n"
            + "        \"Observer\", \"Slave\"\n"
            + "    };"));
        Assert.assertTrue(source.contains("private static final String[] ROLE_VALUES = {\n"
            + "        \"observer\", \"slave\"\n"
            + "    };"));
        Assert.assertFalse(source.contains("\"All-in-one\", \"Observer\", \"Driver\", \"Slave\""));
        Assert.assertFalse(source.contains("\"all\", \"observer\", \"driver\", \"slave\""));
    }

    @Test
    public void collaborationFragmentStartsAndStopsSelectedLoomRole() throws Exception {
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertTrue(source.contains("R.id.btn_collaboration_start_role"));
        Assert.assertTrue(source.contains("R.id.btn_collaboration_stop_role"));
        Assert.assertTrue(source.contains("startSelectedRole"));
        Assert.assertTrue(source.contains("stopSelectedRole"));
        Assert.assertTrue(source.contains("LoomCommandBuilder.startObserverScript"));
        Assert.assertTrue(source.contains("LoomCommandBuilder.startSlaveScript"));
        Assert.assertTrue(source.contains("LoomCommandBuilder.stopObserverScript"));
        Assert.assertTrue(source.contains("LoomCommandBuilder.stopSlaveScript"));
        Assert.assertTrue(source.contains("rememberRuntimeProvider"));
        Assert.assertTrue(source.contains("Driver 尚未绑定"));
        Assert.assertFalse(source.contains("LoomCommandBuilder.startAllInOneScript"));
    }

    @Test
    public void collaborationDriverBindingAllowsServerUrlBeforeWorkspaceKnown() throws Exception {
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertTrue(source.contains("CollaborationConnectionState.canBindDriver(serverUrl, workspaceId)"));
        Assert.assertTrue(source.contains("请先配置协作服务器地址"));
        Assert.assertTrue(source.contains("saveDriverBindingSuccess(profile.provider, identity)"));
        Assert.assertTrue(source.contains("LoomDriverConfigIdentity.parse"));
        Assert.assertFalse(source.contains("请先连接 AgentServer 工作空间。"));
    }

    @Test
    public void collaborationDriverBindingReusesExistingRegistrationAndDismissesAuthDialog() throws Exception {
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertTrue(source.contains("LoomCommandBuilder.setupConfigScript(settings)"));
        Assert.assertTrue(source.contains("LoomCommandBuilder.bindDriverIfNeededScript(settings)"));
        Assert.assertTrue(source.contains("dismissAuthDialog()"));
        Assert.assertFalse(source.contains("LoomCommandBuilder.setupConfigScript(settings, true)"));
    }

    @Test
    public void settingsHubNoLongerOwnsAgentserverAndLoomEntries() throws Exception {
        Document doc = parseXml("src/main/res/layout/fragment_settings_hub.xml");

        Assert.assertNull(findById(doc, "settings_item_agentserver"));
        Assert.assertNull(findById(doc, "settings_item_loom"));
    }

    @Test
    public void collaborationFragmentRoutesToExistingDetailPages() throws Exception {
        String source = readSource("src/main/java/com/termux/app/CollaborationFragment.java");

        Assert.assertTrue(source.contains("showAgentServerMode()"));
        Assert.assertTrue(source.contains("showLoomMode()"));
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
