package com.portalagent.mcp.tools;

import android.content.Context;
import android.os.Build;

import com.portalagent.loom.LoomLocalSlaveRuntimeStore;
import com.portalagent.loom.LoomSlaveRegistry;
import com.portalagent.mcp.McpTool;

import org.json.JSONArray;
import org.json.JSONObject;

public class LoomLocalSlavesTool implements McpTool {

    public interface RegistryFactory {
        LoomSlaveRegistry create(Context context);
    }

    private final RegistryFactory registryFactory;

    public LoomLocalSlavesTool() {
        this(LoomSlaveRegistry::forContext);
    }

    LoomLocalSlavesTool(RegistryFactory registryFactory) {
        this.registryFactory = registryFactory == null
            ? LoomSlaveRegistry::forContext
            : registryFactory;
    }

    @Override
    public String getName() {
        return "loom.list_local_slaves";
    }

    @Override
    public String getDescription() {
        return "List the local Loom slave registry used by the Android collaboration page.";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        LoomSlaveRegistry registry = registryFactory.create(context);
        LoomSlaveRegistry.Machine machine = registry.machineOrDefault(defaultMachineName());
        JSONObject result = LoomLocalSlaveRuntimeStore.snapshot(
            machine.computerName,
            registry.list(),
            null);

        JSONArray content = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("type", "text");
        text.put("text", result.toString(2));
        content.put(text);
        return content.toString();
    }

    private static String defaultMachineName() {
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        return model.isEmpty() ? "Android" : model;
    }
}
