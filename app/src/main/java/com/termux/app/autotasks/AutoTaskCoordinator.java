package com.termux.app.autotasks;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;

public class AutoTaskCoordinator {

    private final ApiSelfCheckManager mApiSelfCheckManager;
    private final AutoUbuntuManager mAutoUbuntuManager;
    private final ApiHttpBridgeServer mApiHttpBridgeServer;
    @SuppressWarnings("FieldCanBeLocal")
    private final AutoClaudeManager mAutoClaudeManager;
    private boolean mEnabled = true;

    public AutoTaskCoordinator(@NonNull TermuxActivity activity) {
        // AutoClaudeManager 先初始化：后台写 inner 脚本，Ubuntu 安装需要几分钟，有充足准备时间
        mAutoClaudeManager = new AutoClaudeManager(activity);
        mApiSelfCheckManager = new ApiSelfCheckManager(activity);
        mAutoUbuntuManager = new AutoUbuntuManager(activity);
        // HTTP API 桥：Android API → localhost HTTP，供 Ubuntu 内 Claude Code 通过 curl 实时调用
        mApiHttpBridgeServer = new ApiHttpBridgeServer(activity);
        mApiHttpBridgeServer.start();
        // 后台生成 capabilities.json，供 Ubuntu 里的 Claude Code 读取设备能力快照
        new CapabilitiesManager(activity).generateAsync();
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        mApiSelfCheckManager.setEnabled(enabled);
        mAutoUbuntuManager.setEnabled(enabled);
    }

    public void setApiSelfCheckEnabled(boolean enabled) {
        mApiSelfCheckManager.setEnabled(enabled);
    }

    public void setAutoUbuntuEnabled(boolean enabled) {
        mAutoUbuntuManager.setEnabled(enabled);
    }

    public void init() {
        if (!mEnabled) return;
        mApiSelfCheckManager.initViews();
    }

    public void onStart() {
        if (!mEnabled) return;
        mApiSelfCheckManager.start();
    }

    public void onResume() {
        if (!mEnabled) return;
        mAutoUbuntuManager.maybeAutoLaunchUbuntu();
    }

    public void onSessionReady() {
        if (!mEnabled) return;
        mApiSelfCheckManager.tryPrintPending();
        mAutoUbuntuManager.maybeAutoLaunchUbuntu();
    }

    public void onDestroy() {
        mApiSelfCheckManager.shutdown();
        mApiHttpBridgeServer.stop();
    }
}
