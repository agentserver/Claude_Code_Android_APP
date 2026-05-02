package com.termux.app.mcp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Display;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Foreground Service that holds a persistent MediaProjection session.
 *
 * Lifecycle:
 *   1. TermuxActivity calls requestScreenCapturePermission() → system permission dialog
 *   2. onActivityResult passes resultCode+data to startWithPermission()
 *   3. Service stays alive; ScreenCaptureTool.call() calls captureJpeg() on demand
 *   4. Service is stopped via stopCapture() or when app is destroyed
 */
public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "android_mcp_screen_capture";
    private static final int    NOTIF_ID   = 9001;

    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_DATA        = "data";

    private static volatile ScreenCaptureService sInstance;

    private MediaProjection mProjection;
    private VirtualDisplay  mVirtualDisplay;
    private ImageReader     mImageReader;
    private int mWidth, mHeight, mDensity;

    // ── Static helpers called from outside ────────────────────────────────────

    public static boolean isRunning() { return sInstance != null; }

    public static ScreenCaptureService getInstance() { return sInstance; }

    public static void startWithPermission(Context context, int resultCode, Intent data) {
        Intent i = new Intent(context, ScreenCaptureService.class);
        i.putExtra(EXTRA_RESULT_CODE, resultCode);
        i.putExtra(EXTRA_DATA, data);
        ContextCompat.startForegroundService(context, i);
    }

    public static void stopCapture(Context context) {
        context.stopService(new Intent(context, ScreenCaptureService.class));
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());

        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        int resultCode   = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent extraData = intent.getParcelableExtra(EXTRA_DATA);
        if (extraData == null || resultCode == 0) { stopSelf(); return START_NOT_STICKY; }

        // Get real display dimensions
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display display   = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics m  = new DisplayMetrics();
        display.getRealMetrics(m);
        mWidth   = m.widthPixels;
        mHeight  = m.heightPixels;
        mDensity = m.densityDpi;

        // Create MediaProjection
        MediaProjectionManager mpm =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mProjection = mpm.getMediaProjection(resultCode, extraData);
        if (mProjection == null) { stopSelf(); return START_NOT_STICKY; }

        // ImageReader: RGBA_8888, 2 frames max
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);

        mVirtualDisplay = mProjection.createVirtualDisplay(
            "AndroidMcpScreen",
            mWidth, mHeight, mDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mImageReader.getSurface(),
            null, null
        );

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        if (mVirtualDisplay != null) { mVirtualDisplay.release(); mVirtualDisplay = null; }
        if (mProjection    != null) { mProjection.stop();        mProjection    = null; }
        if (mImageReader   != null) { mImageReader.close();      mImageReader   = null; }
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Screenshot capture (called on MCP background thread) ──────────────────

    /**
     * Captures the current screen and returns JPEG bytes.
     * Blocks the calling thread up to ~1 s waiting for a frame.
     */
    public byte[] captureJpeg(int maxWidth, int quality) throws Exception {
        if (mImageReader == null) throw new Exception("Screen capture service not ready");

        // VirtualDisplay renders asynchronously; give it a moment
        Thread.sleep(150);

        Image image = null;
        for (int i = 0; i < 20; i++) {
            image = mImageReader.acquireLatestImage();
            if (image != null) break;
            Thread.sleep(50);
        }
        if (image == null) throw new Exception("No screen frame available — try again shortly");

        try {
            Image.Plane plane      = image.getPlanes()[0];
            ByteBuffer  buffer     = plane.getBuffer();
            int pixelStride        = plane.getPixelStride();
            int rowStride          = plane.getRowStride();
            int rowPadding         = rowStride - pixelStride * mWidth;

            // Create bitmap, possibly wider than screen due to row padding
            Bitmap bmp = Bitmap.createBitmap(
                mWidth + rowPadding / pixelStride,
                mHeight,
                Bitmap.Config.ARGB_8888
            );
            bmp.copyPixelsFromBuffer(buffer);

            // Crop to actual screen width
            Bitmap cropped;
            if (rowPadding > 0) {
                cropped = Bitmap.createBitmap(bmp, 0, 0, mWidth, mHeight);
                bmp.recycle();
            } else {
                cropped = bmp;
            }

            // Scale down if requested
            if (maxWidth > 0 && cropped.getWidth() > maxWidth) {
                float scale = (float) maxWidth / cropped.getWidth();
                int newH    = Math.round(cropped.getHeight() * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(cropped, maxWidth, newH, true);
                cropped.recycle();
                cropped = scaled;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            cropped.recycle();
            return baos.toByteArray();

        } finally {
            image.close();
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "屏幕截图服务", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Android MCP 截图服务正在运行");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("屏幕截图已授权")
            .setContentText("Android MCP Server 可截取屏幕供 Claude 使用")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }
}
