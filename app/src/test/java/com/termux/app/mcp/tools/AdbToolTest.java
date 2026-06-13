package com.termux.app.mcp.tools;

import android.content.Context;

import com.termux.app.mcp.AdbCompanionClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class AdbToolTest {

    @Test
    public void tapForwardsCoordinatesToCompanion() throws Exception {
        FakeClient client = new FakeClient(new JSONObject()
            .put("ok", true)
            .put("message", "tapped"));
        AdbTool tool = new AdbTool(AdbTool.Kind.TAP, client);

        String raw = tool.call(new JSONObject().put("x", 12).put("y", 34), context());

        Assert.assertEquals("tap", client.action);
        Assert.assertEquals(12, client.arguments.optInt("x"));
        Assert.assertEquals(34, client.arguments.optInt("y"));
        Assert.assertTrue(raw.contains("tapped"));
    }

    @Test
    public void screenshotReturnsImageContentFromCompanion() throws Exception {
        FakeClient client = new FakeClient(new JSONObject()
            .put("ok", true)
            .put("mime_type", "image/png")
            .put("image_base64", "abc123"));
        AdbTool tool = new AdbTool(AdbTool.Kind.SCREENSHOT, client);

        JSONArray content = new JSONArray(tool.call(new JSONObject(), context()));
        JSONObject image = content.getJSONObject(0);

        Assert.assertEquals("screenshot", client.action);
        Assert.assertEquals("image", image.optString("type"));
        Assert.assertEquals("abc123", image.optString("data"));
        Assert.assertEquals("image/png", image.optString("mimeType"));
    }

    @Test
    public void statusReportsDisconnectedCompanionWithoutThrowing() throws Exception {
        FakeClient client = new FakeClient(new java.io.IOException("connection refused"));
        AdbTool tool = new AdbTool(AdbTool.Kind.GET_STATUS, client);

        JSONArray content = new JSONArray(tool.call(new JSONObject(), context()));
        String text = content.getJSONObject(0).optString("text");

        Assert.assertTrue(text.contains("\"ok\": false"));
        Assert.assertTrue(text.contains("ADB Companion 未连接"));
    }

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static final class FakeClient extends AdbCompanionClient {
        private final JSONObject response;
        private final Exception error;
        String action;
        JSONObject arguments;

        FakeClient(JSONObject response) {
            super("http://127.0.0.1:1", 10);
            this.response = response;
            this.error = null;
        }

        FakeClient(Exception error) {
            super("http://127.0.0.1:1", 10);
            this.response = null;
            this.error = error;
        }

        @Override
        public JSONObject call(String action, JSONObject arguments) throws Exception {
            this.action = action;
            this.arguments = arguments;
            if (error != null) throw error;
            return response;
        }
    }
}
