package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

public class ProviderProfileTest {

    @Test
    public void claudeProfileMatchesExistingRuntime() {
        ProviderProfile p = ProviderProfile.forProvider(AssistantProvider.CLAUDE);

        Assert.assertEquals("Claude Code", p.displayName);
        Assert.assertEquals("claude", p.user);
        Assert.assertEquals("/home/claude", p.home);
        Assert.assertEquals("claude", p.cliBinary);
        Assert.assertEquals("ANTHROPIC_API_KEY", p.apiKeyEnv);
        Assert.assertEquals("ANTHROPIC_BASE_URL", p.baseUrlEnv);
        Assert.assertEquals("/home/claude/.claude/memory", p.memoryDir);
        Assert.assertEquals("/home/claude/.claude/commands", p.commandsDir);
        Assert.assertEquals("/home/claude/CLAUDE.md", p.instructionsFile);
        Assert.assertEquals("/home/claude/loom-driver", p.driverProjectDir);
        Assert.assertEquals("/home/claude/loom-driver/config.yaml", p.driverConfigPath);
        Assert.assertEquals("/home/claude/.loom/driver-local", p.driverTokenDir);
        Assert.assertEquals("/home/claude/loom-driver/.mcp.json", p.loomMcpConfigPath);
        Assert.assertEquals("/home/claude/loom-driver/.claude/skills", p.loomSkillsDir);
    }

    @Test
    public void codexProfileUsesSeparateUserAndOpenAiKey() {
        ProviderProfile p = ProviderProfile.forProvider(AssistantProvider.CODEX);

        Assert.assertEquals("Codex", p.displayName);
        Assert.assertEquals("codex", p.user);
        Assert.assertEquals("/home/codex", p.home);
        Assert.assertEquals("codex", p.cliBinary);
        Assert.assertEquals("OPENAI_API_KEY", p.apiKeyEnv);
        Assert.assertEquals("", p.baseUrlEnv);
        Assert.assertEquals("", p.memoryDir);
        Assert.assertEquals("/home/codex/.codex/skills", p.commandsDir);
        Assert.assertEquals("/home/codex/AGENTS.md", p.instructionsFile);
        Assert.assertEquals("/home/codex/loom-driver", p.driverProjectDir);
        Assert.assertEquals("/home/codex/loom-driver/config.yaml", p.driverConfigPath);
        Assert.assertEquals("/home/codex/.loom/driver-local", p.driverTokenDir);
        Assert.assertEquals("/home/codex/.codex/config.toml", p.loomMcpConfigPath);
        Assert.assertEquals("/home/codex/.codex/skills/loom-driver", p.loomSkillsDir);
    }

    @Test
    public void providerIdsRoundTrip() {
        Assert.assertEquals(AssistantProvider.CLAUDE, AssistantProvider.fromId("claude"));
        Assert.assertEquals(AssistantProvider.CODEX, AssistantProvider.fromId("codex"));
        Assert.assertEquals(AssistantProvider.CODEX, AssistantProvider.fromId(""));
        Assert.assertEquals(AssistantProvider.CODEX, AssistantProvider.fromId(null));
        Assert.assertEquals("codex", AssistantProvider.CODEX.id);
    }
}
