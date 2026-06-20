package com.portalagent.activities;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.portalagent.apitools.ApiToolsController;

public class ApiToolsActivity extends AppCompatActivity implements ApiToolsController.Host {

    private ApiToolsController mController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_api_tools);

        applySettingsChrome();

        View backButton = findViewById(R.id.api_tools_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackPressed());
        }

        mController = new ApiToolsController(this);
        mController.bind(findViewById(android.R.id.content));
    }

    private void applySettingsChrome() {
        Window window = getWindow();
        int backgroundColor = ContextCompat.getColor(this, R.color.app_bg_secondary);

        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(backgroundColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onDestroy() {
        if (mController != null) {
            mController.destroy();
            mController = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @NonNull
    @Override
    public Context getApiToolsContext() {
        return this;
    }

    @Override
    public void requestApiToolsPermissions(@NonNull String[] permissions, int requestCode) {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mController != null) {
            mController.onRequestPermissionsResult(requestCode, grantResults);
        }
    }
}
