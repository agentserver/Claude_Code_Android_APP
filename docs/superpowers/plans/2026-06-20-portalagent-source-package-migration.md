# PortalAgent Source Package Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 PortalAgent 产品代码逐步从 Termux 底座包中拆出，形成可发布应用更清晰的源码边界。

**Architecture:** 保持 `applicationId "com.portalagent"` 和 `namespace "com.termux"` 不变，先把无 Manifest 直接引用的产品逻辑包迁入 `com.portalagent.*`。Termux 入口、终端运行时和底座能力继续留在 `com.termux.*`，后续再逐步迁移 MCP、Provider、UI 和入口包装层。

**Tech Stack:** Android Java, Gradle, JUnit4, Android resource XML, existing Termux Android base.

---

### Task 1: Add Package Structure Guard

**Files:**
- Create: `app/src/test/java/com/termux/app/PortalAgentPackageStructureTest.java`

- [ ] **Step 1: Write the failing package structure test**

```java
package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PortalAgentPackageStructureTest {

    @Test
    public void phaseOneProductPackagesLiveUnderPortalAgentNamespace() throws Exception {
        assertPackageMoved("automation");
        assertPackageMoved("loom");
        assertPackageMoved("collab");
    }

    private static void assertPackageMoved(String packageName) throws Exception {
        File mainDir = resolveProjectFile("src/main/java/com/portalagent/" + packageName);
        File testDir = resolveProjectFile("src/test/java/com/portalagent/" + packageName);
        File oldMainDir = resolveProjectFile("src/main/java/com/termux/app/" + packageName);

        Assert.assertTrue("Missing PortalAgent main package: " + mainDir, mainDir.isDirectory());
        Assert.assertTrue("Missing PortalAgent test package: " + testDir, testDir.isDirectory());
        Assert.assertFalse("Old Termux product package should be removed: " + oldMainDir, oldMainDir.exists());

        List<File> files = new ArrayList<>();
        collectJavaFiles(mainDir, files);
        collectJavaFiles(testDir, files);
        Assert.assertFalse("Expected Java files in " + packageName, files.isEmpty());

        for (File file : files) {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Assert.assertTrue("Wrong package in " + file, source.contains("package com.portalagent." + packageName));
            Assert.assertFalse("Old package leaked in " + file, source.contains("package com.termux.app." + packageName));
        }
    }

    private static void collectJavaFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectJavaFiles(file, out);
            } else if (file.getName().endsWith(".java")) {
                out.add(file);
            }
        }
    }

    private static File resolveProjectFile(String relativePath) {
        File file = new File(relativePath);
        if (!file.exists()) {
            file = new File("app/" + relativePath);
        }
        return file;
    }
}
```

- [ ] **Step 2: Run test and verify it fails before migration**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

Expected before migration: `FAIL` with `Missing PortalAgent main package` for at least one Phase 1 package.

### Task 2: Move Phase 1 Product Packages

**Files:**
- Move: `app/src/main/java/com/termux/app/automation` to `app/src/main/java/com/portalagent/automation`
- Move: `app/src/main/java/com/termux/app/loom` to `app/src/main/java/com/portalagent/loom`
- Move: `app/src/main/java/com/termux/app/collab` to `app/src/main/java/com/portalagent/collab`
- Move: `app/src/test/java/com/termux/app/automation` to `app/src/test/java/com/portalagent/automation`
- Move: `app/src/test/java/com/termux/app/loom` to `app/src/test/java/com/portalagent/loom`
- Move: `app/src/test/java/com/termux/app/collab` to `app/src/test/java/com/portalagent/collab`

- [ ] **Step 1: Update Java package declarations**

Replace these package declarations:

```java
package com.termux.app.automation;
package com.termux.app.loom;
package com.termux.app.collab;
```

with:

```java
package com.portalagent.automation;
package com.portalagent.loom;
package com.portalagent.collab;
```

- [ ] **Step 2: Update imports in consumers**

Replace imports in these consumers:

```text
app/src/main/java/com/termux/app/AutomationRecipeAdapter.java
app/src/main/java/com/termux/app/AutomationSettingsFragment.java
app/src/main/java/com/termux/app/HomeFragment.java
app/src/main/java/com/termux/app/CollaborationFragment.java
app/src/main/java/com/termux/app/LoomFragment.java
app/src/main/java/com/termux/app/autotasks/AutoTaskCoordinator.java
app/src/main/java/com/termux/app/mcp/McpAccessibilityService.java
app/src/main/java/com/termux/app/mcp/McpHttpServer.java
app/src/main/java/com/termux/app/mcp/tools/LoomLocalSlavesTool.java
app/src/test/java/com/termux/app/mcp/tools/LoomLocalSlavesToolTest.java
```

Use the new imports:

```java
import com.portalagent.automation.ActionRecipe;
import com.portalagent.automation.AutomationRuntime;
import com.portalagent.automation.ScreenFingerprint;
import com.portalagent.automation.ToolTraceStore;
import com.portalagent.collab.CollaborationConnectionState;
import com.portalagent.loom.LoomCommandBuilder;
import com.portalagent.loom.LoomSlaveRegistry;
```

- [ ] **Step 3: Run package guard**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

Expected after migration: `BUILD SUCCESSFUL`.

### Task 3: Tighten Direct-Directory Guard

**Files:**
- Modify: `app/src/test/java/com/termux/app/PortalAgentPackageStructureTest.java`

- [ ] **Step 1: Add a guard against accidental nested package directories**

Add this assertion inside `assertPackageMoved` after the directory existence checks:

```java
File accidentalNestedDir = new File(mainDir, packageName);
Assert.assertFalse("Accidental nested package directory should not exist: " + accidentalNestedDir,
    accidentalNestedDir.exists());
```

- [ ] **Step 2: Run test and verify current bad structure fails if present**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

Expected with `com/portalagent/automation/automation`: `FAIL` with `Accidental nested package directory should not exist`.

- [ ] **Step 3: Move files up one level if the nested directory exists**

For `automation`, the correct final layout is:

```text
app/src/main/java/com/portalagent/automation/ActionRecipe.java
app/src/main/java/com/portalagent/automation/ActionStep.java
app/src/main/java/com/portalagent/automation/AndroidMcpActionRunner.java
```

The incorrect nested layout must be removed:

```text
app/src/main/java/com/portalagent/automation/automation
```

- [ ] **Step 4: Re-run package guard**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.PortalAgentPackageStructureTest
```

Expected after fixing the tree: `BUILD SUCCESSFUL`.

### Task 4: Verify Phase 1 Compile and Unit Tests

**Files:**
- No source edits unless tests expose import or package errors.

- [ ] **Step 1: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.portalagent.automation.* --tests com.portalagent.loom.* --tests com.portalagent.collab.* --tests com.termux.app.PortalAgentPackageStructureTest --tests com.termux.app.CollaborationLayoutTest
```

Expected: `BUILD SUCCESSFUL`. If Gradle test filtering rejects wildcards, run the full debug unit test task in Step 2.

- [ ] **Step 2: Run full debug unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/portal-agent_apt-android-7-debug_universal.apk`.

### Task 5: Document Published Source Boundaries

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add Android source boundary note**

Add this note to the implementation section:

```markdown
### Android 源码边界

PortalAgent 的 APK 包名是 `com.portalagent`。当前 Android `namespace` 暂保留为 `com.termux`，用于兼容 Termux 底座、Manifest 相对类名、资源 `R` 引用和环境部署逻辑。

源码按两层维护：

- `com.termux.*`：Termux 终端底座、入口 Activity/Application/Service、bootstrap 与兼容层。
- `com.portalagent.*`：PortalAgent 产品能力，包括自动化、Loom/协作运行时，后续会继续迁移 MCP、Provider、聊天和产品 UI。
```

- [ ] **Step 2: Check README renders with the new section in the intended order**

Run:

```powershell
rg -n "Android 源码边界|首次配置|功能说明" README.md
```

Expected: all three headings are found, and `Android 源码边界` appears in the implementation-related area rather than before user-facing setup.

### Task 6: Prepare Later Phase Specs

**Files:**
- No code edits in Phase 1.

- [ ] **Step 1: Keep MCP migration separate**

Do not move `app/src/main/java/com/termux/app/mcp` in Phase 1 because `AndroidManifest.xml` directly references MCP services.

- [ ] **Step 2: Keep entry and namespace separate**

Do not change this Gradle configuration in Phase 1:

```gradle
namespace "com.termux"
applicationId "com.portalagent"
```

Expected: `applicationId` remains the installed app identity, while `namespace` remains a compatibility layer until Manifest and entry classes are migrated in a dedicated phase.
