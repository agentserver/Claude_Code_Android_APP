package com.portalagent.mcp;

import org.junit.Assert;
import org.junit.Test;

public class AndroidMcpPermissionStateTest {

    @Test
    public void detectsAccessibilityServiceFromSecureSettingList() {
        Assert.assertTrue(AndroidMcpPermissionState.containsAccessibilityService(
            "com.other/.Service:com.portalagent/com.portalagent.mcp.McpAccessibilityService",
            "com.portalagent", "com.portalagent.mcp.McpAccessibilityService"));
    }

    @Test
    public void detectsShortAccessibilityServiceComponent() {
        Assert.assertTrue(AndroidMcpPermissionState.containsAccessibilityService(
            "com.portalagent/.mcp.McpAccessibilityService",
            "com.portalagent", "com.portalagent.mcp.McpAccessibilityService"));
    }

    @Test
    public void rejectsMissingAccessibilityService() {
        Assert.assertFalse(AndroidMcpPermissionState.containsAccessibilityService(
            "com.other/.Service", "com.portalagent", "com.portalagent.mcp.McpAccessibilityService"));
    }
}
