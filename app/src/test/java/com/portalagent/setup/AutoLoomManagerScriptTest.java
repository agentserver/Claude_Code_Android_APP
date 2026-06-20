package com.portalagent.setup;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoLoomManagerScriptTest {

    @Test
    public void innerScriptInstallsRequiredBinariesAtomically() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("_tgz='/tmp/loom-linux-arm64.tgz'"));
        assertTrue(script.contains("observer-server"));
        assertTrue(script.contains("driver-agent"));
        assertTrue(script.contains("slave-agent"));
        assertTrue(script.contains("/usr/local/bin/observer-server.new"));
        assertTrue(script.contains("mv -f /usr/local/bin/observer-server.new /usr/local/bin/observer-server"));
        assertTrue(script.contains("mv -f /usr/local/bin/driver-agent.new /usr/local/bin/driver-agent"));
        assertTrue(script.contains("mv -f /usr/local/bin/slave-agent.new /usr/local/bin/slave-agent"));
    }

    @Test
    public void innerScriptHasOnlineFallbackAndPartFiles() {
        String script = AutoLoomManager.buildInnerScriptForTest(false);

        assertTrue(script.contains("github.com/agentserver/loom/releases/latest/download"));
        assertTrue(script.contains(".part"));
        assertTrue(script.contains("driver-skills.tar.gz"));
        assertTrue(script.contains("sha256sums.txt"));
        assertTrue(script.contains("verify_asset()"));
        assertTrue(script.contains("sha256sum"));
    }

    @Test
    public void innerScriptFallsBackWhenLocalArchiveIsInvalid() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("Local Loom archive is invalid; falling back to online assets."));
        assertTrue(script.contains("_local_ok=0"));
        assertTrue(script.contains("if [ \"$_local_ok\" != \"1\" ]; then"));
    }

    @Test
    public void innerScriptKeepsInstalledBinaryWhenRequiredDownloadFails() {
        String script = AutoLoomManager.buildInnerScriptForTest(false);

        assertTrue(script.contains("ensure_required_asset()"));
        assertTrue(script.contains("command -v \"$_cmd\""));
        assertTrue(script.contains("keeping installed $_cmd"));
    }

    @Test
    public void innerScriptCreatesRuntimeDirectories() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("/home/claude/.loom/observer-local"));
        assertTrue(script.contains("/home/claude/.loom/slave-local"));
        assertTrue(script.contains("/home/claude/loom-driver"));
        assertTrue(script.contains("/home/codex/.loom/observer-local"));
        assertTrue(script.contains("/home/codex/.loom/slave-local"));
        assertTrue(script.contains("/home/codex/loom-driver"));
        assertTrue(script.contains("chown -R claude:claude /home/claude/.loom /home/claude/loom-driver"));
        assertTrue(script.contains("chown -R codex:codex /home/codex/.loom /home/codex/loom-driver /home/codex/.codex"));
    }

    @Test
    public void innerScriptUsesLocalArchiveLayoutAndInstallsSkills() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("\"$_tmpdir/loom/bin/observer-server\""));
        assertTrue(script.contains("\"$_tmpdir/loom/bin/driver-agent\""));
        assertTrue(script.contains("\"$_tmpdir/loom/bin/slave-agent\""));
        assertTrue(script.contains("\"$_tmpdir/loom/skills\""));
        assertTrue(script.contains("/home/claude/loom-driver/.claude/skills"));
        assertTrue(script.contains("/home/codex/.codex/skills/loom-driver"));
        assertTrue(script.contains("/home/codex/loom-driver/AGENTS.md"));
    }

    @Test
    public void innerScriptCleansOwnBashrcHook() {
        String script = AutoLoomManager.buildInnerScriptForTest(true);

        assertTrue(script.contains("sed -i '/.loom-setup/d' ~/.bashrc"));
        assertTrue(script.contains("rm -f ~/.loom-setup.sh"));
    }
}
