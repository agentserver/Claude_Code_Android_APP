package com.termux.app.mcp.tools;

import android.content.Context;
import android.util.Base64;

import com.termux.app.mcp.McpTool;
import com.termux.app.mcp.ScreenCaptureService;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * screen.capture — captures the current device screen via MediaProjection.
 *
 * Returns a base64-encoded JPEG image content item.
 * If the screen capture permission has not been granted in the app UI,
 * returns a descriptive text error instead of throwing.
 */
public class ScreenCaptureTool implements McpTool {

    @Override public String getName() { return "screen.capture"; }

    @Override public String getDescription() {
        return "Capture the current device screen as a JPEG image. " +
               "Returns base64-encoded image data. Requires the user to grant " +
               "screen capture permission via '授权截图' in the app Home tab.";
    }

    @Override public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{" +
            "\"task_id\":{\"type\":\"string\"}," +
            "\"max_width\":{\"type\":\"integer\",\"default\":1080," +
                "\"description\":\"Scale image down to this width (pixels). 0 = no scaling.\"}," +
            "\"jpeg_quality\":{\"type\":\"integer\",\"default\":80," +
                "\"description\":\"JPEG compression quality, 1–100.\"}" +
            "}}";
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        if (!ScreenCaptureService.isRunning()) {
            return textContent("Screen capture permission not granted. " +
                "Please tap '授权截图' in the app's Home tab, then try again.");
        }

        int maxWidth    = Math.max(0, args.optInt("max_width", 1080));
        int jpegQuality = Math.min(100, Math.max(1, args.optInt("jpeg_quality", 80)));

        byte[] jpeg = ScreenCaptureService.getInstance().captureJpeg(maxWidth, jpegQuality);
        String b64  = Base64.encodeToString(jpeg, Base64.NO_WRAP);

        JSONArray content = new JSONArray();
        JSONObject img = new JSONObject();
        img.put("type",     "image");
        img.put("data",     b64);
        img.put("mimeType", "image/jpeg");
        content.put(img);
        return content.toString();
    }

    private static String textContent(String message) throws Exception {
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", message);
        return new JSONArray().put(item).toString();
    }
}
