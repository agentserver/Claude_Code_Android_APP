package com.portalagent.mcp.tools;

import com.portalagent.provider.AssistantProvider;
import com.portalagent.loom.LoomSlave;
import com.portalagent.loom.LoomSlaveRegistry;
import com.portalagent.loom.LoomSlaveStatus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class LoomLocalSlavesToolTest {

    @Test
    public void listsOnlyLocalRegistrySlavesAfterDeletion() throws Exception {
        MemoryStore store = new MemoryStore();
        LoomSlaveRegistry registry = new LoomSlaveRegistry(store);
        LoomSlaveRegistry.Machine machine = registry.ensureMachine("BeamPro_Codex");
        LoomSlave first = registry.create(machine, "/home/codex", "slave1", AssistantProvider.CODEX);
        LoomSlave second = registry.create(machine, "/home/codex/project", "slave2", AssistantProvider.CODEX);
        registry.updateRuntime(first.id, LoomSlaveStatus.RUNNING, 26300, "", "");
        registry.updateRuntime(second.id, LoomSlaveStatus.STOPPED, 0, "", "");
        registry.delete(second.id);

        LoomLocalSlavesTool tool = new LoomLocalSlavesTool(context -> registry);
        JSONArray content = new JSONArray(tool.call(new JSONObject(), null));
        JSONObject result = new JSONObject(content.getJSONObject(0).getString("text"));
        JSONArray slaves = result.getJSONArray("slaves");

        Assert.assertEquals("loom.list_local_slaves", tool.getName());
        Assert.assertTrue(result.getBoolean("local_only"));
        Assert.assertEquals(1, result.getInt("raw_count"));
        Assert.assertEquals(1, result.getInt("visible_count"));
        Assert.assertEquals("BeamPro_Codex-slave1", slaves.getJSONObject(0).getString("display_name"));
        Assert.assertEquals("running", slaves.getJSONObject(0).getString("status"));
    }

    private static final class MemoryStore implements LoomSlaveRegistry.Store {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }
}
