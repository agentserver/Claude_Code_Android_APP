package com.termux.app.autotasks;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AutoUbuntuManager {

    // 资产目录下各架构 rootfs 文件名映射
    private static final String ASSET_DIR = "ubuntu";
    private static final String LOCAL_ROOTFS_BASE = ".ubuntu-local-rootfs";
    // 支持的压缩格式，优先 .tar.gz（与现有 assets 包格式一致），兼容 .tar.xz
    private static final String[] SUPPORTED_EXTENSIONS = { ".tar.gz", ".tar.xz" };

    // pkg 镜像源（清华 > 中科大，按优先级排列）
    private static final String[] PKG_MIRRORS = {
        "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main",
        "https://mirrors.ustc.edu.cn/termux/apt/termux-packages-24"
    };

    // Ubuntu rootfs CDN 镜像（Ubuntu 的 rootfs 来自 Ubuntu 官方 CDN，不是 GitHub）
    // 清华/中科大均镜像了 ubuntu-cdimage，可直接替换域名
    private static final String[][] ROOTFS_CDN_MIRRORS = {
        // { 原域名前缀,  镜像替换前缀 }
        { "https://cdimage.ubuntu.com/",     "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/" },
        { "https://cloud-images.ubuntu.com/","https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/" },
        { "https://cdimage.ubuntu.com/",     "https://mirrors.ustc.edu.cn/ubuntu-cdimage/" },
        { "https://cloud-images.ubuntu.com/","https://mirrors.ustc.edu.cn/ubuntu-cdimage/" },
    };

    private final TermuxActivity mActivity;
    private AutoAgentServerManager mAgentServerManager;
    private boolean mAutoLaunchAttempted;
    private boolean mEnabled = true;

    // 后台提取完成后填充，volatile 保证主线程可见
    private volatile String mLocalRootfsPath = null;
    private volatile boolean mExtractionDone = false;

    public AutoUbuntuManager(@NonNull TermuxActivity activity) {
        mActivity = activity;
        // 后台提前提取 assets 中的 rootfs，减少等待时间
        startRootfsExtraction();
    }

    /** 注入 AgentServer 管理器引用，用于在复制安装包前确认提取完成。 */
    public void setAgentServerManager(@NonNull AutoAgentServerManager mgr) {
        mAgentServerManager = mgr;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void resetAttempt() {
        mAutoLaunchAttempted = false;
    }

    public void maybeAutoLaunchUbuntu() {
        if (!mEnabled || mAutoLaunchAttempted) return;

        TerminalSession session = mActivity.getCurrentSession();
        if (session == null || !session.isRunning()) return;

        mAutoLaunchAttempted = true;

        // 若后台提取仍在进行，最多等待 5 秒，超时则放弃本地包走网络
        if (!mExtractionDone) {
            long deadline = System.currentTimeMillis() + 5000;
            while (!mExtractionDone && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
            }
        }
        // 等待 AgentServer asset 提取完成（最多 5 秒），避免 cp 时文件不存在被静默跳过
        if (mAgentServerManager != null) {
            mAgentServerManager.awaitExtraction(5000);
        }

        // 脚本写入临时文件再执行，避免超长单行命令超出 pty 输入缓冲区（N_TTY_BUF_SIZE=4096）被截断
        String scriptPath = writeScriptToFile();
        if (scriptPath != null) {
            session.write("bash '" + scriptPath + "'\n");
        } else {
            session.write(buildUbuntuCommand()); // 兜底（文件写入失败时）
        }
    }

    /**
     * 将安装脚本写入 $HOME/.ubuntu-setup.sh 并返回绝对路径。
     * 失败时返回 null。
     */
    @Nullable
    private String writeScriptToFile() {
        File scriptFile = new File(mActivity.getFilesDir(), "home/.ubuntu-setup.sh");
        try {
            scriptFile.getParentFile().mkdirs();
            try (java.io.FileWriter w = new java.io.FileWriter(scriptFile)) {
                w.write(buildUbuntuCommand());
            }
            return scriptFile.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 资产提取（后台线程）
    // -------------------------------------------------------------------------

    private void startRootfsExtraction() {
        Thread t = new Thread(() -> {
            mLocalRootfsPath = extractRootfsAsset();
            mExtractionDone = true;
        }, "ubuntu-rootfs-extract");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 将 assets/ubuntu/ubuntu-rootfs-{arch}.tar.{gz,xz} 复制到 $HOME 目录。
     * 若文件已存在且非空则直接返回路径（跳过重复提取）。
     * @return 本地文件的绝对路径，或 null（资产不存在 / 提取失败）
     */
    @Nullable
    private String extractRootfsAsset() {
        // 检测 assets 中实际存在的 rootfs 文件（.tar.gz 优先）
        String assetFileName = resolveRootfsAssetName();
        if (assetFileName == null) return null; // assets 中没有预置文件，走网络

        String assetName = ASSET_DIR + "/" + assetFileName;
        // 本地文件名必须保留原始扩展名：proot-distro 依赖 URL 扩展名选择解压器
        String ext = assetFileName.endsWith(".tar.gz") ? ".tar.gz" : ".tar.xz";
        String localFileName = LOCAL_ROOTFS_BASE + ext;

        // 目标路径：/data/data/<pkg>/files/home/.ubuntu-local-rootfs.tar.{gz,xz}
        // getFilesDir() = .../files，加 "home" = .../files/home（Termux $HOME）
        // 注意：getFilesDir().getParent() = .../（缺少 files 层），是错误写法
        File homeDir = new File(mActivity.getFilesDir(), "home");
        File destFile = new File(homeDir, localFileName);

        // 已存在且非空则复用
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.getAbsolutePath();
        }

        homeDir.mkdirs();
        File tmpFile = new File(homeDir, localFileName + ".tmp");
        try (InputStream in = mActivity.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(tmpFile)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            tmpFile.delete();
            return null;
        }

        if (!tmpFile.renameTo(destFile)) {
            tmpFile.delete();
            return null;
        }
        return destFile.getAbsolutePath();
    }

    /**
     * 检测 assets 中实际存在的 rootfs 文件名（含扩展名）。
     * 优先 .tar.gz，兼容 .tar.xz。找不到返回 null。
     */
    @Nullable
    private String resolveRootfsAssetName() {
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
        // proot-distro 架构名
        String arch;
        switch (abi) {
            case "armeabi-v7a": arch = "arm";    break;
            case "x86_64":      arch = "x86_64"; break;
            case "x86":         arch = "x86";    break;
            default:            arch = "aarch64"; break; // arm64-v8a
        }
        // Ubuntu 官方 base 包使用的架构名（与 proot-distro 不同）
        String ubuntuArch;
        switch (abi) {
            case "armeabi-v7a": ubuntuArch = "armhf"; break;
            case "x86_64":      ubuntuArch = "amd64"; break;
            case "x86":         ubuntuArch = "i386";  break;
            default:            ubuntuArch = "arm64"; break; // arm64-v8a
        }

        try {
            String[] files = mActivity.getAssets().list(ASSET_DIR);
            if (files != null) {
                // 第1优先：proot-distro 专用包 ubuntu-questing-{arch}-pd-*.tar.xz
                // 命名规则与 easycli.sh CDN 下载链接完全一致，直接替换 URL 即可使用，无需额外处理
                for (String f : files) {
                    if (f.startsWith("ubuntu-questing-") && f.contains("-" + arch + "-")
                            && (f.endsWith(".tar.xz") || f.endsWith(".tar.gz"))) {
                        return f;
                    }
                }
                // 第2优先：Ubuntu 官方 base 包 ubuntu-base-*-{ubuntuArch}.tar.{gz,xz}
                for (String f : files) {
                    if (f.startsWith("ubuntu-base-") && f.contains("-" + ubuntuArch)
                            && (f.endsWith(".tar.gz") || f.endsWith(".tar.xz"))) {
                        return f;
                    }
                }
            }
        } catch (IOException ignored) {}

        // 第3优先：ubuntu-rootfs-{arch}.tar.{gz,xz}（自定义打包格式兼容）
        for (String ext : SUPPORTED_EXTENSIONS) {
            String name = "ubuntu-rootfs-" + arch + ext;
            try {
                mActivity.getAssets().open(ASSET_DIR + "/" + name).close();
                return name;
            } catch (IOException ignored) {}
        }
        return null; // assets 中没有预置文件
    }

    // -------------------------------------------------------------------------
    // Shell 命令生成
    // -------------------------------------------------------------------------

    private String buildUbuntuCommand() {
        String localPath = mLocalRootfsPath != null ? mLocalRootfsPath : "";

        // pkg 镜像列表拼成 shell 字符串："mirror1 mirror2 ..."
        StringBuilder pkgMirrors = new StringBuilder();
        for (String m : PKG_MIRRORS) pkgMirrors.append(m).append(" ");

        // rootfs CDN 镜像列表：每对加单引号避免 | 被 bash 解析为管道符
        StringBuilder rootfsMirrors = new StringBuilder();
        for (String[] pair : ROOTFS_CDN_MIRRORS) {
            rootfsMirrors.append("'").append(pair[0]).append("|").append(pair[1]).append("' ");
        }

        StringBuilder sb = new StringBuilder();

        // ── 入口判断：已在 ubuntu 则直接跳过 ──────────────────────────────────
        sb.append("if [ \"${PROOT_DISTRO_NAME:-}\" = \"ubuntu\" ]; then ")
          .append("echo \"[*] Already in ubuntu.\"; ")
          .append("else ");

        // ── 工具函数 ──────────────────────────────────────────────────────────
        // retry_cmd: 带退避重试
        sb.append("retry_cmd() { ")
          .append("cmd=\"$1\"; label=\"$2\"; attempts=${3:-3}; delay=${4:-5}; ")
          .append("i=1; while [ $i -le $attempts ]; do ")
          .append("echo \"[*] ${label} (try ${i}/${attempts})\"; ")
          .append("eval \"$cmd\" && return 0; ")
          .append("echo \"[!] failed (exit $?).\"; ")
          .append("if [ $i -lt $attempts ]; then echo \"[*] retry in ${delay}s\"; sleep $delay; fi; ")
          .append("i=$((i+1)); delay=$((delay*2)); ")
          .append("done; return 1; }; ");

        // switch_pkg_mirror: 切换 Termux apt 源
        // 同时删除 sources.list.d/ 下的动态镜像列表文件，防止 pkg 的测速机制覆盖我们的配置
        sb.append("switch_pkg_mirror() { ")
          .append("mirror=\"$1\"; ")
          .append("echo \"deb $mirror stable main\" > $PREFIX/etc/apt/sources.list; ")
          .append("rm -f $PREFIX/etc/apt/sources.list.d/*.list 2>/dev/null; ")
          .append("echo \"[*] apt source -> $mirror\"; }; ");

        // override_distro_setup: 直接在 proot-distro 系统文件末尾追加 distro_setup 无操作覆盖。
        // proot-distro 4.38 实际读 $PREFIX/etc/proot-distro/ubuntu.sh，而非用户配置目录；
        // bash 取最后一个同名函数，追加到文件末尾即可覆盖原实现，跳过触发 signal 11 的 proot 内 syscall。
        // grep 防止重复追加（多次安装时幂等）。
        sb.append("override_distro_setup() { ")
          .append("sys=\"$PREFIX/etc/proot-distro/ubuntu.sh\"; ")
          .append("if [ -f \"$sys\" ]; then ")
          .append("grep -qF 'distro_setup() { true; }' \"$sys\" 2>/dev/null || ")
          .append("{ printf '\\ndistro_setup() { true; }\\n' >> \"$sys\"; ")
          .append("echo \"[*] distro_setup overridden in system ubuntu.sh (signal-11 bypass)\"; }; ")
          .append("fi; }; ");

        // backup_sys: 首次调用时将系统 ubuntu.sh 备份为 ubuntu.sh.orig（幂等）
        // restore_sys: 从备份还原系统文件，再追加 distro_setup 覆盖（幂等）
        // proot-distro 4.38+ 只读 $PREFIX/etc/proot-distro/ubuntu.sh，用户配置目录对 URL 无效
        sb.append("backup_sys() { ")
          .append("sys=\"$PREFIX/etc/proot-distro/ubuntu.sh\"; ")
          .append("[ -f \"$sys\" ] && [ ! -f \"${sys}.orig\" ] && { cp \"$sys\" \"${sys}.orig\"; echo \"[*] sys ubuntu.sh backed up\"; }; }; ");

        sb.append("restore_sys() { ")
          .append("sys=\"$PREFIX/etc/proot-distro/ubuntu.sh\"; ")
          // 若还没有备份则先备份（兼容无本地包时直接走到 2b/2c 的场景）
          .append("[ -f \"$sys\" ] && [ ! -f \"${sys}.orig\" ] && cp \"$sys\" \"${sys}.orig\"; ")
          .append("[ -f \"${sys}.orig\" ] && { cp \"${sys}.orig\" \"$sys\"; echo \"[*] sys ubuntu.sh restored\"; }; ")
          .append("override_distro_setup; }; ");

        // apply_rootfs_mirror: 还原系统 ubuntu.sh 后在原文件上替换 CDN 域名
        // 每次从干净备份出发，防止多个镜像叠加修改
        sb.append("apply_rootfs_mirror() { ")
          .append("pair=\"$1\"; ")
          .append("orig=\"${pair%%|*}\"; repl=\"${pair##*|}\"; ")
          .append("sys=\"$PREFIX/etc/proot-distro/ubuntu.sh\"; ")
          .append("restore_sys; ")
          .append("[ -f \"$sys\" ] && sed -i \"s|${orig}|${repl}|g\" \"$sys\"; ")
          .append("echo \"[*] rootfs mirror: $orig -> $repl\"; }; ");

        // apply_local_rootfs: 直接修改系统 ubuntu.sh（proot-distro 4.38+ 只读系统文件）
        // 按 URL 内容替换而非按变量名，兼容 DISTRO_ARCHITECTURE_AARCH64_TARBALL_URL 等所有命名格式
        if (!localPath.isEmpty()) {
            sb.append("apply_local_rootfs() { ")
              .append("sys=\"$PREFIX/etc/proot-distro/ubuntu.sh\"; ")
              .append("backup_sys; restore_sys; ")
              .append("if [ -f \"$sys\" ]; then ")
              // 替换 easycli.sh / Ubuntu CDN 任意 URL → 本地 file:// 路径（覆盖所有变量命名格式）
              .append("_lu=\"file://").append(localPath).append("\"; ")
              .append("sed -i \"s|https://easycli.sh/[^[:space:]'\\\"]*|$_lu|g\" \"$sys\"; ")
              .append("sed -i \"s|https://cdimage.ubuntu.com/[^[:space:]'\\\"]*|$_lu|g\" \"$sys\"; ")
              .append("sed -i \"s|https://cloud-images.ubuntu.com/[^[:space:]'\\\"]*|$_lu|g\" \"$sys\"; ")
              // 清空 SHA256：保留变量名前缀，只清空值（兼容带架构名的变量如 ..._AARCH64_TARBALL_SHA256=）
              .append("sed -i 's/\\(.*SHA256=\\).*/\\1\"\"/g' \"$sys\"; ")
              .append("override_distro_setup; ")
              .append("echo \"[*] sys ubuntu.sh -> local bundle\"; ")
              // 打印补丁后的 URL 行，用于确认替换是否生效
              .append("grep -i 'URL\\|SHA256' \"$sys\" | grep -v '^[[:space:]]*#' | head -6 | sed 's/^/[D] /'; ")
              .append("fi; }; ");
        }

        // ── Step 1: 安装 proot-distro ─────────────────────────────────────────
        sb.append("auto_ok=1; ")
          .append("if ! command -v proot-distro >/dev/null 2>&1; then ")
          .append("echo \"[*] Installing proot-distro...\"; ")
          // 直接用 apt-get（而非 pkg），跳过 Termux 的镜像测速机制
          // pkg update 会对所有已知镜像测速，在中国网络下全部超时；apt-get 只读 sources.list
          .append("pkg_ok=0; ")
          .append("for m in ").append(pkgMirrors).append("; do ")
          .append("switch_pkg_mirror \"$m\"; ")
          .append("echo \"[*] apt-get update...\"; ")
          .append("apt-get update -y 2>&1 && ")
          .append("echo \"[*] apt-get install proot-distro...\"; ")
          .append("apt-get install -y proot-distro 2>&1 && { pkg_ok=1; break; }; ")
          .append("echo \"[!] mirror $m failed, trying next...\"; ")
          .append("done; ")
          .append("[ \"$pkg_ok\" != \"1\" ] && { echo \"[!] proot-distro install failed.\"; auto_ok=0; }; ")
          .append("fi; ");

        // ── Step 2: 安装 Ubuntu rootfs ────────────────────────────────────────
        sb.append("if [ \"$auto_ok\" = \"1\" ] && ")
          .append("[ ! -d \"$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu\" ]; then ")
          .append("echo \"[*] Installing Ubuntu...\"; ")
          // 升级 proot 本体，修复旧版无法模拟 Ubuntu 25.10 新 syscall 导致的 signal 11 崩溃
          .append("echo \"[*] Upgrading proot and proot-distro...\"; ")
          .append("apt-get install -y --only-upgrade proot proot-distro 2>&1 || true; ")
          // 修复 proot-distro 部分版本的 "cpu_arch: unbound variable" bug：
          // 原因：proot-distro 在函数内用 local cpu_arch 声明变量，这会遮蔽父 shell export 的同名
          // 环境变量；当架构未匹配时 cpu_arch 仍为 unset，加上 set -u 就会崩溃。
          // 方案：直接从 proot-distro 的 set 选项中去掉 -u（nounset），使未赋值变量不致命。
          .append("pd_bin=$(command -v proot-distro 2>/dev/null); ")
          .append("[ -n \"$pd_bin\" ] && [ -f \"$pd_bin\" ] && { ")
          .append("sed -i 's/set -euo/set -eo/g' \"$pd_bin\" 2>/dev/null; ")
          .append("sed -i 's/set -uo/set -o/g' \"$pd_bin\" 2>/dev/null; ")
          .append("echo \"[*] proot-distro patched (removed -u).\"; }; ")
          // 同时 export cpu_arch 作为兜底：对未用 local 声明的旧版 proot-distro 仍有效
          .append("case \"$(uname -m)\" in ")
          .append("aarch64) export cpu_arch=aarch64 ;; ")
          .append("armv7*|armv8l) export cpu_arch=arm ;; ")
          .append("x86_64) export cpu_arch=x86_64 ;; ")
          .append("i*86) export cpu_arch=i686 ;; ")
          .append("*) export cpu_arch=$(uname -m) ;; ")
          .append("esac; ")
          .append("echo \"[*] cpu_arch=$cpu_arch\"; ")
          .append("ubuntu_ok=0; ");

        // 2a: 优先本地预置包
        if (!localPath.isEmpty()) {
            sb.append("if [ -f \"").append(localPath).append("\" ]; then ")
              .append("echo \"[*] Found local bundle, trying offline install...\"; ")
              .append("apply_local_rootfs; ")
              .append("if proot-distro install ubuntu 2>&1; then ")
              .append("ubuntu_ok=1; echo \"[*] Installed from local bundle.\"; ")
              .append("else ")
              .append("echo \"[!] Local bundle failed, falling back to network.\"; ")
              .append("restore_sys; ")
              .append("fi; ")
              .append("fi; ");
        }

        // 2b: 直连网络（从还原的干净系统文件出发，只追加 distro_setup 覆盖）
        sb.append("if [ \"$ubuntu_ok\" != \"1\" ]; then ")
          .append("echo \"[*] Trying direct network install...\"; ")
          .append("restore_sys; ")
          .append("proot-distro install ubuntu 2>&1 && ubuntu_ok=1; ")
          .append("fi; ");

        // 2c: 依次尝试 Ubuntu CDN 国内镜像
        sb.append("if [ \"$ubuntu_ok\" != \"1\" ]; then ")
          .append("echo \"[*] Trying Ubuntu CDN mirrors...\"; ")
          .append("for pair in ").append(rootfsMirrors).append("; do ")
          .append("apply_rootfs_mirror \"$pair\"; ")
          .append("proot-distro install ubuntu 2>&1 && { ubuntu_ok=1; break; }; ")
          .append("echo \"[!] mirror failed, trying next...\"; ")
          .append("done; ")
          .append("fi; ");

        // 全部失败
        sb.append("if [ \"$ubuntu_ok\" != \"1\" ]; then ")
          .append("echo \"[!] All install attempts failed.\"; ")
          .append("echo \"    Tip: pre-place ubuntu-rootfs-aarch64.tar.xz in app assets to install offline.\"; ")
          .append("auto_ok=0; fi; ")
          .append("fi; ");

        // ── Step 2.9: 注入 Claude Code 安装向导（幂等，每次启动均执行）────────
        // inner 脚本由 AutoClaudeManager 在后台写入 Termux $HOME；
        // 此处将其复制到 Ubuntu rootfs 并在 /root/.bashrc 追加 source hook。
        // inner 脚本首行检查 claude 是否已安装——已装则自我清除，未装则交互引导。
        String claudeInnerPath = new File(mActivity.getFilesDir(),
            AutoClaudeManager.INNER_SCRIPT_REL).getAbsolutePath();
        String capabilitiesPath = new File(mActivity.getFilesDir(),
            CapabilitiesManager.CAPABILITIES_FILE_REL).getAbsolutePath();
        String agentTgzPath = new File(mActivity.getFilesDir(),
            AutoAgentServerManager.ASSET_TGZ_REL).getAbsolutePath();
        String agentInnerPath = new File(mActivity.getFilesDir(),
            AutoAgentServerManager.INNER_SCRIPT_REL).getAbsolutePath();
        sb.append("if [ \"$auto_ok\" = \"1\" ]; then ")
          .append("_ubr=\"$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu\"; ")
          .append("_cis=\"").append(claudeInnerPath).append("\"; ")
          .append("if [ -d \"$_ubr/root\" ]; then ")
          // 注入 claude 安装向导
          .append("[ -f \"$_cis\" ] && cp \"$_cis\" \"$_ubr/root/.claude-setup.sh\" && ")
          .append("{ grep -qF '.claude-setup' \"$_ubr/root/.bashrc\" 2>/dev/null || ")
          .append("printf '\\n[ -f ~/.claude-setup.sh ] && . ~/.claude-setup.sh\\n' ")
          .append(">> \"$_ubr/root/.bashrc\"; }; ")
          // 注入 AgentServer 安装包 + 安装向导（在 Claude hook 之后，确保安装时 Claude 已就绪）
          .append("if [ -f \"").append(agentTgzPath).append("\" ] && [ -s \"").append(agentTgzPath).append("\" ]; then ")
          .append("mkdir -p \"$_ubr/tmp\" && ")
          .append("cp \"").append(agentTgzPath).append("\" \"$_ubr/tmp/agentserver-linux-arm64.tar.gz\" && ")
          .append("echo \"[*] agentserver 安装包已复制到 Ubuntu /tmp/\"; ")
          .append("else echo \"[!] agentserver 安装包未就绪（路径: ").append(agentTgzPath).append("），将由脚本联网下载\"; fi; ")
          .append("[ -f \"").append(agentInnerPath).append("\" ] && ")
          .append("cp \"").append(agentInnerPath).append("\" \"$_ubr/root/.agentserver-setup.sh\" && ")
          .append("{ grep -qF '.agentserver-setup' \"$_ubr/root/.bashrc\" 2>/dev/null || ")
          .append("printf '\\n[ -f ~/.agentserver-setup.sh ] && . ~/.agentserver-setup.sh\\n' ")
          .append(">> \"$_ubr/root/.bashrc\"; }; ")
          // 建 capabilities.json 软链接：Ubuntu 内 ~/capabilities.json -> Termux home 的文件
          .append("ln -sf \"").append(capabilitiesPath).append("\" ")
          .append("\"$_ubr/root/capabilities.json\" 2>/dev/null; ")
          // 建 termux-* wrapper 脚本（HTTP 桥接版）
          // Termux 二进制为 Android bionic 编译，Ubuntu glibc 环境无法执行（exec 会报错）。
          // 改用 curl 调用 ApiHttpBridgeServer（Android 侧 HTTP 服务，127.0.0.1:PORT）。
          .append("mkdir -p \"$_ubr/usr/local/bin\"; ")
          .append("_bp=").append(ApiHttpBridgeServer.PORT).append("; ")
          .append("printf '#!/bin/sh\\ncurl -sf http://127.0.0.1:%s/battery\\n' \"$_bp\" ")
          .append("> \"$_ubr/usr/local/bin/termux-battery-status\" && ")
          .append("chmod +x \"$_ubr/usr/local/bin/termux-battery-status\" 2>/dev/null; ")
          .append("printf '#!/bin/sh\\ncurl -sf http://127.0.0.1:%s/camera\\n' \"$_bp\" ")
          .append("> \"$_ubr/usr/local/bin/termux-camera-info\" && ")
          .append("chmod +x \"$_ubr/usr/local/bin/termux-camera-info\" 2>/dev/null; ")
          .append("printf '#!/bin/sh\\ncurl -sf http://127.0.0.1:%s/sensors\\n' \"$_bp\" ")
          .append("> \"$_ubr/usr/local/bin/termux-sensor\" && ")
          .append("chmod +x \"$_ubr/usr/local/bin/termux-sensor\" 2>/dev/null; ")
          .append("printf '#!/bin/sh\\ncurl -sf http://127.0.0.1:%s/wifi\\n' \"$_bp\" ")
          .append("> \"$_ubr/usr/local/bin/termux-wifi-connectioninfo\" && ")
          .append("chmod +x \"$_ubr/usr/local/bin/termux-wifi-connectioninfo\" 2>/dev/null; ")
          .append("printf '#!/bin/sh\\ncurl -sf http://127.0.0.1:%s/clipboard\\n' \"$_bp\" ")
          .append("> \"$_ubr/usr/local/bin/termux-clipboard-get\" && ")
          .append("chmod +x \"$_ubr/usr/local/bin/termux-clipboard-get\" 2>/dev/null; ")
          .append("echo \"[*] Claude + AgentServer setup + capabilities ready.\"; fi; fi; ");

        // ── Step 3: 登录 Ubuntu ───────────────────────────────────────────────
        // 登录策略：依次尝试四种组合，直到成功（用 || 链：前一个非零退出才执行下一个）。
        // signal 11 根因：Ubuntu 25.10 的 glibc/bash 在启动时调用了 proot 未完全拦截的 syscall。
        //   - --kernel 5.4.0：让 glibc 认为内核较旧，退回不依赖新 syscall 的代码路径
        //   - -- /bin/sh：用比 bash 更简单的 shell，减少对 syscall 的依赖
        sb.append("if [ \"$auto_ok\" = \"1\" ]; then ")
          .append("proot-distro login ubuntu || ")
          .append("proot-distro login --kernel 5.4.0 ubuntu || ")
          .append("proot-distro login --kernel 5.4.0 ubuntu -- /bin/sh || ")
          .append("proot-distro login ubuntu -- /bin/sh; ")
          .append("fi; ");

        sb.append("fi\n");
        return sb.toString();
    }
}
