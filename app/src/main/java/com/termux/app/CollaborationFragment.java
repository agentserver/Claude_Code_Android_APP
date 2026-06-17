package com.termux.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.collab.CollaborationConnectionState;
import com.termux.app.loom.LoomCommandBuilder;
import com.termux.app.loom.LoomDriverConfigIdentity;
import com.termux.app.loom.LoomSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AgentServer 与 Loom 的协作控制台。
 *
 * 阶段一只做入口重组和状态摘要，具体运行时操作继续复用现有详情页。
 */
public class CollaborationFragment extends Fragment {

    private static final String AGENTSERVER_PREFS_NAME = "agentserver_config";
    private static final String KEY_AGENTSERVER_URL = "server_url";
    private static final String KEY_AGENTSERVER_DEVICE_NAME = "device_name";
    private static final String KEY_AGENTSERVER_SANDBOX_CODE = "sandbox_code";
    private static final String KEY_AGENTSERVER_SANDBOX_ID = "sandbox_id";
    private static final String KEY_AGENTSERVER_WORKSPACE_ID = "workspace_id";
    private static final String KEY_AGENTSERVER_SHORT_ID = "short_id";
    private static final String KEY_DRIVER_BINDING_FINGERPRINT = "driver_binding_fingerprint";
    private static final String KEY_DRIVER_BINDING_STATUS = "driver_binding_status";
    private static final String KEY_DRIVER_BINDING_PROVIDER = "driver_binding_provider";
    private static final String KEY_DRIVER_BINDING_SANDBOX_ID = "driver_binding_sandbox_id";
    private static final String KEY_DRIVER_BINDING_SERVER_URL = "driver_binding_server_url";
    private static final String KEY_DRIVER_BINDING_DEVICE_NAME = "driver_binding_device_name";
    private static final String KEY_DRIVER_BINDING_DRIVER_NAME = "driver_binding_driver_name";
    private static final String[] ROLE_LABELS = {
        "Observer", "Slave"
    };
    private static final String[] ROLE_VALUES = {
        "observer", "slave"
    };

    private TextView mProviderText;
    private TextView mProviderSwitchButton;
    private View mDriverBindingDot;
    private TextView mDriverBindingStatus;
    private TextView mStartRoleButton;
    private TextView mStopRoleButton;
    private TextView mWorkspaceSummary;
    private TextView mLoomSummary;
    private TextView mAndroidCapabilitiesSummary;
    private TextView mLocalAgentStatus;
    private TextView mLocalSlaveStatus;
    private Thread mDriverBindingThread;
    private Thread mLoomRuntimeThread;
    private Process mLoomRuntimeProcess;
    private AlertDialog mAuthDialog;
    private boolean mAuthDialogShown;

    private static final Pattern AUTH_URL_PATTERN =
        Pattern.compile("https?://[\\w.-]+(?:/[\\w./?=&%+-]*)?");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collaboration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mProviderText = view.findViewById(R.id.collaboration_provider_text);
        mProviderSwitchButton = view.findViewById(R.id.btn_collaboration_switch_provider);
        mDriverBindingDot = view.findViewById(R.id.collaboration_driver_binding_dot);
        mDriverBindingStatus = view.findViewById(R.id.collaboration_driver_binding_status);
        mStartRoleButton = view.findViewById(R.id.btn_collaboration_start_role);
        mStopRoleButton = view.findViewById(R.id.btn_collaboration_stop_role);
        mWorkspaceSummary = view.findViewById(R.id.collaboration_workspace_summary);
        mLoomSummary = view.findViewById(R.id.collaboration_loom_summary);
        mAndroidCapabilitiesSummary = view.findViewById(R.id.collaboration_android_capabilities_summary);
        mLocalAgentStatus = view.findViewById(R.id.collaboration_local_agent_status);
        mLocalSlaveStatus = view.findViewById(R.id.collaboration_local_slave_status);

        view.findViewById(R.id.btn_collaboration_switch_provider)
            .setOnClickListener(v -> showProviderDialog());
        view.findViewById(R.id.btn_collaboration_bind_driver)
            .setOnClickListener(v -> bindDriverToCurrentAgent(
                new ProviderSettingsStore(requireContext()).getSelectedProvider()));
        view.findViewById(R.id.btn_collaboration_switch_role)
            .setOnClickListener(v -> showRoleDialog());
        view.findViewById(R.id.btn_collaboration_start_role)
            .setOnClickListener(v -> startSelectedRole());
        view.findViewById(R.id.btn_collaboration_stop_role)
            .setOnClickListener(v -> stopSelectedRole());

        View.OnClickListener agentServerClick = v -> {
            TermuxActivity a = act();
            if (a != null) a.showAgentServerMode();
        };
        view.findViewById(R.id.btn_collaboration_agentserver_optional).setOnClickListener(agentServerClick);

        View.OnClickListener loomClick = v -> {
            TermuxActivity a = act();
            if (a != null) a.showLoomMode();
        };
        view.findViewById(R.id.collaboration_loom_summary).setOnClickListener(loomClick);

        refreshDashboard();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDashboard();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDriverBindingThread != null && mDriverBindingThread.isAlive()) {
            mDriverBindingThread.interrupt();
        }
        if (mLoomRuntimeProcess != null) {
            mLoomRuntimeProcess.destroy();
            mLoomRuntimeProcess = null;
        }
        if (mLoomRuntimeThread != null && mLoomRuntimeThread.isAlive()) {
            mLoomRuntimeThread.interrupt();
        }
        dismissAuthDialog();
    }

    private void refreshDashboard() {
        if (getContext() == null) return;

        ProviderProfile profile = ProviderProfile.forProvider(
            new ProviderSettingsStore(requireContext()).getSelectedProvider());
        if (mProviderText != null) {
            mProviderText.setText("当前助手：" + profile.displayName);
        }
        if (mProviderSwitchButton != null) {
            mProviderSwitchButton.setText("切换 Agent");
        }
        if (mDriverBindingStatus != null
                && (mDriverBindingThread == null || !mDriverBindingThread.isAlive())) {
            String status = currentDriverBindingStatus(profile.provider);
            mDriverBindingStatus.setText(driverBindingStatusText(status));
            updateDriverBindingDot(status);
        }

        SharedPreferences agentPrefs = requireContext()
            .getSharedPreferences(AGENTSERVER_PREFS_NAME, Context.MODE_PRIVATE);
        String configuredServerUrl = trim(agentPrefs.getString(KEY_AGENTSERVER_URL, ""));
        String serverUrl = firstNonEmpty(configuredServerUrl, LoomSettings.defaults().agentServerUrl);
        String deviceName = trim(agentPrefs.getString(KEY_AGENTSERVER_DEVICE_NAME, ""));
        String workspaceId = currentWorkspaceId();

        if (mWorkspaceSummary != null) {
            String serverLine = serverUrl.isEmpty() ? "服务器：未配置" : "服务器：" + serverUrl;
            String workspaceLine = workspaceId.isEmpty()
                ? "工作区：绑定后自动同步"
                : "工作区：" + shortId(workspaceId);
            String deviceLine = "本机：" + (deviceName.isEmpty() ? "本机设备" : deviceName);
            mWorkspaceSummary.setText(serverLine + "\n" + workspaceLine + "\n" + deviceLine);
        }

        SharedPreferences loomPrefs = requireContext()
            .getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE);
        LoomSettings defaults = LoomSettings.defaults();
        String role = trim(loomPrefs.getString(LoomSettings.KEY_ROLE_MODE, defaults.roleMode));
        String observerUrl = trim(loomPrefs.getString(LoomSettings.KEY_OBSERVER_URL, defaults.observerUrl));
        updateRoleActionButtons(role);

        if (mLoomSummary != null) {
            mLoomSummary.setText("Loom：" + roleLabel(role)
                + "\nObserver：" + observerUrl
                + "\nDriver：随 " + profile.displayName + " 配置");
        }
        if (mAndroidCapabilitiesSummary != null) {
            mAndroidCapabilitiesSummary.setText(
                "无障碍 / 截图 / ADB / Android MCP 状态在设置与自动化页管理。");
        }
        if (mLocalAgentStatus != null) {
            String name = deviceName.isEmpty() ? "本机 Agent" : deviceName;
            mLocalAgentStatus.setText(name + " · " + profile.displayName);
        }
        if (mLocalSlaveStatus != null) {
            if (mLoomRuntimeThread == null || !mLoomRuntimeThread.isAlive()) {
                mLocalSlaveStatus.setText("本机身份 · " + roleLabel(role));
            }
        }
    }

    private void showProviderDialog() {
        if (getContext() == null) return;
        AssistantProvider current = new ProviderSettingsStore(requireContext()).getSelectedProvider();
        String[] labels = {"Codex", "Claude Code"};
        int checked = current == AssistantProvider.CLAUDE ? 1 : 0;
        new AlertDialog.Builder(requireContext())
            .setTitle("切换当前助手")
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                AssistantProvider next = which == 1
                    ? AssistantProvider.CLAUDE
                    : AssistantProvider.CODEX;
                dialog.dismiss();
                switchProviderAndMarkDriverStale(next);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showRoleDialog() {
        if (getContext() == null) return;
        String current = currentRoleMode();
        new AlertDialog.Builder(requireContext())
            .setTitle("切换本机身份")
            .setSingleChoiceItems(ROLE_LABELS, indexOfRole(current), (dialog, which) -> {
                dialog.dismiss();
                switchRoleOnly(ROLE_VALUES[which]);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void switchRoleOnly(String role) {
        if (getContext() == null) return;
        String safeRole = normalizeRole(role);
        String current = currentRoleMode();
        if (safeRole.equals(current)) return;
        requireContext().getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LoomSettings.KEY_ROLE_MODE, safeRole)
            .apply();
        refreshDashboard();
    }

    private void switchProviderAndMarkDriverStale(AssistantProvider provider) {
        if (getContext() == null) return;
        AssistantProvider safe = provider == null ? AssistantProvider.CODEX : provider;
        AssistantProvider current = new ProviderSettingsStore(requireContext()).getSelectedProvider();
        new ProviderSettingsStore(requireContext()).setSelectedProvider(safe);
        if (current != safe) {
            markDriverBindingStale();
            Toast.makeText(getContext(), "已切换 Agent，请按需重新绑定 Driver", Toast.LENGTH_SHORT).show();
        }
        refreshDashboard();
    }

    private void startSelectedRole() {
        if (getContext() == null) return;
        if (isLoomRuntimeBusy()) {
            Toast.makeText(getContext(), "角色操作正在执行", Toast.LENGTH_SHORT).show();
            return;
        }

        AssistantProvider provider = new ProviderSettingsStore(requireContext()).getSelectedProvider();
        LoomSettings settings = currentLoomSettings()
            .withRoleMode(currentRoleMode())
            .withAgentProvider(provider);
        String role = normalizeRole(settings.roleMode);
        mAuthDialogShown = false;

        if ("observer".equals(role)) {
            String script = transitionScript(settings, "observer")
                + LoomCommandBuilder.setupConfigScript(settings)
                + "\n" + LoomCommandBuilder.startObserverScript(prefix(), settings);
            rememberRuntimeProvider(settings);
            runLoomRuntimeScript(script, "启动 Observer", false);
            return;
        }

        String driverStatus = currentDriverBindingStatus(provider);
        if (!CollaborationConnectionState.canStartRole("slave", driverStatus)) {
            setLocalRuntimeStatus("本机身份 · Slave（Driver 尚未绑定）");
            Toast.makeText(getContext(), "Driver 尚未绑定，请先点击“绑定 Driver”。", Toast.LENGTH_SHORT).show();
            return;
        }

        String script = transitionScript(settings, "slave")
            + LoomCommandBuilder.setupConfigScript(settings)
            + "\n" + LoomCommandBuilder.startSlaveScript(prefix(), settings);
        rememberRuntimeProvider(settings);
        runLoomRuntimeScript(script, "启动 Slave", false);
    }

    private void stopSelectedRole() {
        if (getContext() == null) return;
        if (isLoomRuntimeBusy()) {
            Toast.makeText(getContext(), "角色操作正在执行", Toast.LENGTH_SHORT).show();
            return;
        }

        String role = currentRoleMode();
        LoomSettings settings = runtimeSettings();
        if ("observer".equals(role)) {
            runLoomRuntimeScript(LoomCommandBuilder.stopObserverScript(settings), "停止 Observer", false);
            return;
        }
        runLoomRuntimeScript(LoomCommandBuilder.stopSlaveScript(settings), "停止 Slave", false);
    }

    private void bindDriverToCurrentAgent(AssistantProvider provider) {
        if (getContext() == null) return;
        if (mDriverBindingThread != null && mDriverBindingThread.isAlive()) {
            Toast.makeText(getContext(), "Driver 正在绑定中", Toast.LENGTH_SHORT).show();
            return;
        }

        AssistantProvider safe = provider == null ? AssistantProvider.CODEX : provider;
        ProviderProfile profile = ProviderProfile.forProvider(safe);
        LoomSettings settings = currentLoomSettings().withAgentProvider(safe);
        String serverUrl = settings.agentServerUrl;
        String workspaceId = currentWorkspaceId();
        if (!CollaborationConnectionState.canBindDriver(serverUrl, workspaceId)) {
            setDriverBindingStatus("Driver：请先配置协作服务器");
            Toast.makeText(getContext(), "请先配置协作服务器地址。", Toast.LENGTH_SHORT).show();
            return;
        }

        String status = currentDriverBindingStatus(safe);
        if (CollaborationConnectionState.DRIVER_STATUS_VALID.equals(status)) {
            setDriverBindingStatus("Driver：已绑定");
            Toast.makeText(getContext(), "Driver 已绑定，无需重复扫码。", Toast.LENGTH_SHORT).show();
            return;
        }

        String script = LoomCommandBuilder.setupConfigScript(settings)
            + "\n" + LoomCommandBuilder.bindDriverIfNeededScript(settings)
            + "\n" + LoomCommandBuilder.readDriverConfigScript(settings);
        mAuthDialogShown = false;
        setDriverBindingStatus("Driver：绑定中");
        updateDriverBindingDot(CollaborationConnectionState.DRIVER_STATUS_BINDING);
        setDriverBindingStatusPref(CollaborationConnectionState.DRIVER_STATUS_BINDING, "");

        mDriverBindingThread = new Thread(() -> runDriverBindingScript(script, profile),
            "loom-driver-bind-" + safe.id);
        mDriverBindingThread.setDaemon(true);
        mDriverBindingThread.start();
    }

    private void runDriverBindingScript(String script, ProviderProfile profile) {
        String prefix = System.getenv("PREFIX");
        if (prefix == null || prefix.isEmpty()) prefix = "/data/data/com.termux/files/usr";
        String bash = prefix + "/bin/bash";
        String sysPath = System.getenv("PATH");
        if (sysPath == null) sysPath = "";
        String termuxPath = prefix + "/bin:" + prefix + "/bin/applets:" + sysPath;
        try {
            ProcessBuilder pb = new ProcessBuilder(bash, "-c", script);
            pb.redirectErrorStream(true);
            java.util.Map<String, String> env = pb.environment();
            env.putAll(System.getenv());
            env.put("PATH", termuxPath);
            env.put("PREFIX", prefix);
            env.put("HOME", prefix + "/../home");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            boolean readingDriverConfig = false;
            StringBuilder driverConfig = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroy();
                    return;
                }
                if (LoomCommandBuilder.DRIVER_CONFIG_BEGIN_MARKER.equals(line.trim())) {
                    readingDriverConfig = true;
                    continue;
                }
                if (LoomCommandBuilder.DRIVER_CONFIG_END_MARKER.equals(line.trim())) {
                    readingDriverConfig = false;
                    continue;
                }
                if (readingDriverConfig) {
                    driverConfig.append(line).append('\n');
                    continue;
                }
                maybeShowAuthUrl(line);
            }
            int exit = process.waitFor();
            LoomDriverConfigIdentity identity = exit == 0
                ? LoomDriverConfigIdentity.parse(driverConfig.toString())
                : LoomDriverConfigIdentity.empty();
            post(() -> {
                dismissAuthDialog();
                if (exit == 0 && identity.hasRemoteIdentity()) {
                    saveDriverBindingSuccess(profile.provider, identity);
                    updateDriverBindingDot(CollaborationConnectionState.DRIVER_STATUS_VALID);
                    setDriverBindingStatus("Driver：已绑定到 " + profile.displayName);
                    refreshDashboard();
                } else if (exit == 0) {
                    setDriverBindingStatusPref(CollaborationConnectionState.DRIVER_STATUS_FAILED, "");
                    updateDriverBindingDot(CollaborationConnectionState.DRIVER_STATUS_FAILED);
                    setDriverBindingStatus("Driver：绑定未写入身份");
                } else {
                    setDriverBindingStatusPref(CollaborationConnectionState.DRIVER_STATUS_FAILED, "");
                    updateDriverBindingDot(CollaborationConnectionState.DRIVER_STATUS_FAILED);
                    setDriverBindingStatus("Driver：绑定失败");
                }
            });
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            post(() -> {
                dismissAuthDialog();
                setDriverBindingStatusPref(CollaborationConnectionState.DRIVER_STATUS_FAILED, "");
                updateDriverBindingDot(CollaborationConnectionState.DRIVER_STATUS_FAILED);
                setDriverBindingStatus("Driver：绑定失败");
            });
        }
    }

    private void runLoomRuntimeScript(String script, String label, boolean watchAuthUrl) {
        String role = roleLabel(currentRoleMode());
        setLocalRuntimeStatus("本机身份 · " + role + "（" + label + "中）");

        mLoomRuntimeThread = new Thread(() -> {
            Process process = null;
            String prefix = prefix();
            String bash = prefix + "/bin/bash";
            String sysPath = System.getenv("PATH");
            if (sysPath == null) sysPath = "";
            String termuxPath = prefix + "/bin:" + prefix + "/bin/applets:" + sysPath;
            try {
                ProcessBuilder pb = new ProcessBuilder(bash, "-c", script);
                pb.redirectErrorStream(true);
                java.util.Map<String, String> env = pb.environment();
                env.putAll(System.getenv());
                env.put("PATH", termuxPath);
                env.put("PREFIX", prefix);
                env.put("HOME", prefix + "/../home");
                process = pb.start();
                mLoomRuntimeProcess = process;

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        process.destroy();
                        return;
                    }
                    if (watchAuthUrl) maybeShowAuthUrl(line);
                }
                int exit = process.waitFor();
                post(() -> setLocalRuntimeStatus("本机身份 · " + role + "（"
                    + label + (exit == 0 ? "完成" : "失败") + "）"));
            } catch (InterruptedException ignored) {
                if (process != null) process.destroy();
            } catch (Exception e) {
                post(() -> setLocalRuntimeStatus("本机身份 · " + role + "（" + label + "失败）"));
            } finally {
                if (mLoomRuntimeProcess == process) {
                    mLoomRuntimeProcess = null;
                }
            }
        }, "loom-runtime-" + normalizeRole(currentRoleMode()));
        mLoomRuntimeThread.setDaemon(true);
        mLoomRuntimeThread.start();
    }

    private LoomSettings currentLoomSettings() {
        LoomSettings d = LoomSettings.defaults();
        SharedPreferences p = requireContext()
            .getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE);
        LoomSettings settings = d.withRoleMode(normalizeRole(prefOrDefault(p, LoomSettings.KEY_ROLE_MODE, d.roleMode)))
            .withObserverUrl(prefOrDefault(p, LoomSettings.KEY_OBSERVER_URL, d.observerUrl))
            .withWorkspaceId(prefOrDefault(p, LoomSettings.KEY_WORKSPACE_ID, d.workspaceId))
            .withWorkspaceApiKey(prefOrDefault(p, LoomSettings.KEY_WORKSPACE_API_KEY, d.workspaceApiKey))
            .withAgentServerUrl(prefOrDefault(p, LoomSettings.KEY_AGENTSERVER_URL, d.agentServerUrl))
            .withObserverName(prefOrDefault(p, LoomSettings.KEY_OBSERVER_NAME, d.observerName))
            .withDriverName(prefOrDefault(p, LoomSettings.KEY_DRIVER_NAME, d.driverName))
            .withSlaveName(prefOrDefault(p, LoomSettings.KEY_SLAVE_NAME, d.slaveName))
            .withTags(prefOrDefault(p, LoomSettings.KEY_TAGS, d.tags))
            .withAgentProvider(new ProviderSettingsStore(requireContext()).getSelectedProvider());
        return agentServerBackedSettings(settings);
    }

    private LoomSettings runtimeSettings() {
        AssistantProvider runtimeProvider = savedRuntimeProvider();
        LoomSettings settings = currentLoomSettings();
        return runtimeProvider == null ? settings : settings.withAgentProvider(runtimeProvider);
    }

    private LoomSettings agentServerBackedSettings(LoomSettings settings) {
        SharedPreferences p = requireContext()
            .getSharedPreferences(AGENTSERVER_PREFS_NAME, Context.MODE_PRIVATE);
        String serverUrl = prefOrDefault(p, KEY_AGENTSERVER_URL, settings.agentServerUrl);
        String workspaceId = firstNonEmpty(
            p.getString(KEY_AGENTSERVER_WORKSPACE_ID, ""),
            p.getString(KEY_AGENTSERVER_SANDBOX_CODE, ""),
            p.getString(KEY_AGENTSERVER_SANDBOX_ID, ""),
            settings.workspaceId);
        return settings.withAgentServerUrl(serverUrl).withWorkspaceId(workspaceId);
    }

    @Nullable
    private AssistantProvider savedRuntimeProvider() {
        if (getContext() == null) return null;
        SharedPreferences p = requireContext()
            .getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE);
        String id = p.getString(LoomSettings.KEY_AGENT_PROVIDER, null);
        return id == null ? null : AssistantProvider.fromId(id);
    }

    private void rememberRuntimeProvider(LoomSettings settings) {
        requireContext().getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LoomSettings.KEY_AGENT_PROVIDER, settings.agentProvider.id)
            .apply();
    }

    private String transitionScript(LoomSettings targetSettings, String role) {
        AssistantProvider previousProvider = savedRuntimeProvider();
        if (previousProvider == null || previousProvider == targetSettings.agentProvider) return "";

        ProviderProfile previousProfile = ProviderProfile.forProvider(previousProvider);
        LoomSettings previousSettings = targetSettings.withAgentProvider(previousProvider);
        String header = "echo '[*] Loom 上次使用 " + previousProfile.displayName + "，先停止旧角色进程'\n";
        if ("observer".equals(role)) {
            return header + LoomCommandBuilder.stopObserverScript(previousSettings) + "\n";
        }
        if ("slave".equals(role)) {
            return header + LoomCommandBuilder.stopSlaveScript(previousSettings) + "\n";
        }
        return "";
    }

    private void maybeShowAuthUrl(String line) {
        String lower = line == null ? "" : line.toLowerCase();
        if (mAuthDialogShown || !lower.contains("http")) return;
        if (!lower.contains("device") && !lower.contains("authenticate")
                && !lower.contains("register") && !lower.contains("auth")) {
            return;
        }
        Matcher matcher = AUTH_URL_PATTERN.matcher(line);
        if (!matcher.find()) return;
        String authUrl = matcher.group();
        mAuthDialogShown = true;
        post(() -> showAuthDialog(authUrl));
    }

    private void showAuthDialog(String authUrl) {
        if (getContext() == null) return;
        if (mAuthDialog != null && mAuthDialog.isShowing()) mAuthDialog.dismiss();

        View view = LayoutInflater.from(getContext())
            .inflate(R.layout.dialog_auth_qr, null, false);
        ImageView qrIv = view.findViewById(R.id.auth_qr_image);
        TextView urlTv = view.findViewById(R.id.auth_url_text);
        urlTv.setText(authUrl);
        Bitmap bmp = QrCodeUtil.generate(authUrl, 600);
        if (bmp != null) qrIv.setImageBitmap(bmp);
        else qrIv.setVisibility(View.GONE);

        view.findViewById(R.id.auth_btn_copy).setOnClickListener(b -> {
            ClipboardManager cm = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(
                ClipData.newPlainText("loom driver auth url", authUrl));
            Toast.makeText(getContext(), "链接已复制", Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.auth_btn_open).setOnClickListener(b -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(getContext(), "无法打开浏览器: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            }
        });

        mAuthDialog = new AlertDialog.Builder(requireContext())
            .setView(view)
            .setNegativeButton("取消授权", (d, w) -> {
                d.dismiss();
                mAuthDialog = null;
            })
            .setCancelable(true)
            .create();
        mAuthDialog.show();
    }

    private void dismissAuthDialog() {
        if (mAuthDialog != null && mAuthDialog.isShowing()) {
            mAuthDialog.dismiss();
        }
        mAuthDialog = null;
        mAuthDialogShown = false;
    }

    private void setDriverBindingStatus(String text) {
        if (mDriverBindingStatus != null) mDriverBindingStatus.setText(text);
    }

    private String driverBindingStatusText(String status) {
        if (CollaborationConnectionState.DRIVER_STATUS_VALID.equals(status)) {
            return "Driver：已绑定";
        }
        if (CollaborationConnectionState.DRIVER_STATUS_STALE.equals(status)) {
            return "Driver：需重新绑定";
        }
        if (CollaborationConnectionState.DRIVER_STATUS_BINDING.equals(status)) {
            return "Driver：绑定中";
        }
        if (CollaborationConnectionState.DRIVER_STATUS_FAILED.equals(status)) {
            return "Driver：绑定失败";
        }
        return "Driver：需绑定";
    }

    private void updateDriverBindingDot(String status) {
        if (mDriverBindingDot == null) return;
        int resId = CollaborationConnectionState.DRIVER_STATUS_VALID.equals(status)
            ? R.drawable.bg_status_dot_connected
            : R.drawable.bg_status_dot_idle;
        mDriverBindingDot.setBackgroundResource(resId);
    }

    private String currentDriverBindingStatus(AssistantProvider provider) {
        if (getContext() == null) return CollaborationConnectionState.DRIVER_STATUS_MISSING;
        SharedPreferences p = requireContext()
            .getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE);
        return CollaborationConnectionState.driverBindingStatus(
            p.getString(KEY_DRIVER_BINDING_FINGERPRINT, ""),
            currentDriverFingerprint(provider),
            p.getString(KEY_DRIVER_BINDING_STATUS, ""));
    }

    private String currentDriverFingerprint(AssistantProvider provider) {
        AssistantProvider safe = provider == null ? AssistantProvider.CODEX : provider;
        ProviderProfile profile = ProviderProfile.forProvider(safe);
        LoomSettings settings = currentLoomSettings().withAgentProvider(safe);
        SharedPreferences agentPrefs = requireContext()
            .getSharedPreferences(AGENTSERVER_PREFS_NAME, Context.MODE_PRIVATE);
        String serverUrl = trim(agentPrefs.getString(KEY_AGENTSERVER_URL, ""));
        String deviceName = trim(agentPrefs.getString(KEY_AGENTSERVER_DEVICE_NAME, ""));
        return CollaborationConnectionState.computeDriverFingerprint(
            safe,
            serverUrl,
            currentWorkspaceId(),
            deviceName,
            settings.driverName,
            profile.driverConfigPath,
            profile.loomMcpConfigPath);
    }

    private String currentWorkspaceId() {
        if (getContext() == null) return "";
        SharedPreferences p = requireContext()
            .getSharedPreferences(AGENTSERVER_PREFS_NAME, Context.MODE_PRIVATE);
        return firstNonEmpty(
            p.getString(KEY_AGENTSERVER_WORKSPACE_ID, ""),
            p.getString(KEY_AGENTSERVER_SANDBOX_CODE, ""),
            p.getString(KEY_AGENTSERVER_SANDBOX_ID, ""));
    }

    private void markDriverBindingStale() {
        if (getContext() == null) return;
        requireContext().getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DRIVER_BINDING_STATUS, CollaborationConnectionState.DRIVER_STATUS_STALE)
            .apply();
    }

    private void setDriverBindingStatusPref(String status, String fingerprint) {
        if (getContext() == null) return;
        SharedPreferences.Editor editor = requireContext()
            .getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DRIVER_BINDING_STATUS, status);
        if (fingerprint != null && !fingerprint.trim().isEmpty()) {
            editor.putString(KEY_DRIVER_BINDING_FINGERPRINT, fingerprint);
        }
        editor.apply();
    }

    private void saveDriverBindingSuccess(AssistantProvider provider, LoomDriverConfigIdentity identity) {
        if (getContext() == null) return;
        AssistantProvider safe = provider == null ? AssistantProvider.CODEX : provider;
        SharedPreferences agentPrefs = requireContext()
            .getSharedPreferences(AGENTSERVER_PREFS_NAME, Context.MODE_PRIVATE);
        LoomDriverConfigIdentity safeIdentity = identity == null ? LoomDriverConfigIdentity.empty() : identity;
        String serverUrl = firstNonEmpty(safeIdentity.serverUrl, agentPrefs.getString(KEY_AGENTSERVER_URL, ""));
        String deviceName = trim(agentPrefs.getString(KEY_AGENTSERVER_DEVICE_NAME, ""));
        String workspaceId = firstNonEmpty(safeIdentity.workspaceId, currentWorkspaceId());
        LoomSettings settings = currentLoomSettings().withAgentProvider(safe);
        SharedPreferences.Editor agentEditor = agentPrefs.edit();
        if (!safeIdentity.serverUrl.isEmpty()) {
            agentEditor.putString(KEY_AGENTSERVER_URL, safeIdentity.serverUrl);
        }
        if (!safeIdentity.sandboxId.isEmpty()) {
            agentEditor.putString(KEY_AGENTSERVER_SANDBOX_ID, safeIdentity.sandboxId);
        }
        if (!safeIdentity.workspaceId.isEmpty()) {
            agentEditor.putString(KEY_AGENTSERVER_WORKSPACE_ID, safeIdentity.workspaceId);
        }
        if (!safeIdentity.shortId.isEmpty()) {
            agentEditor.putString(KEY_AGENTSERVER_SHORT_ID, safeIdentity.shortId);
        }
        agentEditor.apply();
        requireContext().getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DRIVER_BINDING_FINGERPRINT, currentDriverFingerprint(safe))
            .putString(KEY_DRIVER_BINDING_STATUS, CollaborationConnectionState.DRIVER_STATUS_VALID)
            .putString(KEY_DRIVER_BINDING_PROVIDER, safe.id)
            .putString(KEY_DRIVER_BINDING_SANDBOX_ID, workspaceId)
            .putString(KEY_DRIVER_BINDING_SERVER_URL, serverUrl)
            .putString(KEY_DRIVER_BINDING_DEVICE_NAME, deviceName)
            .putString(KEY_DRIVER_BINDING_DRIVER_NAME, settings.driverName)
            .apply();
    }

    private void setLocalRuntimeStatus(String text) {
        if (mLocalSlaveStatus != null) mLocalSlaveStatus.setText(text);
    }

    private void updateRoleActionButtons(String role) {
        String safeRole = normalizeRole(role);
        if (mStartRoleButton != null) {
            if ("observer".equals(safeRole)) {
                mStartRoleButton.setText("启动 Observer");
            } else {
                mStartRoleButton.setText("启动 Slave");
            }
        }
        if (mStopRoleButton != null) {
            mStopRoleButton.setText("停止当前角色");
            mStopRoleButton.setEnabled(true);
            mStopRoleButton.setAlpha(1.0f);
        }
    }

    private boolean isLoomRuntimeBusy() {
        return mLoomRuntimeThread != null && mLoomRuntimeThread.isAlive();
    }

    private String prefix() {
        String p = System.getenv("PREFIX");
        return (p == null || p.isEmpty()) ? "/data/data/com.termux/files/usr" : p;
    }

    private void post(Runnable runnable) {
        if (getActivity() != null) getActivity().runOnUiThread(runnable);
    }

    private static String roleLabel(String role) {
        if ("observer".equals(role)) return "Observer";
        return "Slave";
    }

    private String currentRoleMode() {
        if (getContext() == null) return "slave";
        LoomSettings d = LoomSettings.defaults();
        SharedPreferences p = requireContext()
            .getSharedPreferences(LoomSettings.PREFS_NAME, Context.MODE_PRIVATE);
        return normalizeRole(p.getString(LoomSettings.KEY_ROLE_MODE, d.roleMode));
    }

    private static int indexOfRole(String role) {
        String safe = normalizeRole(role);
        for (int i = 0; i < ROLE_VALUES.length; i++) {
            if (ROLE_VALUES[i].equals(safe)) return i;
        }
        return 0;
    }

    private static String normalizeRole(String role) {
        return "observer".equals(role) ? "observer" : "slave";
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8) + "...";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String prefOrDefault(SharedPreferences prefs, String key, String defaultValue) {
        String value = prefs.getString(key, defaultValue);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    @Nullable
    private TermuxActivity act() {
        return (getActivity() instanceof TermuxActivity)
            ? (TermuxActivity) getActivity() : null;
    }
}
