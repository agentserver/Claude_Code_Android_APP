package com.portalagent.loom;

import com.portalagent.provider.AssistantProvider;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class LoomSlaveListPolicyTest {

    @Test
    public void visibleSlavesDedupesDisplayNameAndKeepsBestRuntimeEntry() {
        LoomSlave oldStopped = slave("old", "BeamPro-worker", LoomSlaveStatus.STOPPED, 10);
        LoomSlave running = slave("running", "BeamPro-worker", LoomSlaveStatus.RUNNING, 20);
        LoomSlave other = slave("other", "BeamPro-other", LoomSlaveStatus.PAUSED, 30);

        List<LoomSlave> visible = LoomSlaveListPolicy.visibleSlaves(
            Arrays.asList(oldStopped, running, other));

        Assert.assertEquals(2, visible.size());
        Assert.assertEquals("running", visible.get(0).id);
        Assert.assertEquals("other", visible.get(1).id);
    }

    @Test
    public void visibleSlavesKeepsNewestWhenSameNameHasSameRuntimePriority() {
        LoomSlave older = slave("older", "BeamPro-worker", LoomSlaveStatus.STOPPED, 10);
        LoomSlave newer = slave("newer", "BeamPro-worker", LoomSlaveStatus.STOPPED, 20);

        List<LoomSlave> visible = LoomSlaveListPolicy.visibleSlaves(
            Arrays.asList(older, newer));

        Assert.assertEquals(1, visible.size());
        Assert.assertEquals("newer", visible.get(0).id);
    }

    @Test
    public void agentUsableStatusOnlyTreatsRunningSlaveAsAvailable() {
        Assert.assertTrue(LoomSlaveListPolicy.isAgentUsableStatus(LoomSlaveStatus.RUNNING));
        Assert.assertFalse(LoomSlaveListPolicy.isAgentUsableStatus(LoomSlaveStatus.STOPPED));
        Assert.assertFalse(LoomSlaveListPolicy.isAgentUsableStatus(LoomSlaveStatus.PAUSED));
        Assert.assertFalse(LoomSlaveListPolicy.isAgentUsableStatus(LoomSlaveStatus.ERROR));
        Assert.assertFalse(LoomSlaveListPolicy.isAgentUsableStatus(LoomSlaveStatus.AUTH_REQUIRED));
    }

    private static LoomSlave slave(String id, String displayName, String status, long updatedAt) {
        return new LoomSlave(
            id,
            "worker",
            displayName,
            "/home/codex/repo",
            "/home/codex/.loom/slaves/" + id + "/config.yaml",
            "/home/codex/.loom/slaves/" + id + "/logs/slave.log",
            AssistantProvider.CODEX.id,
            status,
            0,
            "",
            "",
            1,
            updatedAt);
    }
}
