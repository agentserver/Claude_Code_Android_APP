package com.termux.app;

import com.portalagent.agentserver.AgentServerCommandBuilder;
import com.portalagent.agentserver.AgentServerFragment;
import com.portalagent.apitools.ApiToolsController;
import com.portalagent.apitools.ApiToolsFragment;
import com.portalagent.automation.ui.AutomationRecipeAdapter;
import com.portalagent.automation.ui.AutomationSettingsFragment;
import com.portalagent.chat.ChatAdapter;
import com.portalagent.chat.ChatMessage;
import com.portalagent.chat.ChatTranscriptStore;
import com.portalagent.chat.ChatTurnOrdering;
import com.portalagent.keys.ApiKeyAdapter;
import com.portalagent.keys.ApiKeyFragment;
import com.portalagent.keys.ApiKeyStore;
import com.portalagent.provider.AssistantProvider;
import com.portalagent.provider.ProviderConfigManager;
import com.portalagent.provider.ProviderEnvironmentWriter;
import com.portalagent.provider.ProviderProfile;
import com.portalagent.provider.ProviderSettingsStore;
import com.portalagent.session.ClaudeStreamSession;
import com.portalagent.session.CodexExecSession;
import com.portalagent.session.SessionAdapter;
import com.portalagent.session.SessionStore;
import com.portalagent.settings.AppSettingsFragment;
import com.portalagent.settings.SettingsHubFragment;
import com.portalagent.settings.SettingsPreferenceStyler;
import com.portalagent.settings.WorkspaceAccessSettingsFragment;
import com.portalagent.settings.WorkspaceAccessSettingsStore;
import com.portalagent.tasks.AgentTask;
import com.portalagent.tasks.AgentTaskDetailFragment;
import com.portalagent.tasks.AgentTaskListAdapter;
import com.portalagent.tasks.AgentTaskStore;
import com.portalagent.tasks.UploadStore;
import com.portalagent.ui.collaboration.CollaborationFragment;
import com.portalagent.ui.collaboration.LoomFragment;
import com.portalagent.ui.home.DrawerFileAdapter;
import com.portalagent.ui.home.HomeFragment;
import com.portalagent.ui.status.FloatingStatusService;
import com.portalagent.util.QrCodeUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PortalAgentPackageStructureTest {

    @Test
    public void phaseOneProductPackagesLiveUnderPortalAgentNamespace() throws Exception {
        assertPackageMoved("automation");
        assertPackageMoved("loom");
        assertPackageMoved("collab");
    }

    @Test
    public void phaseTwoMcpPackageLivesUnderPortalAgentNamespace() throws Exception {
        assertPackageMoved("mcp");
    }

    @Test
    public void phaseTwoManifestUsesPortalAgentMcpServiceClassNames() throws Exception {
        String manifest = readProjectFile("src/main/AndroidManifest.xml");
        Assert.assertTrue(manifest.contains("com.portalagent.mcp.ScreenCaptureService"));
        Assert.assertTrue(manifest.contains("com.portalagent.mcp.McpAccessibilityService"));
        Assert.assertFalse(manifest.contains(".app.mcp.ScreenCaptureService"));
        Assert.assertFalse(manifest.contains(".app.mcp.McpAccessibilityService"));
    }

    @Test
    public void remainingProductPackagesLiveUnderPortalAgentNamespace() throws Exception {
        assertPortalAgentPackage("provider");
        assertPortalAgentPackage("chat");
        assertPortalAgentPackage("session");
        assertPortalAgentPackage("keys");
        assertPortalAgentPackage("settings");
        assertPortalAgentPackage("agentserver");
        assertPortalAgentPackage("tasks");
        assertPortalAgentPackage("apitools");
        assertPortalAgentPackage("setup");
        assertPortalAgentPackage("ui/home");
        assertPortalAgentPackage("ui/collaboration");
        assertPortalAgentPackage("ui/status");
        assertPortalAgentPackage("util");
        assertPortalAgentPackage("activities");
    }

    @Test
    public void oldRootProductClassesAreRemovedFromTermuxAppPackage() {
        String[] oldRootFiles = {
            "AgentServerCommandBuilder.java",
            "AgentServerFragment.java",
            "AgentTask.java",
            "AgentTaskDetailFragment.java",
            "AgentTaskListAdapter.java",
            "AgentTaskStore.java",
            "ApiKeyAdapter.java",
            "ApiKeyFragment.java",
            "ApiKeyStore.java",
            "ApiToolsController.java",
            "ApiToolsFragment.java",
            "AppSettingsFragment.java",
            "AssistantProvider.java",
            "AutomationRecipeAdapter.java",
            "AutomationSettingsFragment.java",
            "ChatAdapter.java",
            "ChatMessage.java",
            "ChatTranscriptStore.java",
            "ChatTurnOrdering.java",
            "ClaudeStreamSession.java",
            "CodexExecSession.java",
            "CollaborationFragment.java",
            "DrawerFileAdapter.java",
            "FloatingStatusService.java",
            "HomeFragment.java",
            "LoomFragment.java",
            "ProviderConfigManager.java",
            "ProviderEnvironmentWriter.java",
            "ProviderProfile.java",
            "ProviderSettingsStore.java",
            "QrCodeUtil.java",
            "SessionAdapter.java",
            "SessionStore.java",
            "SettingsHubFragment.java",
            "SettingsPreferenceStyler.java",
            "UploadStore.java",
            "WorkspaceAccessSettingsFragment.java",
            "WorkspaceAccessSettingsStore.java"
        };
        for (String fileName : oldRootFiles) {
            File oldFile = resolveProjectFile("src/main/java/com/termux/app/" + fileName);
            Assert.assertFalse("Old Termux product class should be removed: " + oldFile,
                oldFile.exists());
        }
        File oldSetupDir = resolveProjectFile("src/main/java/com/termux/app/autotasks");
        Assert.assertFalse("Old Termux setup package should be removed: " + oldSetupDir,
            oldSetupDir.exists());
    }

    private static void assertPackageMoved(String packageName) throws Exception {
        File mainDir = resolveProjectFile("src/main/java/com/portalagent/" + packageName);
        File testDir = resolveProjectFile("src/test/java/com/portalagent/" + packageName);
        File oldMainDir = resolveProjectFile("src/main/java/com/termux/app/" + packageName);

        Assert.assertTrue("Missing PortalAgent main package: " + mainDir, mainDir.isDirectory());
        Assert.assertTrue("Missing PortalAgent test package: " + testDir, testDir.isDirectory());
        Assert.assertFalse("Old Termux product package should be removed: " + oldMainDir,
            oldMainDir.exists());
        File accidentalNestedDir = new File(mainDir, packageName);
        Assert.assertFalse("Accidental nested package directory should not exist: " + accidentalNestedDir,
            accidentalNestedDir.exists());

        List<File> files = new ArrayList<>();
        collectJavaFiles(mainDir, files);
        collectJavaFiles(testDir, files);
        Assert.assertFalse("Expected Java files in " + packageName, files.isEmpty());

        for (File file : files) {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Assert.assertTrue("Wrong package in " + file,
                source.contains("package com.portalagent." + packageName));
            Assert.assertFalse("Old package leaked in " + file,
                source.contains("package com.termux.app." + packageName));
        }
    }

    private static void assertPortalAgentPackage(String packagePath) throws Exception {
        File mainDir = resolveProjectFile("src/main/java/com/portalagent/" + packagePath);
        Assert.assertTrue("Missing PortalAgent main package: " + mainDir, mainDir.isDirectory());

        List<File> files = new ArrayList<>();
        collectJavaFiles(mainDir, files);
        Assert.assertFalse("Expected Java files in " + packagePath, files.isEmpty());

        String packageName = "com.portalagent." + packagePath.replace('/', '.');
        for (File file : files) {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Assert.assertTrue("Wrong package in " + file, source.contains("package " + packageName));
        }
    }

    private static void collectJavaFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectJavaFiles(file, out);
            } else if (file.getName().endsWith(".java")) {
                out.add(file);
            }
        }
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File file = resolveProjectFile(relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File resolveProjectFile(String relativePath) {
        File file = new File(relativePath);
        if (!file.exists()) {
            file = new File("app/" + relativePath);
        }
        return file;
    }
}
