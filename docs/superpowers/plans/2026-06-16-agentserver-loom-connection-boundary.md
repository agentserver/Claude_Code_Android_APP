# AgentServer Loom Connection Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate AgentServer workspace connection, Driver MCP binding, and Loom role runtime so role switching no longer causes repeated QR authorization.

**Architecture:** Add a focused `CollaborationConnectionState` helper for AgentServer/Driver/Loom state decisions, then make `CollaborationFragment` delegate boundary checks to it. Keep existing command builders and QR dialogs; only explicit AgentServer connect and explicit Driver binding may launch auth flows.

**Tech Stack:** Android Java fragments, SharedPreferences, JUnit structure tests, Gradle debug unit tests.

---

### Task 1: Add Connection Boundary Tests

**Files:**
- Modify: `app/src/test/java/com/termux/app/CollaborationNavigationStructureTest.java`
- Create: `app/src/test/java/com/termux/app/collab/CollaborationConnectionStateTest.java`

- [x] **Step 1: Write tests for source-level boundaries**

Add tests that assert:

```java
Assert.assertFalse(source.contains("switchRoleAndBind(String role)"));
Assert.assertTrue(source.contains("switchRoleOnly"));
Assert.assertTrue(source.contains("markDriverBindingStale"));
Assert.assertFalse(source.contains("switchProviderAndBind"));
Assert.assertTrue(source.contains("switchProviderAndMarkDriverStale"));
Assert.assertTrue(source.contains("Driver MCP 尚未绑定"));
```

- [x] **Step 2: Write pure state tests**

Create `CollaborationConnectionStateTest` with tests for:

```java
CollaborationConnectionState.computeDriverFingerprint(...);
CollaborationConnectionState.driverBindingStatus(...);
CollaborationConnectionState.canStartRole(...);
```

Expected cases:

- Missing saved fingerprint returns `missing`.
- Matching saved fingerprint and `valid` status returns `valid`.
- Changed workspace returns `stale`.
- Failed status returns `failed`.
- Observer can start without Driver.
- Slave cannot start unless Driver state is `valid`.

- [x] **Step 3: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.CollaborationNavigationStructureTest --tests com.termux.app.collab.CollaborationConnectionStateTest
```

Expected: fails because the helper class and source-level boundaries do not exist yet.

### Task 2: Add Collaboration Connection State Helper

**Files:**
- Create: `app/src/main/java/com/termux/app/collab/CollaborationConnectionState.java`

- [x] **Step 1: Implement pure state helpers**

Add constants:

```java
public static final String DRIVER_STATUS_MISSING = "missing";
public static final String DRIVER_STATUS_VALID = "valid";
public static final String DRIVER_STATUS_STALE = "stale";
public static final String DRIVER_STATUS_BINDING = "binding";
public static final String DRIVER_STATUS_FAILED = "failed";
```

Add methods:

```java
public static String computeDriverFingerprint(...);
public static String driverBindingStatus(String savedFingerprint, String currentFingerprint, String savedStatus);
public static boolean canStartRole(String role, String driverStatus);
```

- [x] **Step 2: Run pure state tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.collab.CollaborationConnectionStateTest
```

Expected: pass.

### Task 3: Update Collaboration Fragment Boundary Behavior

**Files:**
- Modify: `app/src/main/java/com/termux/app/CollaborationFragment.java`
- Modify: `app/src/test/java/com/termux/app/CollaborationNavigationStructureTest.java`

- [x] **Step 1: Remove role-switch auto binding**

Rename behavior to `switchRoleOnly(String role)`:

```java
private void switchRoleOnly(String role) {
    // save role, refresh dashboard, do not call bindDriverToCurrentAgent()
}
```

- [x] **Step 2: Replace provider auto binding**

Rename behavior to `switchProviderAndMarkDriverStale(AssistantProvider provider)`:

```java
private void switchProviderAndMarkDriverStale(AssistantProvider provider) {
    // save provider, mark binding stale, refresh dashboard
}
```

- [x] **Step 3: Gate Driver binding**

Before running `driver-agent register`:

- Require a non-empty AgentServer workspace identity.
- Skip command if Driver status is already `valid`.
- Set status to `binding` while running.
- Save status `valid` and fingerprint on exit 0.
- Save status `failed` on non-zero exit or exception.

- [x] **Step 4: Gate role startup**

Before starting `Slave`, require Driver state `valid`; if not valid show:

```text
Driver MCP 尚未绑定，请先点击“绑定 Driver”。
```

Observer startup remains allowed without Driver binding.

- [x] **Step 5: Run boundary tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.CollaborationNavigationStructureTest --tests com.termux.app.collab.CollaborationConnectionStateTest
```

Expected: pass.

### Task 4: Verify Existing Collaboration/Loom Surface

**Files:**
- Test only.

- [x] **Step 1: Run related unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.termux.app.*Collaboration* --tests com.termux.app.*Loom* --tests com.termux.app.AgentServerCommandBuilderTest
```

Expected: pass.

- [x] **Step 2: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: build success.

## Self Review

- Covers all accepted spec boundaries.
- Keeps AgentServer connection, Driver binding, and Loom role runtime separate.
- Does not introduce a backend protocol change.
- Uses TDD for new state behavior and source-level collaboration boundaries.
