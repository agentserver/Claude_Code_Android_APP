package com.portalagent.provider;

public final class ProviderProfile {

    public final AssistantProvider provider;
    public final String displayName;
    public final String user;
    public final String home;
    public final String cliBinary;
    public final String apiKeyEnv;
    public final String baseUrlEnv;
    public final String memoryDir;
    public final String commandsDir;
    public final String instructionsFile;
    public final String driverProjectDir;
    public final String driverConfigPath;
    public final String driverTokenDir;
    public final String loomMcpConfigPath;
    public final String loomSkillsDir;

    private ProviderProfile(
        AssistantProvider provider,
        String displayName,
        String user,
        String home,
        String cliBinary,
        String apiKeyEnv,
        String baseUrlEnv,
        String memoryDir,
        String commandsDir,
        String instructionsFile,
        String driverProjectDir,
        String driverConfigPath,
        String driverTokenDir,
        String loomMcpConfigPath,
        String loomSkillsDir) {
        this.provider = provider;
        this.displayName = displayName;
        this.user = user;
        this.home = home;
        this.cliBinary = cliBinary;
        this.apiKeyEnv = apiKeyEnv;
        this.baseUrlEnv = baseUrlEnv == null ? "" : baseUrlEnv;
        this.memoryDir = memoryDir;
        this.commandsDir = commandsDir;
        this.instructionsFile = instructionsFile;
        this.driverProjectDir = driverProjectDir;
        this.driverConfigPath = driverConfigPath;
        this.driverTokenDir = driverTokenDir;
        this.loomMcpConfigPath = loomMcpConfigPath;
        this.loomSkillsDir = loomSkillsDir;
    }

    public static ProviderProfile forProvider(AssistantProvider provider) {
        if (provider == AssistantProvider.CODEX) {
            return new ProviderProfile(
                AssistantProvider.CODEX,
                "Codex",
                "codex",
                "/home/codex",
                "codex",
                "OPENAI_API_KEY",
                "",
                "",
                "/home/codex/.codex/skills",
                "/home/codex/AGENTS.md",
                "/home/codex/loom-driver",
                "/home/codex/loom-driver/config.yaml",
                "/home/codex/.loom/driver-local",
                "/home/codex/.codex/config.toml",
                "/home/codex/.codex/skills/loom-driver");
        }
        return new ProviderProfile(
            AssistantProvider.CLAUDE,
            "Claude Code",
            "claude",
            "/home/claude",
            "claude",
            "ANTHROPIC_API_KEY",
            "ANTHROPIC_BASE_URL",
            "/home/claude/.claude/memory",
            "/home/claude/.claude/commands",
            "/home/claude/CLAUDE.md",
            "/home/claude/loom-driver",
            "/home/claude/loom-driver/config.yaml",
            "/home/claude/.loom/driver-local",
            "/home/claude/loom-driver/.mcp.json",
            "/home/claude/loom-driver/.claude/skills");
    }
}
