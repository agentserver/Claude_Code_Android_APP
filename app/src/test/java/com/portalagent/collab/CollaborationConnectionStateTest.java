package com.portalagent.collab;

import com.portalagent.provider.AssistantProvider;

import org.junit.Assert;
import org.junit.Test;

public class CollaborationConnectionStateTest {

    @Test
    public void driverFingerprintChangesWithWorkspaceAndProvider() {
        String codexFingerprint = CollaborationConnectionState.computeDriverFingerprint(
            AssistantProvider.CODEX,
            "https://agent.example",
            "sandbox-a",
            "xreal",
            "driver-phone",
            "/home/codex/.agent/driver/config.yaml",
            "/home/codex/.codex/config.toml");
        String otherWorkspace = CollaborationConnectionState.computeDriverFingerprint(
            AssistantProvider.CODEX,
            "https://agent.example",
            "sandbox-b",
            "xreal",
            "driver-phone",
            "/home/codex/.agent/driver/config.yaml",
            "/home/codex/.codex/config.toml");
        String claudeFingerprint = CollaborationConnectionState.computeDriverFingerprint(
            AssistantProvider.CLAUDE,
            "https://agent.example",
            "sandbox-a",
            "xreal",
            "driver-phone",
            "/home/claude/.agent/driver/config.yaml",
            "/home/claude/.claude/mcp.json");

        Assert.assertNotEquals(codexFingerprint, otherWorkspace);
        Assert.assertNotEquals(codexFingerprint, claudeFingerprint);
    }

    @Test
    public void driverStatusClassifiesMissingValidStaleAndFailed() {
        String current = CollaborationConnectionState.computeDriverFingerprint(
            AssistantProvider.CODEX,
            "https://agent.example",
            "sandbox-a",
            "xreal",
            "driver-phone",
            "/driver/config.yaml",
            "/codex/config.toml");

        Assert.assertEquals(CollaborationConnectionState.DRIVER_STATUS_MISSING,
            CollaborationConnectionState.driverBindingStatus("", current, ""));
        Assert.assertEquals(CollaborationConnectionState.DRIVER_STATUS_VALID,
            CollaborationConnectionState.driverBindingStatus(
                current, current, CollaborationConnectionState.DRIVER_STATUS_VALID));
        Assert.assertEquals(CollaborationConnectionState.DRIVER_STATUS_STALE,
            CollaborationConnectionState.driverBindingStatus(
                "old", current, CollaborationConnectionState.DRIVER_STATUS_VALID));
        Assert.assertEquals(CollaborationConnectionState.DRIVER_STATUS_FAILED,
            CollaborationConnectionState.driverBindingStatus(
                current, current, CollaborationConnectionState.DRIVER_STATUS_FAILED));
    }

    @Test
    public void observerCanStartWithoutDriverButSlaveRequiresValidDriverBinding() {
        Assert.assertTrue(CollaborationConnectionState.canStartRole(
            "observer", CollaborationConnectionState.DRIVER_STATUS_MISSING));
        Assert.assertFalse(CollaborationConnectionState.canStartRole(
            "slave", CollaborationConnectionState.DRIVER_STATUS_MISSING));
        Assert.assertFalse(CollaborationConnectionState.canStartRole(
            "slave", CollaborationConnectionState.DRIVER_STATUS_STALE));
        Assert.assertTrue(CollaborationConnectionState.canStartRole(
            "slave", CollaborationConnectionState.DRIVER_STATUS_VALID));
    }

    @Test
    public void validDriverStatusBecomesStaleWhenCredentialProbeFails() {
        Assert.assertEquals(CollaborationConnectionState.DRIVER_STATUS_STALE,
            CollaborationConnectionState.driverStatusAfterCredentialProbe(
                CollaborationConnectionState.DRIVER_STATUS_VALID,
                false));
        Assert.assertEquals(CollaborationConnectionState.DRIVER_STATUS_VALID,
            CollaborationConnectionState.driverStatusAfterCredentialProbe(
                CollaborationConnectionState.DRIVER_STATUS_VALID,
                true));
        Assert.assertEquals(CollaborationConnectionState.DRIVER_STATUS_FAILED,
            CollaborationConnectionState.driverStatusAfterCredentialProbe(
                CollaborationConnectionState.DRIVER_STATUS_FAILED,
                false));
    }

    @Test
    public void driverBindingCanStartWithServerUrlBeforeWorkspaceIsKnown() {
        Assert.assertTrue(CollaborationConnectionState.canBindDriver(
            "https://agent.cs.ac.cn/",
            ""));
        Assert.assertTrue(CollaborationConnectionState.canBindDriver(
            "",
            "ws-phone"));
        Assert.assertFalse(CollaborationConnectionState.canBindDriver(
            "",
            ""));
    }
}
