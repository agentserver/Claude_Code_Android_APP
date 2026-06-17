# Unified Collaboration Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the Android collaboration page so AgentServer is presented as the workspace/control plane and Loom as the multi-agent orchestration layer under that workspace.

**Architecture:** This is Phase 1 from the design spec. Keep existing AgentServer and Loom command builders, fragments, preferences, and installers unchanged; only reshape the collaboration dashboard, summaries, navigation labels, and tests.

**Tech Stack:** Android Java, XML layouts, MaterialCardView, SharedPreferences, JUnit4 XML/source structure tests.

---

## File Structure

- Modify `app/src/main/res/layout/fragment_collaboration.xml`
  - Replace the two peer cards with a unified Dashboard: workspace, local runtime, Loom orchestration, Android capabilities, update area.
  - Keep stable IDs needed by existing code where reasonable.
  - Add new stable IDs for structure tests and status summaries.
- Modify `app/src/main/java/com/termux/app/CollaborationFragment.java`
  - Bind new TextViews.
  - Render AgentServer as workspace status.
  - Render Loom as orchestration/runtime status.
  - Keep provider switch, role switch, driver binding, and detail-page navigation behavior.
- Modify `app/src/test/java/com/termux/app/CollaborationNavigationStructureTest.java`
  - Replace old "AgentServer and Loom peer entries" assertions with unified section assertions.
  - Keep assertions for route methods and driver binding behavior.

## Task 1: Update Structure Test

**Files:**
- Modify: `app/src/test/java/com/termux/app/CollaborationNavigationStructureTest.java`

- [ ] **Step 1: Change structure assertions**

Replace the old `collaborationDashboardExposesAgentserverLoomAndLocalRoles` expectations with assertions for:

```java
Assert.assertNotNull(findById(doc, "collaboration_workspace_card"));
Assert.assertNotNull(findById(doc, "collaboration_runtime_card"));
Assert.assertNotNull(findById(doc, "collaboration_loom_card"));
Assert.assertNotNull(findById(doc, "collaboration_android_capabilities_card"));
Assert.assertNotNull(findById(doc, "collaboration_update_area"));
Assert.assertNotNull(findById(doc, "btn_collaboration_workspace_settings"));
Assert.assertNotNull(findById(doc, "btn_collaboration_loom_settings"));
Assert.assertNotNull(findById(doc, "btn_collaboration_bind_driver"));
Assert.assertNotNull(findById(doc, "btn_collaboration_switch_role"));
```

- [ ] **Step 2: Run the test and confirm it fails before layout changes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.CollaborationNavigationStructureTest
```

Expected: FAIL because the new card IDs do not exist yet.

## Task 2: Update Collaboration Layout

**Files:**
- Modify: `app/src/main/res/layout/fragment_collaboration.xml`

- [ ] **Step 1: Replace top section text**

Use:

```xml
android:text="协作运行时"
```

and subtitle:

```xml
android:text="AgentServer 工作空间 · Loom 编排能力 · 本机手机能力"
```

- [ ] **Step 2: Add workspace card**

Create a `MaterialCardView` with id `@+id/collaboration_workspace_card`, title `AgentServer 工作空间`, summary id `@+id/collaboration_workspace_summary`, and button id `@+id/btn_collaboration_workspace_settings`.

- [ ] **Step 3: Add local runtime card**

Create a `MaterialCardView` with id `@+id/collaboration_runtime_card`, status ids `collaboration_provider_card_value`, `collaboration_driver_binding_status`, `collaboration_local_agent_status`, `collaboration_local_slave_status`, and buttons `btn_collaboration_switch_role`, `btn_collaboration_bind_driver`.

- [ ] **Step 4: Add Loom orchestration card**

Create a `MaterialCardView` with id `@+id/collaboration_loom_card`, summary id `@+id/collaboration_loom_summary`, and button id `@+id/btn_collaboration_loom_settings`.

- [ ] **Step 5: Add Android capability card**

Create a `MaterialCardView` with id `@+id/collaboration_android_capabilities_card` and summary id `@+id/collaboration_android_capabilities_summary`.

- [ ] **Step 6: Keep update area**

Keep `@+id/collaboration_update_area` and `@+id/collaboration_update_summary`, but change text to package-layer wording.

## Task 3: Update Collaboration Fragment

**Files:**
- Modify: `app/src/main/java/com/termux/app/CollaborationFragment.java`

- [ ] **Step 1: Update fields**

Replace `mAgentServerSummary` with `mWorkspaceSummary` and add `mAndroidCapabilitiesSummary`.

- [ ] **Step 2: Update view bindings**

Bind:

```java
mWorkspaceSummary = view.findViewById(R.id.collaboration_workspace_summary);
mAndroidCapabilitiesSummary = view.findViewById(R.id.collaboration_android_capabilities_summary);
```

- [ ] **Step 3: Update click listeners**

Use:

```java
view.findViewById(R.id.btn_collaboration_workspace_settings).setOnClickListener(...)
view.findViewById(R.id.btn_collaboration_loom_settings).setOnClickListener(...)
view.findViewById(R.id.collaboration_runtime_card).setOnClickListener(v -> showRoleDialog());
```

- [ ] **Step 4: Update summaries**

Workspace summary format:

```text
服务器：<url or 未配置>
工作空间：旧版 AgentServer 连接页中配置/授权
本机：<device or 本机设备>
```

Loom summary format:

```text
编排角色：<role>
Observer：<observerUrl>
Driver MCP：随 <provider> 配置
```

Android capability summary:

```text
无障碍 / 截图 / ADB / Android MCP 状态在设置与自动化页管理；本机作为 Slave 或 Connector 时依赖这些能力。
```

Update summary:

```text
安装层保持分包：Ubuntu 基础环境、AgentServer addon、Loom addon、Claude/Codex runtime 分开维护。
```

## Task 4: Verify

**Files:**
- Test: `app/src/test/java/com/termux/app/CollaborationNavigationStructureTest.java`

- [ ] **Step 1: Run focused test**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.CollaborationNavigationStructureTest
```

Expected: PASS.

- [ ] **Step 2: Run broader unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.*Collaboration* --tests com.termux.app.*Loom* --tests com.termux.app.AgentServerCommandBuilderTest
```

Expected: PASS.

- [ ] **Step 3: Assemble debug APK**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: PASS.

