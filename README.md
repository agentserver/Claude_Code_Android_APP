# Claude Code Android App（Termux Fork）

基于 Termux 的 Android 应用：在手机上**一键全自动**部署 Ubuntu（`proot-distro`）、Claude Code、MCP（手机能力）与 AgentServer，并提供带管理能力的主页聊天 UI。

仓库默认面向 **arm64** 设备（内置离线安装包）。

## 主要功能

- **全自动快速安装**
  - 首次启动自动初始化 Termux 运行环境
  - 自动部署 Ubuntu/Claude Code/MCP/AgentServer（优先走快照/离线包，失败再回退在线安装）
- **API Key 配置**
  - 管理多组 `ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL`
  - 一键切换“当前使用”的 Key（主页聊天与 AgentServer 共用）
- **AgentServer 配置与连接**
  - 在 App 内直接连接上游 AgentServer（支持 `--resume` 复用沙盒）
  - 连接日志与实时监控（含 `mcp-audit.log`）
- **主页聊天 UI（Home）**
  - 流式输出渲染（Claude Code `stream-json`）
  - **历史**：会话列表、切换/删除、从 Claude session jsonl 回放
  - **记忆（Memory）**：查看/管理（与 Claude Code 工作目录一致）
  - **技能**：查看/管理skills `.claude/commands` 等
  - **任务**：管理来自AgentServer下发的任务、Claude 执行状态与输出内容
  - **已上传文件**：管理从本机上传给AI分析的文档、图片
    
## 使用方式（首次安装）

1. 安装 APK 后打开应用（最好保持前台与联网，首次初始化可能需要几分钟）
2. 打开 **API Key** 页面
   - 添加 `ANTHROPIC_API_KEY`
   - 如需自建网关可填写 `ANTHROPIC_BASE_URL`，否则留空使用官方默认
   - 设为“当前使用”
3. 打开 **Home** 页面开始对话
4. 如需接入上游任务：打开 **AgentServer** 页面填入 `Server URL`（例如 `https://agent.cs.ac.cn`）并点击连接

## AgentServer 任务显示是如何实现的

- AgentServer 在 Ubuntu 内运行时会调用 `claude` 执行任务。
- 应用在 Ubuntu 的 `~/.local/bin/claude` 注入 wrapper：
  - 把任务 prompt 与 Claude 的 `stream-json` 输出追加写入 `~/.agentserver-pipe.jsonl`
  - Home 页后台监听该 pipe 并渲染为“AgentServer 任务”列表

## 架构概览（与 `app架构.md` 对应）

应用整体是一个 Termux Fork，在 `TermuxActivity` 中承载多个页面（Fragment），并由 `AutoTaskCoordinator` 在启动时串联后台组件：

- **UI 层（Fragment）**
  - `HomeFragment`：主页聊天 + 历史/记忆/技能/任务/上传文件管理
  - `ApiKeyFragment`：API Key 多组管理与切换
  - `AgentServerFragment`：AgentServer 配置、连接、日志/监控
- **安装与环境（autotasks）**
  - `AutoUbuntuManager`：检测/部署 Ubuntu rootfs（优先快照，失败回退安装）
  - `AutoClaudeManager`：准备 Claude Code 的安装/修复脚本与 MCP 注册
  - `AutoAgentServerManager`：准备 AgentServer 安装包与安装脚本
  - `UbuntuSnapshotManager`：快照式快速部署 Ubuntu+Claude 环境（可离线/可在线）
- **本地服务（Android 进程内）**
  - `McpHttpServer`（`127.0.0.1:8765/mcp`）：把手机能力封装为 MCP 工具供 Claude Code 调用（UI/截图/相机/文件/App 等）
  - `ApiHttpBridgeServer`（`127.0.0.1:17681`）：旧的只读 HTTP 桥，主要用于在 Ubuntu 内提供 `termux-*` 兼容输出（电池/WiFi/传感器/剪贴板等）

## 文件附件流程（上传给 AI 分析）

Home 页选择文件后，会走“两段拷贝”，保证在 Ubuntu 容器里可读：

1. `URI` → App cache：`/data/data/com.termux/cache/upload_src`
2. `proot-distro login ubuntu --user claude --bind ...` 把文件复制到 `~/uploads/`
   - 最终路径形如 `/home/claude/uploads/<filename>`，会作为“附件路径”拼进 prompt

## 自动安装/启动流程（首次运行）

- `AutoTaskCoordinator` 初始化并启动本地服务（MCP/Bridge），后台准备安装脚本
- `AutoUbuntuManager` 在终端会话可用时尝试自动部署 Ubuntu：
  1. 优先从 APK 内置快照离线部署（如果你把快照打进 assets）
  2. 否则从 GitHub Release 下载快照快速部署
  3. 快照失败再回退 `proot-distro install ubuntu`（支持镜像源与 CDN 兜底）
- Ubuntu 登录后通过 `/root/.bashrc` 的一次性 hook 完成：Claude Code 安装/修复、AgentServer 安装、MCP 注册等

## 目录与日志（排查用）

- Termux HOME：`/data/data/com.termux/files/home`
  - AgentServer 日志：`~/agentserver-agent.log`
  - MCP 审计日志：`~/mcp-audit.log`
- Ubuntu rootfs：`/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu`

## 构建

- Android Studio + JDK 17
- Debug 构建：`./gradlew :app:assembleDebug`
- APK 输出目录：`app/build/outputs/apk/`
