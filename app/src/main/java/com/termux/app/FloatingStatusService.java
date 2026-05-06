package com.termux.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.termux.R;

/**
 * 后台悬浮窗：App 切到后台时显示 Claude 当前状态和最后一条消息预览。
 * 通过 static updateStatus() 从任意线程更新内容。
 */
public class FloatingStatusService extends Service {

    public static final String ACTION_SHOW = "SHOW";
    public static final String ACTION_HIDE = "HIDE";

    private static FloatingStatusService sInstance;

    private WindowManager mWindowManager;
    private View          mFloatingView;
    private TextView      mStatusText;
    private TextView      mPreviewText;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // 最新状态缓存（Service 可能在 SHOW 前就收到更新）
    private static volatile String sPendingStatus  = "● 等待中";
    private static volatile String sPendingPreview = "";
    private static volatile int    sPendingColor   = 0xFF888888;

    // ── 静态接口（供 HomeFragment 跨线程调用） ─────────────────────────────

    /** 更新悬浮窗显示的状态和消息预览；线程安全。 */
    public static void updateStatus(String status, int color, String preview) {
        sPendingStatus  = status;
        sPendingColor   = color;
        sPendingPreview = preview != null ? preview : "";
        FloatingStatusService inst = sInstance;
        if (inst != null) {
            inst.mHandler.post(inst::applyPending);
        }
    }

    // ── Service 生命周期 ────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mFloatingView  = LayoutInflater.from(this).inflate(R.layout.layout_floating_status, null);
        mStatusText    = mFloatingView.findViewById(R.id.float_status);
        mPreviewText   = mFloatingView.findViewById(R.id.float_preview);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 16;
        params.y = 80;

        mFloatingView.setVisibility(View.GONE);
        mWindowManager.addView(mFloatingView, params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_SHOW.equals(action)) {
            applyPending();
            mFloatingView.setVisibility(View.VISIBLE);
        } else if (ACTION_HIDE.equals(action)) {
            mFloatingView.setVisibility(View.GONE);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null && mWindowManager != null) {
            mWindowManager.removeView(mFloatingView);
        }
        sInstance = null;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── 内部 ────────────────────────────────────────────────────────────────

    private void applyPending() {
        if (mStatusText == null) return;
        mStatusText.setText(sPendingStatus);
        mStatusText.setTextColor(sPendingColor);
        String preview = sPendingPreview;
        if (preview.isEmpty()) {
            mPreviewText.setVisibility(View.GONE);
        } else {
            mPreviewText.setText(preview.length() > 40 ? preview.substring(0, 40) + "…" : preview);
            mPreviewText.setVisibility(View.VISIBLE);
        }
    }
}
