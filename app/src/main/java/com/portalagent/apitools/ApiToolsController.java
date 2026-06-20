package com.portalagent.apitools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.api.apis.BatteryStatusAPI;
import com.termux.api.apis.CameraInfoAPI;
import com.termux.api.apis.ClipboardAPI;
import com.termux.api.apis.SensorAPI;
import com.termux.api.apis.WifiAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiToolsController {

    public static final int REQUEST_PERMISSIONS = 2001;

    public interface Host {
        @NonNull
        Context getApiToolsContext();

        void requestApiToolsPermissions(@NonNull String[] permissions, int requestCode);
    }

    private final Host mHost;
    private final List<Button> mCommandButtons = new ArrayList<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private TextView mStatusView;
    private TextView mOutputView;
    private ApiCommand mPendingCommand;

    public ApiToolsController(@NonNull Host host) {
        mHost = host;
    }

    public void bind(@NonNull View view) {
        mStatusView = view.findViewById(R.id.api_tools_status);
        mOutputView = view.findViewById(R.id.api_tools_output);

        setCommandButton(view.findViewById(R.id.button_api_battery), ApiCommand.BATTERY_STATUS);
        setCommandButton(view.findViewById(R.id.button_api_camera), ApiCommand.CAMERA_INFO);
        setCommandButton(view.findViewById(R.id.button_api_sensor), ApiCommand.SENSOR_LIST);
        setCommandButton(view.findViewById(R.id.button_api_wifi), ApiCommand.WIFI_CONNECTION_INFO);
        setCommandButton(view.findViewById(R.id.button_api_clipboard), ApiCommand.CLIPBOARD_GET);

        Button clearButton = view.findViewById(R.id.button_api_clear);
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                if (mOutputView != null) mOutputView.setText("");
            });
        }

        setStatus(context().getString(R.string.api_tools_status_ready));
    }

    public void destroy() {
        mExecutor.shutdownNow();
    }

    public boolean onRequestPermissionsResult(int requestCode,
                                              @NonNull int[] grantResults) {
        if (requestCode != REQUEST_PERMISSIONS) return false;

        boolean granted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        ApiCommand pending = mPendingCommand;
        mPendingCommand = null;

        if (granted && pending != null) {
            runCommand(pending);
        } else {
            setStatus(context().getString(R.string.api_tools_status_permission_denied));
            setButtonsEnabled(true);
        }
        return true;
    }

    private void setCommandButton(Button button, ApiCommand command) {
        if (button == null) return;
        mCommandButtons.add(button);
        button.setOnClickListener(v -> runCommand(command));
    }

    private void runCommand(ApiCommand command) {
        if (!ensurePermissions(command)) return;

        setButtonsEnabled(false);
        Context context = context();
        String label = context.getString(command.labelResId);
        setStatus(context.getString(R.string.api_tools_status_running, label));

        mExecutor.execute(() -> {
            String output;
            try {
                output = command.run(context);
                if (TextUtils.isEmpty(output)) {
                    output = context.getString(R.string.api_tools_output_empty);
                }
            } catch (Throwable t) {
                String message = t.getMessage();
                if (TextUtils.isEmpty(message)) {
                    message = t.toString();
                }
                output = context.getString(R.string.api_tools_output_error, message);
            }

            String finalOutput = output;
            TextView outputView = mOutputView;
            if (outputView == null) return;
            outputView.post(() -> {
                outputView.setText(finalOutput);
                setStatus(context().getString(R.string.api_tools_status_ready));
                setButtonsEnabled(true);
            });
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button button : mCommandButtons) {
            button.setEnabled(enabled);
            button.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void setStatus(String status) {
        if (mStatusView != null) {
            mStatusView.setText(status);
        }
    }

    private boolean ensurePermissions(ApiCommand command) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        String[] required = command.permissions;
        if (required.length == 0) return true;

        List<String> missing = new ArrayList<>();
        Context context = context();
        for (String permission : required) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (missing.isEmpty()) return true;

        mPendingCommand = command;
        mHost.requestApiToolsPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        setStatus(context.getString(R.string.api_tools_status_permission,
            context.getString(command.labelResId)));
        return false;
    }

    @NonNull
    private Context context() {
        return mHost.getApiToolsContext();
    }

    private interface CommandRunner {
        String run(Context context) throws Exception;
    }

    private enum ApiCommand {
        BATTERY_STATUS(R.string.api_tools_button_battery, new String[]{}, BatteryStatusAPI::getBatteryStatusJson),
        CAMERA_INFO(R.string.api_tools_button_camera, new String[]{Manifest.permission.CAMERA}, CameraInfoAPI::getCameraInfoJson),
        SENSOR_LIST(R.string.api_tools_button_sensor, new String[]{Manifest.permission.BODY_SENSORS}, SensorAPI::getSensorListJson),
        WIFI_CONNECTION_INFO(R.string.api_tools_button_wifi, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, WifiAPI::getWifiConnectionInfoJson),
        CLIPBOARD_GET(R.string.api_tools_button_clipboard, new String[]{}, ClipboardAPI::getClipboardText);

        private final int labelResId;
        private final String[] permissions;
        private final CommandRunner runner;

        ApiCommand(int labelResId, String[] permissions, CommandRunner runner) {
            this.labelResId = labelResId;
            this.permissions = permissions;
            this.runner = runner;
        }

        public String run(Context context) throws Exception {
            return runner.run(context);
        }
    }
}
