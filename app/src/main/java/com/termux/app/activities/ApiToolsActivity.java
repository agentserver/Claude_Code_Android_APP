package com.termux.app.activities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.termux.R;
import com.termux.api.apis.BatteryStatusAPI;
import com.termux.api.apis.CameraInfoAPI;
import com.termux.api.apis.ClipboardAPI;
import com.termux.api.apis.SensorAPI;
import com.termux.api.apis.WifiAPI;
import com.termux.shared.activity.media.AppCompatActivityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiToolsActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 2001;

    private final List<Button> mCommandButtons = new ArrayList<>();
    private ExecutorService mExecutor;
    private TextView mStatusView;
    private TextView mOutputView;
    private ApiCommand mPendingCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_api_tools);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        applySettingsChrome();

        mExecutor = Executors.newSingleThreadExecutor();
        mStatusView = findViewById(R.id.api_tools_status);
        mOutputView = findViewById(R.id.api_tools_output);

        Button batteryButton = findViewById(R.id.button_api_battery);
        setCommandButton(batteryButton, ApiCommand.BATTERY_STATUS);

        Button cameraButton = findViewById(R.id.button_api_camera);
        setCommandButton(cameraButton, ApiCommand.CAMERA_INFO);

        Button sensorButton = findViewById(R.id.button_api_sensor);
        setCommandButton(sensorButton, ApiCommand.SENSOR_LIST);

        Button wifiButton = findViewById(R.id.button_api_wifi);
        setCommandButton(wifiButton, ApiCommand.WIFI_CONNECTION_INFO);

        Button clipboardButton = findViewById(R.id.button_api_clipboard);
        setCommandButton(clipboardButton, ApiCommand.CLIPBOARD_GET);

        Button clearButton = findViewById(R.id.button_api_clear);
        clearButton.setOnClickListener(v -> mOutputView.setText(""));

        setStatus(getString(R.string.api_tools_status_ready));
    }

    private void applySettingsChrome() {
        Window window = getWindow();
        int backgroundColor = ContextCompat.getColor(this, R.color.app_bg_secondary);
        int surfaceColor = ContextCompat.getColor(this, R.color.app_card_bg);
        int primaryColor = ContextCompat.getColor(this, R.color.app_primary);
        int textColor = ContextCompat.getColor(this, R.color.app_text_primary);

        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(surfaceColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }

        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(surfaceColor);
            toolbar.setTitleTextColor(textColor);
            toolbar.setSubtitleTextColor(ContextCompat.getColor(this, R.color.app_text_secondary));
            Drawable navigationIcon = toolbar.getNavigationIcon();
            if (navigationIcon != null) {
                Drawable wrappedIcon = DrawableCompat.wrap(navigationIcon).mutate();
                DrawableCompat.setTint(wrappedIcon, primaryColor);
                toolbar.setNavigationIcon(wrappedIcon);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setCommandButton(Button button, ApiCommand command) {
        mCommandButtons.add(button);
        button.setOnClickListener(v -> runCommand(command));
    }

    private void runCommand(ApiCommand command) {
        if (!ensurePermissions(command)) return;

        setButtonsEnabled(false);
        String label = getString(command.labelResId);
        setStatus(getString(R.string.api_tools_status_running, label));

        mExecutor.execute(() -> {
            String output;
            try {
                output = command.run(this);
                if (TextUtils.isEmpty(output)) {
                    output = getString(R.string.api_tools_output_empty);
                }
            } catch (Throwable t) {
                String message = t.getMessage();
                if (TextUtils.isEmpty(message)) {
                    message = t.toString();
                }
                output = getString(R.string.api_tools_output_error, message);
            }

            String finalOutput = output;
            runOnUiThread(() -> {
                mOutputView.setText(finalOutput);
                setStatus(getString(R.string.api_tools_status_ready));
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
        for (String permission : required) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (missing.isEmpty()) return true;

        mPendingCommand = command;
        ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        setStatus(getString(R.string.api_tools_status_permission, getString(command.labelResId)));
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_PERMISSIONS) return;

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
            setStatus(getString(R.string.api_tools_status_permission_denied));
            setButtonsEnabled(true);
        }
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
