package com.termux.app.loom;

import com.termux.app.AssistantProvider;

import org.junit.Assert;
import org.junit.Test;

public class LoomCommandBuilderTest {
    @Test
    public void statusChecksAllRoleBinariesAndProcesses() {
        String script = LoomCommandBuilder.statusScript("/data/data/com.termux/files/usr");

        Assert.assertTrue(script.contains("command -v proot-distro"));
        Assert.assertTrue(script.contains("command -v observer-server"));
        Assert.assertTrue(script.contains("command -v driver-agent"));
        Assert.assertTrue(script.contains("command -v slave-agent"));
        Assert.assertTrue(script.contains(
            "pgrep -f 'observer-server --config /home/codex/\\.loom/observer-local/observer\\.yaml'"));
        Assert.assertTrue(script.contains(
            "pgrep -f 'slave-agent /home/codex/\\.loom/slave-local/config\\.yaml'"));
    }

    @Test
    public void startObserverUsesNohupAndSpecificConfig() {
        String script = LoomCommandBuilder.startObserverScript("/data/data/com.termux/files/usr");

        Assert.assertTrue(script.contains("cd /home/codex/.loom/observer-local"));
        Assert.assertTrue(script.contains("nohup observer-server --config observer.yaml"));
        Assert.assertTrue(script.contains("loom-observer.log"));
    }

    @Test
    public void stopScriptsUseNarrowKillPatterns() {
        Assert.assertTrue(LoomCommandBuilder.stopObserverScript()
            .contains("pkill -f 'observer-server --config /home/codex/\\.loom/observer-local/observer\\.yaml'"));
        Assert.assertTrue(LoomCommandBuilder.stopSlaveScript()
            .contains("pkill -f 'slave-agent /home/codex/\\.loom/slave-local/config\\.yaml'"));
    }

    @Test
    public void registerDriverUsesExpectedProjectPath() {
        String script = LoomCommandBuilder.registerDriverScript();

        Assert.assertTrue(script.contains("/home/codex/loom-driver/driver-agent register"));
        Assert.assertTrue(script.contains("--config /home/codex/loom-driver/config.yaml"));
    }

    @Test
    public void bindDriverUsesCurrentProviderProjectPathAndReadableLabel() {
        String script = LoomCommandBuilder.bindDriverScript(LoomSettings.defaults()
            .withAgentProvider(AssistantProvider.CODEX));

        Assert.assertTrue(script.contains("绑定 Driver 到当前 Agent"));
        Assert.assertTrue(script.contains("proot-distro login --user codex ubuntu"));
        Assert.assertTrue(script.contains("/home/codex/loom-driver/driver-agent register"));
        Assert.assertTrue(script.contains("--config /home/codex/loom-driver/config.yaml"));
        Assert.assertTrue(script.contains("loom-driver-bind.log"));
    }

    @Test
    public void bindDriverIfNeededReusesRegisteredConfigBeforeRegistering() {
        String script = LoomCommandBuilder.bindDriverIfNeededScript(LoomSettings.defaults()
            .withAgentProvider(AssistantProvider.CODEX));

        Assert.assertTrue(script.contains("Driver already registered; reusing existing config"));
        Assert.assertTrue(script.contains("driver_has_identity"));
        Assert.assertTrue(script.contains("driver_server_matches"));
        Assert.assertTrue(script.contains("/home/codex/loom-driver/driver-agent register"));
        Assert.assertTrue(script.contains("tee -a \"$HOME/loom-driver-bind.log\""));
    }

    @Test
    public void lifecycleScriptsFilterWrapperProcessesBeforeMatching() {
        String statusScript = LoomCommandBuilder.statusScript("/data/data/com.termux/files/usr");
        String stopObserverScript = LoomCommandBuilder.stopObserverScript();
        String stopSlaveScript = LoomCommandBuilder.stopSlaveScript();

        Assert.assertTrue(statusScript.contains("loom_pids()"));
        Assert.assertTrue(statusScript.contains("[ \"$p\" = \"$$\" ] && continue"));
        Assert.assertTrue(statusScript.contains("grep -Eq 'bash -lc|proot-distro login' && continue"));
        Assert.assertTrue(stopObserverScript.contains("loom_pids()"));
        Assert.assertTrue(stopSlaveScript.contains("loom_pids()"));
        Assert.assertFalse(stopObserverScript.contains("\npkill -f 'observer-server --config .*observer-local/observer.yaml'"));
        Assert.assertFalse(stopSlaveScript.contains("\npkill -f 'slave-agent .*\\.loom/slave-local/config.yaml'"));
    }

    @Test
    public void observerLifecycleUsesConsistentAbsoluteConfigPath() {
        String startScript = LoomCommandBuilder.startObserverScript("/data/data/com.termux/files/usr");
        String statusScript = LoomCommandBuilder.statusScript("/data/data/com.termux/files/usr");
        String stopScript = LoomCommandBuilder.stopObserverScript();

        Assert.assertTrue(startScript.contains(
            "observer-server --config /home/codex/.loom/observer-local/observer.yaml"));
        Assert.assertTrue(statusScript.contains("observer-local/observer\\.yaml"));
        Assert.assertTrue(stopScript.contains("observer-local/observer\\.yaml"));
    }

    @Test
    public void registerDriverKeepsStdoutVisibleWhileLogging() {
        String script = LoomCommandBuilder.registerDriverScript();

        Assert.assertTrue(script.contains("tee -a \"$HOME/loom-driver-bind.log\""));
        Assert.assertFalse(script.contains(">> \"$HOME/loom-driver-bind.log\" 2>&1"));
    }

    @Test
    public void registerDriverPreservesRegisterExitStatusThroughTee() {
        String script = LoomCommandBuilder.registerDriverScript();

        Assert.assertTrue(script.contains("set -o pipefail") || script.contains("${PIPESTATUS[0]}"));
    }

    @Test
    public void setupConfigWritesAllRoleConfigsAndDriverMcpFile() {
        String script = LoomCommandBuilder.setupConfigScript(claudeSettings());

        Assert.assertTrue(script.contains("/home/claude/.loom/observer-local/observer.yaml"));
        Assert.assertTrue(script.contains("/home/claude/loom-driver/config.yaml"));
        Assert.assertTrue(script.contains("/home/claude/.loom/slave-local/config.yaml"));
        Assert.assertTrue(script.contains("/home/claude/loom-driver/.mcp.json"));
        Assert.assertTrue(script.contains("/home/claude/loom-driver/.claude/skills"));
        Assert.assertTrue(script.contains("base64 -d"));
        Assert.assertTrue(script.contains("chmod 600"));
    }

    @Test
    public void setupConfigPreservesRegisteredDriverConfigByDefault() {
        String script = LoomCommandBuilder.setupConfigScript(LoomSettings.defaults()
            .withAgentProvider(AssistantProvider.CODEX));

        Assert.assertTrue(script.contains("Keeping registered Driver config"));
        Assert.assertTrue(script.contains("driver_has_identity"));
        Assert.assertTrue(script.contains("driver_server_matches"));
        Assert.assertTrue(script.contains("/home/codex/loom-driver/config.yaml.tmp.android"));
    }

    @Test
    public void setupConfigCanResetDriverConfigForExplicitBinding() {
        String script = LoomCommandBuilder.setupConfigScript(LoomSettings.defaults()
            .withAgentProvider(AssistantProvider.CODEX), true);

        Assert.assertFalse(script.contains("Keeping registered Driver config"));
        Assert.assertFalse(script.contains("driver_has_identity"));
        Assert.assertTrue(script.contains("base64 -d > /home/codex/loom-driver/config.yaml"));
    }

    @Test
    public void readDriverConfigScriptOnlyEmitsPublicIdentityFields() {
        String script = LoomCommandBuilder.readDriverConfigScript(LoomSettings.defaults()
            .withAgentProvider(AssistantProvider.CODEX));

        Assert.assertTrue(script.contains("proot-distro login --user codex ubuntu"));
        Assert.assertTrue(script.contains(LoomCommandBuilder.DRIVER_CONFIG_BEGIN_MARKER));
        Assert.assertTrue(script.contains(LoomCommandBuilder.DRIVER_CONFIG_END_MARKER));
        Assert.assertTrue(script.contains("/home/codex/loom-driver/config.yaml"));
        Assert.assertTrue(script.contains("sandbox_id"));
        Assert.assertTrue(script.contains("workspace_id"));
        Assert.assertTrue(script.contains("short_id"));
        Assert.assertFalse(script.contains("tunnel_token"));
        Assert.assertFalse(script.contains("proxy_token"));
    }

    @Test
    public void codexSetupWritesCodexRuntimePaths() {
        LoomSettings settings = LoomSettings.defaults()
            .withAgentProvider(AssistantProvider.CODEX);

        String script = LoomCommandBuilder.setupConfigScript(settings);

        Assert.assertTrue(script.contains("proot-distro login --user codex ubuntu"));
        Assert.assertTrue(script.contains("/home/codex/.loom/observer-local/observer.yaml"));
        Assert.assertTrue(script.contains("/home/codex/loom-driver/config.yaml"));
        Assert.assertTrue(script.contains("/home/codex/.loom/slave-local/config.yaml"));
        Assert.assertTrue(script.contains("/home/codex/.codex/config.toml"));
        Assert.assertTrue(script.contains("[mcp_servers.loom-driver]"));
        Assert.assertTrue(script.contains("command = \"/home/codex/loom-driver/driver-agent\""));
        Assert.assertFalse(script.contains("/home/claude/loom-driver/config.yaml"));
    }

    @Test
    public void codexLifecycleScriptsUseCodexUserPathsAndProcesses() {
        LoomSettings settings = LoomSettings.defaults()
            .withAgentProvider(AssistantProvider.CODEX);

        String statusScript = LoomCommandBuilder.statusScript("/data/data/com.termux/files/usr", settings);
        String startObserverScript = LoomCommandBuilder.startObserverScript(
            "/data/data/com.termux/files/usr", settings);
        String registerScript = LoomCommandBuilder.registerDriverScript(settings);
        String startSlaveScript = LoomCommandBuilder.startSlaveScript(
            "/data/data/com.termux/files/usr", settings);
        String stopObserverScript = LoomCommandBuilder.stopObserverScript(settings);
        String stopSlaveScript = LoomCommandBuilder.stopSlaveScript(settings);

        Assert.assertTrue(statusScript.contains("proot-distro login --user codex ubuntu"));
        Assert.assertTrue(statusScript.contains(
            "observer-server --config /home/codex/\\.loom/observer-local/observer\\.yaml"));
        Assert.assertTrue(startObserverScript.contains("cd /home/codex/.loom/observer-local"));
        Assert.assertTrue(registerScript.contains("/home/codex/loom-driver/driver-agent register"));
        Assert.assertTrue(startSlaveScript.contains("slave-agent /home/codex/.loom/slave-local/config.yaml"));
        Assert.assertTrue(stopObserverScript.contains("/home/codex/\\.loom/observer-local/observer\\.yaml"));
        Assert.assertTrue(stopSlaveScript.contains("/home/codex/\\.loom/slave-local/config\\.yaml"));
        Assert.assertFalse(stopSlaveScript.contains("/home/claude/\\.loom/slave-local/config\\.yaml"));
    }

    @Test
    public void allInOneStartsObserverAndSlave() {
        String script = LoomCommandBuilder.startAllInOneScript(
            "/data/data/com.termux/files/usr", LoomSettings.defaults());

        Assert.assertTrue(script.contains("observer-server --config observer.yaml"));
        Assert.assertTrue(script.contains("slave-agent config.yaml"));
        Assert.assertTrue(script.contains("Driver project is ready"));
    }

    @Test
    public void startScriptsNohupOuterProotCommand() {
        Assert.assertTrue(LoomCommandBuilder.startObserverScript("/data/data/com.termux/files/usr")
            .contains("nohup proot-distro login --user codex ubuntu"));
        Assert.assertTrue(LoomCommandBuilder.startSlaveScript("/data/data/com.termux/files/usr")
            .contains("nohup proot-distro login --user codex ubuntu"));
    }

    @Test
    public void stopScriptsExitZeroWhenNothingIsRunningAndKillExplicitPids() {
        String stopObserverScript = LoomCommandBuilder.stopObserverScript();
        String stopSlaveScript = LoomCommandBuilder.stopSlaveScript();

        Assert.assertTrue(stopObserverScript.contains("not running"));
        Assert.assertTrue(stopObserverScript.contains("exit 0"));
        Assert.assertTrue(stopObserverScript.contains("kill $pids"));
        Assert.assertTrue(stopSlaveScript.contains("not running"));
        Assert.assertTrue(stopSlaveScript.contains("exit 0"));
        Assert.assertTrue(stopSlaveScript.contains("kill $pids"));
        Assert.assertFalse(stopObserverScript.contains("proot-distro login --user claude ubuntu -- bash -lc 'pkill -f"));
        Assert.assertFalse(stopSlaveScript.contains("proot-distro login --user claude ubuntu -- bash -lc 'pkill -f"));
    }

    @Test
    public void stopScriptsTolerateProcessExitBetweenLookupAndKill() {
        Assert.assertTrue(LoomCommandBuilder.stopObserverScript().contains("kill $pids 2>/dev/null || true"));
        Assert.assertTrue(LoomCommandBuilder.stopSlaveScript().contains("kill $pids 2>/dev/null || true"));
    }

    private static LoomSettings claudeSettings() {
        return LoomSettings.defaults().withAgentProvider(AssistantProvider.CLAUDE);
    }
}
