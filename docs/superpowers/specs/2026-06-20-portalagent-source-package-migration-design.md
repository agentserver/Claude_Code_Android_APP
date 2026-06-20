# PortalAgent Source Package Migration Design

## 背景

当前 APK 的安装包名已经是 `com.portalagent`，配置位置在 `app/build.gradle` 的 `applicationId "com.portalagent"`。但 Android namespace 仍是 `com.termux`，源码也集中在 `app/src/main/java/com/termux` 下。

这种结构可以运行，但不适合作为长期可发行 App 维护：PortalAgent 产品代码、Agent 功能、MCP 能力和 Termux 底座代码混在同一个 Java 包里，后续合并上游 Termux、排查发布问题、做模块边界审查都会变难。

## 目标

把源码结构调整成“Termux 底座”和“PortalAgent 产品能力”并列，而不是一次性改掉整个包名。

目标结构：

```text
app/src/main/java/com/termux/
  Termux 上游底座、终端运行时、bootstrap、terminal、filepicker、Termux API 兼容层

app/src/main/java/com/portalagent/
  PortalAgent 产品 UI、Agent provider、聊天、协作、Loom、AgentServer、MCP、自动化、环境安装编排
```

短期保持：

```gradle
namespace "com.termux"
applicationId "com.portalagent"
```

这样可以避免一次性触碰 `R` 类、Manifest 相对类名、Termux bootstrap、shared 常量和已有环境路径修复逻辑。

## 非目标

- 第一阶段不修改 `applicationId`，它已经是 `com.portalagent`。
- 第一阶段不修改 `namespace`，仍保留 `com.termux`。
- 第一阶段不重命名 `TermuxActivity`、`TermuxService`、`TermuxApplication`。
- 第一阶段不拆 Gradle module。
- 第一阶段不修改 Ubuntu 快照、bootstrap 包或已发布 APK 资产。

## 阶段规划

### Phase 0: 结构护栏

新增包结构回归测试，明确哪些包必须迁入 `com.portalagent`，哪些包必须暂留 `com.termux`。

护栏规则：

- `automation`、`loom`、`collab` 属于 PortalAgent 产品代码。
- `mcp` 最终属于 PortalAgent，但包含 Manifest service，放到后续阶段。
- `autotasks` 最终属于 PortalAgent，但与 `TermuxActivity`、`TermuxConstants`、安装脚本耦合较深，放到后续阶段。
- `TermuxActivity`、`TermuxService`、`TermuxApplication` 暂留 `com.termux.app`。

### Phase 1: 迁移纯产品逻辑包

迁移低风险、边界清晰、无需 Manifest 改名的包：

```text
com.termux.app.automation -> com.portalagent.automation
com.termux.app.loom       -> com.portalagent.loom
com.termux.app.collab     -> com.portalagent.collab
```

同步更新引用它们的 UI、MCP、autotasks 和测试代码。

### Phase 2: 迁移 MCP 能力层

迁移 Android MCP 相关包：

```text
com.termux.app.mcp -> com.portalagent.mcp
```

这一步需要同步改：

- `AndroidManifest.xml` 中 `ScreenCaptureService`、`McpAccessibilityService`。
- `accessibility_service_config` 相关引用。
- `HomeFragment`、`AutoTaskCoordinator`、自动化运行器中的 imports。
- MCP 相关单测中的服务类名断言。

### Phase 3: 迁移 Agent 运行时和安装编排

迁移环境安装、Provider、聊天、AgentServer、Loom UI、协作 UI：

```text
com.termux.app.autotasks -> com.portalagent.setup
com.termux.app.Provider* -> com.portalagent.provider
com.termux.app.Chat*     -> com.portalagent.chat
com.termux.app.AgentServer* -> com.portalagent.agentserver
com.termux.app.LoomFragment / CollaborationFragment -> com.portalagent.ui.collaboration
com.termux.app.HomeFragment / ApiKeyFragment / SettingsHubFragment -> com.portalagent.ui.*
```

`TermuxActivity` 暂时作为宿主保留在 `com.termux.app`，但它只应该 import PortalAgent UI 和控制器，不再承载业务实现。

### Phase 4: Manifest 和入口层清理

当产品代码迁移稳定后，评估是否引入 PortalAgent 入口类：

```text
com.portalagent.PortalAgentActivity
com.portalagent.PortalAgentApplication
```

这一步有两种方案：

- 包装方案：PortalAgent 入口继承或委托 Termux 入口，风险较低。
- 完整改名方案：Manifest 主入口改成 PortalAgent 类，Termux 类只作为底座内部实现，风险较高。

Phase 4 完成前，不建议改 `namespace`。

### Phase 5: namespace 评估

只有当 Manifest、入口类、资源引用和测试都稳定后，才评估：

```gradle
namespace "com.portalagent"
```

这个改动会影响 `R` 类包名、Manifest 相对类名和大量 import，必须单独做，不与业务迁移混在一起。

## 第一阶段设计

第一阶段只迁移 `automation`、`loom`、`collab` 三组包。

原因：

- 它们明显是 PortalAgent 新增能力，不属于 Termux 上游底座。
- 它们大多是普通 Java 类，Manifest 中没有直接声明。
- 它们已有较多单测，迁移后可以快速验证行为未变。
- 它们被 UI、MCP、autotasks 引用，迁移后能立刻形成 `com.termux` 调用 `com.portalagent` 的真实边界。

第一阶段完成后，源码会形成：

```text
app/src/main/java/com/portalagent/automation
app/src/main/java/com/portalagent/loom
app/src/main/java/com/portalagent/collab
```

测试会同步形成：

```text
app/src/test/java/com/portalagent/automation
app/src/test/java/com/portalagent/loom
app/src/test/java/com/portalagent/collab
```

## 验证策略

每个阶段至少执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

如果阶段涉及 Manifest service、Activity、Accessibility 或截图服务，还需要安装到 Xreal 并验证：

```powershell
adb -s R1LM45S11867DC install -r app\build\outputs\apk\debug\portal-agent_apt-android-7-debug_universal.apk
```

第一阶段不改 Manifest，但仍建议安装到 Xreal，至少确认首页、协作页、自动化页能正常打开。

## 发布化判断标准

这个迁移不以“包名全改完”为成功标准，而以边界清晰为标准：

- Termux 底座代码可以被识别和隔离。
- PortalAgent 产品代码集中在 `com.portalagent` 下。
- Manifest 入口层仍能稳定启动。
- Ubuntu/AgentServer/Loom/MCP 能力不因源码迁移改变运行路径。
- 测试能防止新增产品代码继续落回 `com.termux.app`。
