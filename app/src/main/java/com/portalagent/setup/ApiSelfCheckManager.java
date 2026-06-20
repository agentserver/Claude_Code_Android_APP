package com.portalagent.setup;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.api.apis.BatteryStatusAPI;
import com.termux.api.apis.CameraInfoAPI;
import com.termux.api.apis.ClipboardAPI;
import com.termux.api.apis.SensorAPI;
import com.termux.api.apis.WifiAPI;
import com.termux.app.TermuxActivity;
import com.portalagent.activities.ApiToolsActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.shared.activity.ActivityUtils;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiSelfCheckManager {

    private final TermuxActivity mActivity;
    private View mContainer;
    private TextView mTextView;
    private ExecutorService mExecutor;
    private boolean mComplete;
    private String mPendingReport;
    private boolean mEnabled = true;

    public ApiSelfCheckManager(@NonNull TermuxActivity activity) {
        mActivity = activity;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void initViews() {
        mContainer = mActivity.findViewById(R.id.api_self_check_container);
        mTextView = mActivity.findViewById(R.id.api_self_check_text);

        if (mContainer != null) {
            mContainer.setVisibility(View.GONE);
            mContainer.setOnClickListener(v ->
                ActivityUtils.startActivity(mActivity, new Intent(mActivity, ApiToolsActivity.class)));
        }

        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadExecutor();
        }
    }

    public void start() {
        if (!mEnabled || mComplete) return;

        mComplete = true;

        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadExecutor();
        }

        mExecutor.execute(() -> {
            final String report = buildApiSelfCheckReport();
            mActivity.runOnUiThread(() -> {
                mPendingReport = report;
                tryPrintPending();
            });
        });
    }

    public void tryPrintPending() {
        if (!mEnabled || TextUtils.isEmpty(mPendingReport)) return;

        TerminalSession session = mActivity.getCurrentSession();
        if (session != null && session.isRunning()) {
            if (appendToTerminalView(session, formatReportForTerminal(mPendingReport))) {
                mPendingReport = null;
            }
        }
    }

    public void shutdown() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
    }

    private boolean appendToTerminalView(TerminalSession session, String text) {
        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null) return false;

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);

        TermuxTerminalSessionActivityClient client = mActivity.getTermuxTerminalSessionClient();
        if (client != null) {
            client.onTextChanged(session);
        }

        return true;
    }

    private String formatReportForTerminal(String report) {
        String normalized = report.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        builder.append("\r\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0 && !TextUtils.isEmpty(line)) {
                line = " - " + line;
            }
            builder.append("\r").append(line).append("\r\n");
        }
        return builder.toString();
    }

    private String buildApiSelfCheckReport() {
        StringBuilder report = new StringBuilder();
        report.append(mActivity.getString(R.string.api_self_check_title)).append("\n");
        report.append(mActivity.getString(R.string.api_self_check_item,
            mActivity.getString(R.string.api_tools_button_battery), checkBatteryStatus())).append("\n");
        report.append(mActivity.getString(R.string.api_self_check_item,
            mActivity.getString(R.string.api_tools_button_camera), checkCameraInfo())).append("\n");
        report.append(mActivity.getString(R.string.api_self_check_item,
            mActivity.getString(R.string.api_tools_button_sensor), checkSensorList())).append("\n");
        report.append(mActivity.getString(R.string.api_self_check_item,
            mActivity.getString(R.string.api_tools_button_wifi), checkWifiInfo())).append("\n");
        report.append(mActivity.getString(R.string.api_self_check_item,
            mActivity.getString(R.string.api_tools_button_clipboard), checkClipboard()));
        return report.toString();
    }

    private String checkBatteryStatus() {
        try {
            String json = BatteryStatusAPI.getBatteryStatusJson(mActivity);
            return TextUtils.isEmpty(json)
                ? mActivity.getString(R.string.api_self_check_status_empty)
                : mActivity.getString(R.string.api_self_check_status_ok);
        } catch (Throwable t) {
            return formatThrowableStatus(t);
        }
    }

    private String checkCameraInfo() {
        if (!isPermissionGranted(Manifest.permission.CAMERA)) {
            return mActivity.getString(R.string.api_self_check_status_permission, Manifest.permission.CAMERA);
        }

        try {
            String json = CameraInfoAPI.getCameraInfoJson(mActivity);
            JSONArray cameras = new JSONArray(json);
            return cameras.length() == 0
                ? mActivity.getString(R.string.api_self_check_status_empty)
                : mActivity.getString(R.string.api_self_check_status_ok);
        } catch (Throwable t) {
            return formatThrowableStatus(t);
        }
    }

    private String checkSensorList() {
        if (!isPermissionGranted(Manifest.permission.BODY_SENSORS)) {
            return mActivity.getString(R.string.api_self_check_status_permission, Manifest.permission.BODY_SENSORS);
        }

        try {
            String json = SensorAPI.getSensorListJson(mActivity);
            JSONObject obj = new JSONObject(json);
            JSONArray sensors = obj.optJSONArray("sensors");
            return (sensors == null || sensors.length() == 0)
                ? mActivity.getString(R.string.api_self_check_status_empty)
                : mActivity.getString(R.string.api_self_check_status_ok);
        } catch (Throwable t) {
            return formatThrowableStatus(t);
        }
    }

    private String checkWifiInfo() {
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return mActivity.getString(R.string.api_self_check_status_permission, Manifest.permission.ACCESS_FINE_LOCATION);
        }

        try {
            String json = WifiAPI.getWifiConnectionInfoJson(mActivity);
            String apiError = getApiErrorFromJson(json);
            if (apiError != null) {
                return mActivity.getString(R.string.api_self_check_status_note, apiError);
            }
            return mActivity.getString(R.string.api_self_check_status_ok);
        } catch (Throwable t) {
            return formatThrowableStatus(t);
        }
    }

    private String checkClipboard() {
        try {
            String text = ClipboardAPI.getClipboardText(mActivity);
            return TextUtils.isEmpty(text)
                ? mActivity.getString(R.string.api_self_check_status_empty)
                : mActivity.getString(R.string.api_self_check_status_ok);
        } catch (Throwable t) {
            return formatThrowableStatus(t);
        }
    }

    private boolean isPermissionGranted(String permission) {
        return ContextCompat.checkSelfPermission(mActivity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    private String getApiErrorFromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("API_ERROR")) {
                return obj.optString("API_ERROR");
            }
        } catch (JSONException e) {
            return e.getMessage();
        }
        return null;
    }

    private String formatThrowableStatus(Throwable t) {
        String message = t.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = t.toString();
        }
        return mActivity.getString(R.string.api_self_check_status_error, message);
    }
}
