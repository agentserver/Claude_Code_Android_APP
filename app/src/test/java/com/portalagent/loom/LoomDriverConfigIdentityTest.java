package com.portalagent.loom;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class LoomDriverConfigIdentityTest {

    @Test
    public void parsesPublicDriverIdentityWithoutExposingTokens() {
        String yaml = ""
            + "server:\n"
            + "  url: \"https://agent.cs.ac.cn\"\n"
            + "  name: \"driver-xreal\"\n"
            + "credentials:\n"
            + "  sandbox_id: \"sb-1\"\n"
            + "  tunnel_token: \"secret-tunnel\"\n"
            + "  proxy_token: \"secret-proxy\"\n"
            + "  workspace_id: \"ws-1\"\n"
            + "  short_id: \"abc123\"\n";

        LoomDriverConfigIdentity identity = LoomDriverConfigIdentity.parse(yaml);

        Assert.assertEquals("https://agent.cs.ac.cn", identity.serverUrl);
        Assert.assertEquals("sb-1", identity.sandboxId);
        Assert.assertEquals("ws-1", identity.workspaceId);
        Assert.assertEquals("abc123", identity.shortId);
        Assert.assertTrue(identity.hasRemoteIdentity());
        for (Field field : LoomDriverConfigIdentity.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            Assert.assertFalse("Driver identity must not expose token field: " + field.getName(),
                name.contains("token"));
        }
    }

    @Test
    public void treatsBlankCredentialsAsNotRegistered() {
        String yaml = ""
            + "server:\n"
            + "  url: \"https://agent.cs.ac.cn\"\n"
            + "credentials:\n"
            + "  sandbox_id: \"\"\n"
            + "  workspace_id: \"\"\n"
            + "  short_id: \"\"\n";

        LoomDriverConfigIdentity identity = LoomDriverConfigIdentity.parse(yaml);

        Assert.assertEquals("https://agent.cs.ac.cn", identity.serverUrl);
        Assert.assertFalse(identity.hasRemoteIdentity());
    }
}
