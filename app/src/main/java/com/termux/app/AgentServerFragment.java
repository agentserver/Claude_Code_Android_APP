package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * AgentServer 配置与管理页面。
 *
 * 通过 proot-distro login ubuntu -- agentserver <subcommand> 与 Ubuntu 内的 agentserver 交互。
 */
public class AgentServerFragment extends Fragment {

    private static final String PREFS_NAME        = "agentserver_config";
    private static final String KEY_SERVER_URL    = "server_url";
    private static final String KEY_SANDBOX_CODE  = "sandbox_code";
    private static final String KEY_DEVICE_NAME   = "device_name";

    private TextView  mStatusText;
    private TextView  mInfoText;
    private EditText  mUrlEdit;
    private EditText  mCodeEdit;
    private EditText  mDeviceNameEdit;
    private TextView  mLogText;
    private ScrollView mLogScroll;

    private Thread mActiveThread;

    // ─────────────────────────────────────────────────────────────────────────
    // Fragment 生命周期
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_agent_server, container, false);

        mStatusText    = v.findViewById(R.id.agentserver_status_text);
        mInfoText      = v.findViewById(R.id.agentserver_info);
        mUrlEdit       = v.findViewById(R.id.agentserver_url);
        mCodeEdit      = v.findViewById(R.id.agentserver_code);
        mDeviceNameEdit = v.findViewById(R.id.agentserver_device_name);
        mLogText       = v.findViewById(R.id.agentserver_log);
        mLogScroll     = v.findViewById(R.id.agentserver_log_scroll);

        v.findViewById(R.id.btn_agentserver_connect)   .setOnClickListener(b -> doConnect());
        v.findViewById(R.id.btn_agentserver_stop)      .setOnClickListener(b -> doStop());
        v.findViewById(R.id.btn_agentserver_refresh)   .setOnClickListener(b -> checkStatus());
        v.findViewById(R.id.btn_agentserver_clear_log) .setOnClickListener(b -> clearLog());

        loadPrefs();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkStatus();   // 每次进入页面自动刷新状态
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelActiveThread();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 操作
    // ─────────────────────────────────────────────────────────────────────────

    private void checkStatus() {
        String prefix  = System.getenv("PREFIX");
        if (prefix == null || prefix.isEmpty()) prefix = "/data/data/com.termux/files/usr";
        String logFile = prefix + "/../home/agentserver-agent.log";

        String script =
            "if ! command -v proot-distro >/dev/null 2>&1; then\n" +
            "  echo '[!] proot-distro 未找到，Ubuntu 环境尚未初始化'; exit 1\n" +
            "fi\n" +
            "if ! proot-distro login ubuntu -- sh -c 'command -v agentserver >/dev/null 2>&1'; then\n" +
            "  echo '[!] AgentServer 未安装'; exit 1\n" +
            "fi\n" +
            "echo \"版本: $(proot-distro login ubuntu -- agentserver version 2>/dev/null)\"\n" +
            "echo ''\n" +
            // 检查 Termux 层的 proot agentserver 进程（不在 proot 内部 grep）
            "if pgrep -f 'agentserver' >/dev/null 2>&1; then\n" +
            "  echo '[*] Agent 运行中'\n" +
            "  pgrep -a -f 'agentserver' 2>/dev/null | grep -v grep | head -5\n" +
            "else\n" +
            "  echo '[-] Agent 未运行'\n" +
            "fi\n" +
            "echo ''\n" +
            "echo '=== 最近日志（最后 30 行）==='\n" +
            "tail -30 '" + logFile + "' 2>/dev/null || echo '（无日志文件）'\n";

        runScript(script, "刷新状态");
    }

    /**
     * 在 Termux 层 nohup 整个 proot-distro 进程，使 agentserver 后台持续运行。
     * v0.40.0 使用 claudecode（OAuth），首次运行日志里会出现认证 URL。
     * 注：不在 doConnect 里 pkill 已有进程（避免 pkill 的模式字符串匹配到自身 bash 脚本导致 exit 143）。
     */
    private void doConnect() {
        String url    = mUrlEdit.getText().toString().trim();
        String code   = mCodeEdit != null ? mCodeEdit.getText().toString().trim() : "";
        String device = mDeviceNameEdit.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(getContext(), "请填写服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        savePrefs();

        String prefix   = System.getenv("PREFIX");
        if (prefix == null || prefix.isEmpty()) prefix = "/data/data/com.termux/files/usr";
        String home     = prefix + "/../home";
        String logFile  = home + "/agentserver-agent.log";
        String pdBin    = prefix + "/bin/proot-distro";
        String safeUrl  = url.replace("'", "'\\''");
        String nameFlag = device.isEmpty() ? "" : " --name '" + device.replace("'", "'\\''") + "'";

        // v0.40.0 只有 claudecode（OAuth）；如未来 connect 子命令可用时可在此扩展
        String agentArgs = "claudecode --server '" + safeUrl + "'" + nameFlag + " --skip-open-browser";

        String script =
            "echo '[*] 正在启动 AgentServer（如首次使用需 OAuth 授权）...'\n" +
            // 在 Termux 层 nohup proot-distro，避免 proot shell 退出时杀死 agentserver
            "nohup '" + pdBin + "' login ubuntu -- agentserver " + agentArgs +
            " >> '" + logFile + "' 2>&1 &\n" +
            "AS_PID=$!\n" +
            "sleep 3\n" +
            "if kill -0 $AS_PID 2>/dev/null; then\n" +
            "  echo \"[*] Agent 已启动，PID: $AS_PID\"\n" +
            "  echo ''\n" +
            "  echo '=== 日志（如有 OAuth URL 请复制到浏览器）==='\n" +
            "  tail -30 '" + logFile + "' 2>/dev/null\n" +
            "else\n" +
            "  echo '[!] Agent 启动失败，日志:'\n" +
            "  cat '" + logFile + "' 2>/dev/null || echo '（无日志）'\n" +
            "fi\n";

        runScript(script, "连接 AgentServer");
    }

    /** 停止后台运行的 Agent（在 Termux 层 kill，不进入 proot）。 */
    private void doStop() {
        runScript(
            "pkill -f 'proot-distro.*login ubuntu' 2>/dev/null && echo '[*] Agent 已断开连接'" +
            " || echo '[!] 未找到运行中的 Agent 进程'",
            "断开连接"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 命令执行
    // ─────────────────────────────────────────────────────────────────────────

    private void runUbuntuCmd(String ubuntuCmd, String label) {
        // 在 Ubuntu proot 内执行单条命令，stderr 合并到 stdout
        String safe = ubuntuCmd.replace("'", "'\\''");
        runScript("proot-distro login ubuntu -- sh -c '" + safe + "' 2>&1", label);
    }

    private void runScript(String bashScript, String label) {
        cancelActiveThread();
        clearLog();
        appendLog("▶ " + label + "\n");
        setStatus("● 运行中", "#F57C00");
        setInfo("正在执行...");

        // Termux 的 bash 路径：$PREFIX/bin/bash，不能直接用 "bash"（Android 没有系统 bash）
        String prefix = System.getenv("PREFIX");
        if (prefix == null || prefix.isEmpty()) prefix = "/data/data/com.termux/files/usr";
        String bash = prefix + "/bin/bash";

        // 构建包含 Termux bin 路径的 PATH（System.getenv 里可能没有 $PREFIX/bin）
        String sysPath = System.getenv("PATH");
        if (sysPath == null) sysPath = "";
        String termuxPath = prefix + "/bin:" + prefix + "/bin/applets:" + sysPath;
        final String finalPrefix = prefix;
        final String finalPath   = termuxPath;

        mActiveThread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(bash, "-c", bashScript);
                pb.redirectErrorStream(true);
                java.util.Map<String, String> env = pb.environment();
                env.putAll(System.getenv());
                env.put("PATH",   finalPath);
                env.put("PREFIX", finalPrefix);
                env.put("HOME",   finalPrefix + "/../home"); // Termux $HOME
                Process p = pb.start();

                BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        p.destroy();
                        return;
                    }
                    final String l = line;
                    post(() -> appendLog(l + "\n"));
                }
                p.waitFor();
                int exit = p.exitValue();
                post(() -> {
                    appendLog("─────────────── 完成（exit " + exit + "）\n");
                    updateStatusFromLog(exit);
                });

            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                post(() -> {
                    appendLog("[!] 执行出错：" + e.getMessage() + "\n");
                    setStatus("● 错误", "#E53935");
                    setInfo("执行出错");
                });
            }
        }, "agentserver-cmd");
        mActiveThread.setDaemon(true);
        mActiveThread.start();
    }

    /** 根据退出码和日志内容推断状态，更新状态徽章和摘要文本。 */
    private void updateStatusFromLog(int exitCode) {
        String log = mLogText.getText().toString().toLowerCase();

        if (exitCode != 0) {
            if (log.contains("未安装") || log.contains("not installed") || log.contains("not found")) {
                setStatus("● 未安装", "#888888");
                setInfo("AgentServer 未安装，请重启应用等待自动安装");
            } else if (log.contains("未就绪") || log.contains("proot-distro 未找到")) {
                setStatus("● 环境未就绪", "#888888");
                setInfo("Ubuntu 环境尚未初始化，请先切换到终端 Tab");
            } else {
                setStatus("● 失败", "#E53935");
                setInfo("命令执行失败，请查看日志");
            }
            return;
        }

        if (log.contains("connected") || log.contains("已连接")) {
            setStatus("● 已连接", "#388E3C");
            setInfo("AgentServer 已连接到服务器");
        } else if (log.contains("running") || log.contains("运行中")) {
            setStatus("● 运行中", "#1976D2");
            setInfo("AgentServer 服务运行中");
        } else if (log.contains("stopped") || log.contains("已停止")) {
            setStatus("● 已停止", "#F57C00");
            setInfo("AgentServer 服务已停止");
        } else if (log.contains("disconnected") || log.contains("已断开")) {
            setStatus("● 已断开", "#888888");
            setInfo("AgentServer 已断开连接");
        } else {
            setStatus("● 已安装", "#555555");
            setInfo("AgentServer 已安装");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI 辅助
    // ─────────────────────────────────────────────────────────────────────────

    private void appendLog(String text) {
        mLogText.append(text);
        mLogScroll.post(() -> mLogScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void clearLog() {
        if (mLogText != null) mLogText.setText("");
    }

    private void setStatus(String text, String colorHex) {
        mStatusText.setText(text);
        mStatusText.setTextColor(Color.parseColor(colorHex));
    }

    private void setInfo(String text) {
        mInfoText.setText(text);
    }

    private void loadPrefs() {
        SharedPreferences p = requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mUrlEdit.setText(p.getString(KEY_SERVER_URL, ""));
        mCodeEdit.setText(p.getString(KEY_SANDBOX_CODE, ""));
        mDeviceNameEdit.setText(p.getString(KEY_DEVICE_NAME, ""));
    }

    private void savePrefs() {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, mUrlEdit.getText().toString().trim())
            .putString(KEY_SANDBOX_CODE, mCodeEdit.getText().toString().trim())
            .putString(KEY_DEVICE_NAME, mDeviceNameEdit.getText().toString().trim())
            .apply();
    }

    private void cancelActiveThread() {
        if (mActiveThread != null && mActiveThread.isAlive()) {
            mActiveThread.interrupt();
        }
    }

    private void post(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }
}
