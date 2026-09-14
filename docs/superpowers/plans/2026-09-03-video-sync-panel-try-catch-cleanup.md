# VideoSyncPanel Try-Catch Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the three unnecessary silent `Throwable` catches from `VideoSyncPanel` while preserving its programmatic control-state synchronization.

**Architecture:** This is an isolated UI cleanup. A source-level policy test first makes the forbidden silent catch pattern observable and red; the production change then replaces each wrapper with its direct Swing assignment. Existing panel behavior remains unchanged except that unexpected programming failures are no longer suppressed.

**Tech Stack:** Kotlin 2.2, Swing, JUnit 5, Maven Surefire.

**Spec:** `docs/try-catch-cleanup-audit.md`

## Global Constraints

- Do not catch `Throwable` around ordinary Swing state assignments.
- Preserve `VideoSyncPanel`'s public setter names and their normal UI state effects.
- Do not modify the user's existing `PointsListPanel` or `PointsListPanelTest` changes.
- Write the test before production code and observe its expected failure.
- Run the focused `VideoSyncPanelTest` after each test/code stage.

---

### Task 1: Remove silent state-assignment catches from VideoSyncPanel

**Files:**
- Modify: `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/VideoSyncPanel.kt:107-141`
- Modify: `src/test/kotlin/org/litvin/ui/tabs/scoring/VideoSyncPanelTest.kt`
- Modify: `docs/try-catch-cleanup-audit.md`

**Interfaces:**
- Consumes: `VideoSyncPanel.setNoPointEnabled(Boolean)`, `setNoPointSelected(Boolean)`, and `setFrameStepEnabled(Boolean)`.
- Produces: the same three public setters, each directly applying the requested state and no longer suppressing unexpected failures.

- [ ] **Step 1: Write the failing policy and behavior tests**

  Add imports for `java.nio.file.Files`, `java.nio.file.Path`, `javax.swing.JCheckBox`, `javax.swing.JToggleButton`, `kotlin.test.assertFalse`, and `kotlin.test.assertTrue` to `VideoSyncPanelTest.kt`. Add these tests inside `VideoSyncPanelTest`:

  ```kotlin
  @Test
  fun programmaticSettersContainNoSilentThrowableCatch() {
      val source = Files.readString(
          Path.of("src/main/kotlin/org/litvin/ui/tabs/scoring/ui/VideoSyncPanel.kt"),
      )

      assertFalse(source.contains("catch (_: Throwable)"))
  }

  @Test
  fun programmaticSettersSynchronizeTheirControls() {
      SwingUtilities.invokeAndWait {
          val panel = VideoSyncPanel(noOpVideoActions()) {}
          val noPoint = panel.descendants().filterIsInstance<JToggleButton>()
              .first { it.name == "no-point" }
          val frameStep = panel.descendants().filterIsInstance<JCheckBox>()
              .first { it.text == "Seek frame-by-frame [F]" }

          panel.setNoPointEnabled(false)
          panel.setNoPointSelected(true)
          panel.setFrameStepEnabled(true)

          assertFalse(noPoint.isEnabled)
          assertTrue(noPoint.isSelected)
          assertTrue(frameStep.isSelected)
      }
  }
  ```

  Add this test-only `VideoPlayerActions` helper in the same class:

  ```kotlin
  private fun noOpVideoActions() = object : VideoPlayerActions {
      override fun playPause() = Unit
      override fun seekBy(milliseconds: Long) = Unit
      override fun setSpeedMultiplier(multiplier: Float) = Unit
      override fun setFrameStepEnabled(enabled: Boolean) = Unit
  }
  ```

- [ ] **Step 2: Run the focused test and verify it fails for the policy violation**

  Run:

  ```powershell
  mvn -Dtest=VideoSyncPanelTest test
  ```

  Expected: `programmaticSettersContainNoSilentThrowableCatch` fails because `VideoSyncPanel.kt` still contains `catch (_: Throwable)`.

- [ ] **Step 3: Remove only the three redundant wrappers**

  Replace the three function bodies with direct assignments:

  ```kotlin
  fun setNoPointEnabled(enabled: Boolean) {
      noPointBtn.isEnabled = enabled
  }

  fun setNoPointSelected(selected: Boolean) {
      noPointBtn.model.isSelected = selected
  }

  fun setFrameStepEnabled(enabled: Boolean) {
      frameStepCheckbox.isSelected = enabled
  }
  ```

- [ ] **Step 4: Run the focused test and verify it passes**

  Run:

  ```powershell
  mvn -Dtest=VideoSyncPanelTest test
  ```

  Expected: all `VideoSyncPanelTest` tests pass, including the no-silent-catch policy check and control-state assertions.

- [ ] **Step 5: Update the audit tracker and verify the source is clean**

  Change the `VideoSyncPanel.kt` tracker row in `docs/try-catch-cleanup-audit.md` to:

  ```markdown
  | `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/VideoSyncPanel.kt` | Changed | Removed the three silent `Throwable` wrappers at 108–111, 115–118, and 137–140; `VideoSyncPanelTest` verifies direct setter synchronization and the absence of the forbidden catch pattern. |
  ```

  Run:

  ```powershell
  rg -n "catch\s*\(_:\s*Throwable\)" src/main/kotlin/org/litvin/ui/tabs/scoring/ui/VideoSyncPanel.kt
  ```

  Expected: no output and exit code 1.

- [ ] **Step 6: Commit the isolated cleanup**

  ```powershell
  git add src/main/kotlin/org/litvin/ui/tabs/scoring/ui/VideoSyncPanel.kt src/test/kotlin/org/litvin/ui/tabs/scoring/VideoSyncPanelTest.kt docs/try-catch-cleanup-audit.md docs/superpowers/plans/2026-09-03-video-sync-panel-try-catch-cleanup.md
  git commit -m "refactor: remove silent video sync catches"
  ```
