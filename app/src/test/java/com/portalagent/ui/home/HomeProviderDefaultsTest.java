package com.portalagent.ui.home;

import com.portalagent.agentserver.AgentServerFragment;
import com.portalagent.keys.ApiKeyFragment;
import com.portalagent.provider.AssistantProvider;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class HomeProviderDefaultsTest {

    @Test
    public void homeProviderPickerDefaultsToCodexFirst() throws Exception {
        String source = readSource("src/main/java/com/portalagent/ui/home/HomeFragment.java");

        Assert.assertTrue(source.contains("mProvider = AssistantProvider.CODEX"));
        Assert.assertTrue(source.contains("String[] labels = {\"Codex\", \"Claude Code\"}"));
        Assert.assertTrue(source.contains("int checked = mProvider == AssistantProvider.CLAUDE ? 1 : 0"));
        Assert.assertTrue(source.contains("if (next == null) next = AssistantProvider.CODEX"));
    }

    @Test
    public void providerAwarePagesUseCodexAsFieldDefault() throws Exception {
        String agentServer = readSource("src/main/java/com/portalagent/agentserver/AgentServerFragment.java");
        String apiKey = readSource("src/main/java/com/portalagent/keys/ApiKeyFragment.java");

        Assert.assertTrue(agentServer.contains("mProvider = AssistantProvider.CODEX"));
        Assert.assertTrue(apiKey.contains("mProvider = AssistantProvider.CODEX"));
    }

    private static String readSource(String relativePath) throws Exception {
        File file = new File(relativePath);
        if (!file.isFile()) file = new File("app/" + relativePath);
        Assert.assertTrue("Missing source file: " + file.getAbsolutePath(), file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
