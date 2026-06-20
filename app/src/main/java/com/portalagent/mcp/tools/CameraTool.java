package com.portalagent.mcp.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Base64;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.portalagent.mcp.McpTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * camera.take_photo — captures a photo using CameraX and returns it as base64 JPEG.
 *
 * Uses ProcessLifecycleOwner so no Activity is required; the camera is bound to the
 * application process lifecycle and released after each capture.
 */
public class CameraTool implements McpTool {

    private static final int CAMERA_TIMEOUT_SECONDS = 12;

    @Override public String getName() { return "camera.take_photo"; }

    @Override public String getDescription() {
        return "Capture a photo with the device camera. Returns the image as a base64-encoded JPEG.";
    }

    @Override public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{" +
            "\"task_id\":{\"type\":\"string\"}," +
            "\"camera\":{\"type\":\"string\",\"enum\":[\"back\",\"front\"],\"default\":\"back\"}," +
            "\"max_width\":{\"type\":\"integer\",\"default\":1080}," +
            "\"jpeg_quality\":{\"type\":\"integer\",\"default\":85}" +
            "}}";
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        String cameraStr   = args.optString("camera", "back");
        int maxWidth       = args.optInt("max_width", 1080);
        int jpegQuality    = Math.min(100, Math.max(1, args.optInt("jpeg_quality", 85)));

        int lensFacing = "front".equals(cameraStr)
            ? CameraSelector.LENS_FACING_FRONT
            : CameraSelector.LENS_FACING_BACK;
        CameraSelector cameraSelector = new CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build();

        // Get camera provider (blocking, 5 s timeout)
        ListenableFuture<ProcessCameraProvider> future =
            ProcessCameraProvider.getInstance(context);
        ProcessCameraProvider cameraProvider =
            future.get(5, TimeUnit.SECONDS);

        ImageCapture imageCapture = new ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build();

        // Bind on main thread (CameraX requirement)
        CountDownLatch bindLatch = new CountDownLatch(1);
        AtomicReference<Exception> bindError = new AtomicReference<>();
        ContextCompat.getMainExecutor(context).execute(() -> {
            try {
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                    ProcessLifecycleOwner.get(), cameraSelector, imageCapture);
            } catch (Exception e) {
                bindError.set(e);
            } finally {
                bindLatch.countDown();
            }
        });
        if (!bindLatch.await(5, TimeUnit.SECONDS)) {
            throw new Exception("Camera bind timeout");
        }
        if (bindError.get() != null) throw bindError.get();

        // Capture
        CountDownLatch captureLatch = new CountDownLatch(1);
        AtomicReference<byte[]> imageBytes = new AtomicReference<>();
        AtomicReference<Exception> captureError = new AtomicReference<>();

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            new ImageCapture.OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(ImageProxy image) {
                    try {
                        imageBytes.set(imageProxyToBytes(image));
                    } catch (Exception e) {
                        captureError.set(e);
                    } finally {
                        image.close();
                        captureLatch.countDown();
                    }
                }
                @Override
                public void onError(ImageCaptureException exc) {
                    captureError.set(exc);
                    captureLatch.countDown();
                }
            }
        );

        boolean captured = captureLatch.await(CAMERA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Unbind on main thread
        ContextCompat.getMainExecutor(context).execute(cameraProvider::unbindAll);

        if (!captured)               throw new Exception("Camera capture timeout");
        if (captureError.get() != null) throw captureError.get();

        byte[] raw = imageBytes.get();
        if (raw == null)             throw new Exception("No image data returned");

        // Decode, resize, re-encode as JPEG
        byte[] jpeg = compressJpeg(raw, maxWidth, jpegQuality);
        String b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);

        JSONArray content = new JSONArray();
        JSONObject img = new JSONObject();
        img.put("type", "image");
        img.put("data", b64);
        img.put("mimeType", "image/jpeg");
        content.put(img);
        return content.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static byte[] imageProxyToBytes(ImageProxy image) {
        // JPEG output from ImageCapture is in plane[0]
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static byte[] compressJpeg(byte[] raw, int maxWidth, int quality) {
        Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        if (bmp == null) return raw;

        if (bmp.getWidth() > maxWidth) {
            float scale = (float) maxWidth / bmp.getWidth();
            int newH = Math.round(bmp.getHeight() * scale);
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            Bitmap scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            bmp.recycle();
            bmp = scaled;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        bmp.recycle();
        return baos.toByteArray();
    }
}
