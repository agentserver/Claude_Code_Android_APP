# PortalAgent Product Layer Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将剩余 PortalAgent 产品层源码从 `com.termux.app` 迁出，使 `com.termux.*` 只保留 Termux 底座、终端和兼容层。

**Architecture:** 保持 `applicationId "com.portalagent"`，暂不改 Android `namespace "com.termux"`。PortalAgent 产品代码按职责迁入 `com.portalagent.provider`、`com.portalagent.chat`、`com.portalagent.session`、`com.portalagent.keys`、`com.portalagent.settings`、`com.portalagent.agentserver`、`com.portalagent.tasks`、`com.portalagent.apitools`、`com.portalagent.setup`、`com.portalagent.ui.*`、`com.portalagent.util` 和 `com.portalagent.activities`。

**Tech Stack:** Android Java, AndroidManifest.xml, Fragment UI, Android services, JUnit4, Gradle.

---

### Task 1: Add Remaining Product Layer Guards

**Files:**
- Modify: `app/src/test/java/com/termux/app/PortalAgentPackageStructureTest.java`

- [ ] **Step 1: Add package guards for remaining product packages**

Add assertions that these directories exist and contain Java files whose package starts with the expected package:

```text
app/src/main/java/com/portalagent/provider        -> package com.portalagent.provider
app/src/main/java/com/portalagent/chat            -> package com.portalagent.chat
app/src/main/java/com/portalagent/session         -> package com.portalagent.session
app/src/main/java/com/portalagent/keys            -> package com.portalagent.keys
app/src/main/java/com/portalagent/settings        -> package com.portalagent.settings
app/src/main/java/com/portalagent/agentserver     -> package com.portalagent.agentserver
app/src/main/java/com/portalagent/tasks           -> package com.portalagent.tasks
app/src/main/java/com/portalagent/apitools        -> package com.portalagent.apitools
app/src/main/java/com/portalagent/setup           -> package com.portalagent.setup
app/src/main/java/com/portalagent/ui/home         -> package com.portalagent.ui.home
app/src/main/java/com/portalagent/ui/collaboration -> package com.portalagent.ui.collaboration
app/src/main/java/com/portalagent/ui/status       -> package com.portalagent.ui.status
app/src/main/java/com/portalagent/util            -> package com.portalagent.util
app/src/main/java/com/portalagent/activities      -> package com.portalagent.activities
```

- [ ] **Step 2: Add guards that old product files are gone**

Assert these old files no longer exist under `app/src/main/java/com/termux/app`:

```text
AgentServerCommandBuilder.java
AgentServerFragment.java
AgentTask.java
AgentTaskDetailFragment.java
AgentTaskListAdapter.java
AgentTaskStore.java
ApiKeyAdapter.java
ApiKeyFragment.java
ApiKeyStore.java
ApiToolsController.java
ApiToolsFragment.java
AppSettingsFragment.java
AssistantProvider.java
AutomationRecipeAdapter.java
AutomationSettingsFragment.java
ChatAdapter.java
ChatMessage.java
ChatTranscriptStore.java
ChatTurnOrdering.java
ClaudeStreamSession.java
CodexExecSession.java
CollaborationFragment.java
DrawerFileAdapter.java
FloatingStatusService.java
HomeFragment.java
LoomFragment.java
ProviderConfigManager.java
ProviderEnvironmentWriter.java
ProviderProfile.java
ProviderSettingsStore.java
QrCodeUtil.java
SessionAdapter.java
SessionStore.java
SettingsHubFragment.java
SettingsPreferenceStyler.java
UploadStore.java
WorkspaceAccessSettingsFragment.java
WorkspaceAccessSettingsStore.java
```

Assert `app/src/main/java/com/termux/app/autotasks` no longer exists.

- [ ] **Step 3: Run the guard and verify it fails before migration**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

Expected before migration: `FAIL` with missing `com.portalagent.provider` and old product files still present.

### Task 2: Move Product Root Classes

**Files:**
- Move root classes from `app/src/main/java/com/termux/app` into `app/src/main/java/com/portalagent/*`
- Move matching tests from `app/src/test/java/com/termux/app` into `app/src/test/java/com/portalagent/*`

- [ ] **Step 1: Move provider classes**

Move:

```text
AssistantProvider.java
ProviderProfile.java
ProviderSettingsStore.java
ProviderEnvironmentWriter.java
ProviderConfigManager.java
```

to:

```text
app/src/main/java/com/portalagent/provider
```

with:

```java
package com.portalagent.provider;
```

- [ ] **Step 2: Move chat and session classes**

Move:

```text
ChatAdapter.java
ChatMessage.java
ChatTranscriptStore.java
ChatTurnOrdering.java
ClaudeStreamSession.java
CodexExecSession.java
SessionAdapter.java
SessionStore.java
DrawerFileAdapter.java
```

to `com.portalagent.chat`, `com.portalagent.session`, or `com.portalagent.ui.home` according to file responsibility.

- [ ] **Step 3: Move UI/product feature classes**

Move:

```text
HomeFragment.java                     -> com.portalagent.ui.home
CollaborationFragment.java            -> com.portalagent.ui.collaboration
LoomFragment.java                     -> com.portalagent.ui.collaboration
AgentServerCommandBuilder.java        -> com.portalagent.agentserver
AgentServerFragment.java              -> com.portalagent.agentserver
ApiKeyAdapter.java/ApiKeyFragment.java/ApiKeyStore.java -> com.portalagent.keys
ApiToolsController.java/ApiToolsFragment.java -> com.portalagent.apitools
AppSettingsFragment.java/SettingsHubFragment.java/SettingsPreferenceStyler.java -> com.portalagent.settings
WorkspaceAccessSettingsFragment.java/WorkspaceAccessSettingsStore.java -> com.portalagent.settings
AutomationRecipeAdapter.java/AutomationSettingsFragment.java -> com.portalagent.automation.ui
AgentTask*.java/UploadStore.java -> com.portalagent.tasks
QrCodeUtil.java -> com.portalagent.util
FloatingStatusService.java -> com.portalagent.ui.status
```

- [ ] **Step 4: Update imports and package declarations**

Replace old references to `com.termux.app.<ProductClass>` with the new `com.portalagent.*` package names. Keep imports to `com.termux.R`, `com.termux.shared.*`, and `com.termux.app.TermuxActivity` where the moved code still depends on the Termux host.

### Task 3: Move Setup and Product Activities

**Files:**
- Move: `app/src/main/java/com/termux/app/autotasks` to `app/src/main/java/com/portalagent/setup`
- Move: `app/src/test/java/com/termux/app/autotasks` to `app/src/test/java/com/portalagent/setup`
- Move: `app/src/main/java/com/termux/app/activities/SettingsActivity.java` to `app/src/main/java/com/portalagent/activities/SettingsActivity.java`
- Move: `app/src/main/java/com/termux/app/activities/ApiToolsActivity.java` to `app/src/main/java/com/portalagent/activities/ApiToolsActivity.java`

- [ ] **Step 1: Update setup package**

Use:

```java
package com.portalagent.setup;
```

for former `autotasks` classes and tests.

- [ ] **Step 2: Update activities package and Manifest**

Use:

```java
package com.portalagent.activities;
```

for `SettingsActivity` and `ApiToolsActivity`.

In `AndroidManifest.xml`, replace:

```xml
android:name=".app.activities.SettingsActivity"
android:name=".app.activities.ApiToolsActivity"
```

with:

```xml
android:name="com.portalagent.activities.SettingsActivity"
android:name="com.portalagent.activities.ApiToolsActivity"
```

Keep `HelpActivity` in `com.termux.app.activities`.

### Task 4: Add PortalAgent Entry Wrappers

**Files:**
- Create: `app/src/main/java/com/portalagent/PortalAgentActivity.java`
- Create: `app/src/main/java/com/portalagent/PortalAgentApplication.java`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add wrapper activity**

```java
package com.portalagent;

import com.termux.app.TermuxActivity;

public class PortalAgentActivity extends TermuxActivity {
}
```

- [ ] **Step 2: Add wrapper application**

```java
package com.portalagent;

import com.termux.app.TermuxApplication;

public class PortalAgentApplication extends TermuxApplication {
}
```

- [ ] **Step 3: Update Manifest entry declarations**

Use:

```xml
android:name="com.portalagent.PortalAgentApplication"
android:name="com.portalagent.PortalAgentActivity"
android:targetActivity="com.portalagent.PortalAgentActivity"
```

for the application, launcher activity, and `.HomeActivity` alias.

Keep Termux services and file receiver components as `com.termux.app.*` absolute or namespace-relative names until a separate Termux base migration exists.

### Task 5: Verify and Device Test

**Files:**
- No source edits unless verification exposes stale imports or package-private API boundaries.

- [ ] **Step 1: Run package guards**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

- [ ] **Step 2: Run full debug unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

- [ ] **Step 3: Build APK**

```powershell
.\gradlew.bat :app:assembleDebug
```

- [ ] **Step 4: Install and smoke test on Xreal**

```powershell
adb -s R1LM45S11867DC install -r app\build\outputs\apk\debug\portal-agent_apt-android-7-debug_universal.apk
adb -s R1LM45S11867DC shell monkey -p com.portalagent 1
```

Then use `dumpsys window` and logcat checks to confirm the app launches without crashing. If safe, also verify the main tab navigation by checking current resumed activity and absence of fatal exceptions.

### Task 6: Stop Before Namespace Change

**Files:**
- No namespace edits in this migration pass.

- [ ] **Step 1: Keep Gradle namespace stable**

Do not change:

```gradle
namespace "com.termux"
```

Expected: PortalAgent product source and entry wrappers are migrated, while resource `R` package and Termux base compatibility remain stable for this release line.
