# PortalAgent MCP Package Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android MCP 能力层从 `com.termux.app.mcp` 迁移到 `com.portalagent.mcp`，让手机能力成为 PortalAgent 产品层代码。

**Architecture:** 保持 `namespace "com.termux"` 和 `applicationId "com.portalagent"` 不变。MCP Java 代码迁入 `com.portalagent.mcp`，Manifest 中的截图服务和无障碍服务改用绝对类名，Termux 宿主层通过 imports 调用 PortalAgent MCP。

**Tech Stack:** Android Java, AndroidManifest.xml, AccessibilityService, MediaProjection foreground service, JUnit4, Gradle.

---

### Task 1: Add Phase 2 Package and Manifest Guards

**Files:**
- Modify: `app/src/test/java/com/termux/app/PortalAgentPackageStructureTest.java`

- [ ] **Step 1: Add MCP package guard**

Add this test method:

```java
@Test
public void phaseTwoMcpPackageLivesUnderPortalAgentNamespace() throws Exception {
    assertPackageMoved("mcp");
}
```

- [ ] **Step 2: Add Manifest service guard**

Add this test method:

```java
@Test
public void phaseTwoManifestUsesPortalAgentMcpServiceClassNames() throws Exception {
    String manifest = readProjectFile("src/main/AndroidManifest.xml");
    Assert.assertTrue(manifest.contains("com.portalagent.mcp.ScreenCaptureService"));
    Assert.assertTrue(manifest.contains("com.portalagent.mcp.McpAccessibilityService"));
    Assert.assertFalse(manifest.contains(".app.mcp.ScreenCaptureService"));
    Assert.assertFalse(manifest.contains(".app.mcp.McpAccessibilityService"));
}
```

Add this helper:

```java
private static String readProjectFile(String relativePath) throws Exception {
    File file = resolveProjectFile(relativePath);
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
}
```

- [ ] **Step 3: Run the guard and verify it fails before migration**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

Expected before migration: `FAIL` because `src/main/java/com/portalagent/mcp` does not exist and Manifest still references `.app.mcp.*`.

### Task 2: Move MCP Main and Test Packages

**Files:**
- Move: `app/src/main/java/com/termux/app/mcp` to `app/src/main/java/com/portalagent/mcp`
- Move: `app/src/test/java/com/termux/app/mcp` to `app/src/test/java/com/portalagent/mcp`

- [ ] **Step 1: Update package declarations**

Replace:

```java
package com.termux.app.mcp;
package com.termux.app.mcp.tools;
```

with:

```java
package com.portalagent.mcp;
package com.portalagent.mcp.tools;
```

- [ ] **Step 2: Update imports and fully qualified references**

Replace:

```java
com.termux.app.mcp
```

with:

```java
com.portalagent.mcp
```

Important consumers:

```text
app/src/main/java/com/termux/app/TermuxActivity.java
app/src/main/java/com/termux/app/HomeFragment.java
app/src/main/java/com/termux/app/autotasks/AutoTaskCoordinator.java
app/src/main/java/com/portalagent/automation/AndroidMcpActionRunner.java
app/src/test/java/com/portalagent/mcp/AndroidMcpPermissionStateTest.java
app/src/test/java/com/portalagent/mcp/tools/AdbToolTest.java
```

### Task 3: Update Manifest Service Declarations

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Use absolute PortalAgent MCP service class names**

Replace:

```xml
android:name=".app.mcp.ScreenCaptureService"
android:name=".app.mcp.McpAccessibilityService"
```

with:

```xml
android:name="com.portalagent.mcp.ScreenCaptureService"
android:name="com.portalagent.mcp.McpAccessibilityService"
```

- [ ] **Step 2: Update accessibility detection tests**

In `app/src/test/java/com/portalagent/mcp/AndroidMcpPermissionStateTest.java`, use the installed package and class pairing:

```java
"com.portalagent/com.portalagent.mcp.McpAccessibilityService"
"com.portalagent/.mcp.McpAccessibilityService"
"com.portalagent", "com.portalagent.mcp.McpAccessibilityService"
```

### Task 4: Verify Compile and Package Guards

**Files:**
- No source edits unless verification exposes stale imports.

- [ ] **Step 1: Run MCP package guard**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

Expected after migration: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run MCP focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.portalagent.mcp.* --tests com.portalagent.mcp.tools.*
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run full debug unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 5: Build and Install on Xreal

**Files:**
- No source edits unless build exposes stale Manifest/class references.

- [ ] **Step 1: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/portal-agent_apt-android-7-debug_universal.apk`.

- [ ] **Step 2: Install to Xreal**

Run:

```powershell
adb -s R1LM45S11867DC install -r app\build\outputs\apk\debug\portal-agent_apt-android-7-debug_universal.apk
```

Expected: `Success`.

### Task 6: Stop Before Entry and Namespace Migration

**Files:**
- No code edits in this phase.

- [ ] **Step 1: Keep Termux entry classes in place**

Do not move these classes in Phase 2:

```text
app/src/main/java/com/termux/app/TermuxActivity.java
app/src/main/java/com/termux/app/TermuxApplication.java
app/src/main/java/com/termux/app/TermuxService.java
```

- [ ] **Step 2: Keep Gradle namespace in place**

Do not change:

```gradle
namespace "com.termux"
```

Expected: product capability code moves, while Android resource and Manifest entry compatibility remain stable.
