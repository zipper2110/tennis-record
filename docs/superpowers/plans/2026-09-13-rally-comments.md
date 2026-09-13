# Rally Comments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add project-scoped, time-pinned text comments to Rallies and optionally burn them into exported video.

**Architecture:** Extend `edl.json` with an additive comment collection and project-local comment defaults. Keep comment lifecycle separate from point-boundary lifecycle, join them only at the Rallies presentation boundary, and calculate comment overlay spans from the exact kept intervals used by export. Emit comments and scoreboard events into one ASS script so FFmpeg needs only one subtitle filter.

**Tech Stack:** Kotlin/JVM, Swing, Jackson Kotlin, JUnit 5/kotlin.test, FFmpeg ASS subtitles, Maven.

**Spec:** `docs/superpowers/specs/2026-09-13-rally-comments-design.md`

## Global Constraints

- Keep `EdlV1.version` at `1`; missing comment fields in legacy `edl.json` files must deserialize safely.
- Comment IDs are positive project-local integers, allocated monotonically and never reused after deletion.
- Persist `#FFFFFF` as the initial project default; changing any comment color updates only that project's next-comment default.
- A comment may overlap rallies, comments, and idle gaps; it must never be subjected to rally interval-overlap validation.
- The comment start is always a source-video time. In trimmed export, include it only if the start lies in an actually kept half-open rally interval; its duration proceeds continuously in output time across concatenated rallies.
- Keep the first version limited to fixed lower-third rendering: centered, large, wrapped, comment-colored text over a half-transparent dark backing. Do not add in-player comment rendering or per-comment layout controls.
- The **Include comments** export checkbox is independent of Scoreboard and unchecked by default.
- Preserve unrelated working-tree changes, especially the existing Timeline and Points List edits, while making feature changes.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `src/main/kotlin/org/litvin/EdlIO.kt` | Comment persistence models, legacy defaults, numeric-ID repair, and JSON round trips. |
| `src/main/kotlin/org/litvin/CommentDispatcher.kt` | Validation and mutable lifecycle for comments and their project default color. |
| `src/main/kotlin/org/litvin/ScoreboardTimeline.kt` | Shared overlay span types; add the comment-specific output span. |
| `src/main/kotlin/org/litvin/export/ExportPlanner.kt` | Map source-pinned comments into full-video or trimmed output time and snapshot them in a job. |
| `src/main/kotlin/org/litvin/RenderQueue.kt` | Carry the comment overlay snapshot and write a combined ASS file when either overlay is enabled. |
| `src/main/kotlin/org/litvin/RenderOverlayScript.kt` | Isolate combined ASS-file creation so comments-only rendering is unit-testable without FFmpeg. |
| `src/main/kotlin/org/litvin/AssOverlayWriter.kt` | Write scoreboards plus safe, wrapped lower-third comment ASS events. |
| `src/main/kotlin/org/litvin/CompletedRendersStore.kt` and `src/main/kotlin/org/litvin/export/CompletedRendersRepository.kt` | Persist `includeComments` with completed renders. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/MarkupContracts.kt` | Typed Rallies list DTOs and comment actions/patches. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/EditCommentDialog.kt` | Create/edit modal for text, start, duration, and color. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/PointsCardsView.kt` | Mixed chronological **Rallies & events** cards, including direct comment color changes. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/SwingTimelineComponent.kt` | Comments track, full-height marker bars, right-attached numbered boxes, lane layout, and hit testing. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/TransportControls.kt` | Add the Rallies **Add comment** action. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/SwingMarkupPanel.kt` | Join point/comment state, selection, autosave, cards, timeline, and modal wiring. |
| `src/main/kotlin/org/litvin/ui/tabs/export/SwingExportPanel.kt` | Export checkbox and comment snapshot request wiring. |
| `src/main/kotlin/org/litvin/ui/tabs/export/RenderQueueList.kt` and `src/main/kotlin/org/litvin/ui/tabs/export/CompletedRendersList.kt` | Display both enabled overlay types in queue/history summaries. |
| `src/test/kotlin/org/litvin/EdlIOTest.kt`, `CommentDispatcherTest.kt`, `OverlayAssWriterTest.kt`, `CompletedRendersStoreTest.kt` | Persistence, lifecycle, subtitle, and render-history unit coverage. |
| `src/test/kotlin/org/litvin/export/ExportPlannerTest.kt` | Source-to-output comment mapping and immutable job snapshot coverage. |
| `src/test/kotlin/org/litvin/ui/tabs/markup/ui/SwingTimelineComponentTest.kt`, `EventsCardsViewTest.kt` | Marker geometry, lanes, hit testing, and mixed card rendering tests. |
| `src/test/kotlin/org/litvin/ui/flow/screens/RalliesScreen.kt`, `ExportScreen.kt`, `EditingAcrossTabsUiFlowIT.kt`, `ExportConfigurationUiFlowIT.kt` | End-user persistence and export-configuration UI flows. |

## Task 1: Persist and Manage Project Comments

**Files:**
- Modify: `src/main/kotlin/org/litvin/EdlIO.kt`
- Create: `src/main/kotlin/org/litvin/CommentDispatcher.kt`
- Modify: `src/test/kotlin/org/litvin/EdlIOTest.kt`
- Create: `src/test/kotlin/org/litvin/CommentDispatcherTest.kt`

**Interfaces:**
- Produces `CommentV1(id: Int, startMs: Int, durationMs: Int, text: String, colorHex: String)` and `CommentDefaultsV1(colorHex: String = "#FFFFFF")` in `org.litvin.markup`.
- Produces `EdlV1.comments`, `EdlV1.commentDefaults`, and `EdlV1.nextCommentId` with additive defaults.
- Produces `CommentPatch`, `CommentState`, and `CommentDispatcher` in `org.litvin.markup.components`; later UI tasks consume these types.

- [ ] **Step 1: Write failing EDL persistence tests**

Add the following cases to `EdlIOTest` before changing production code:

```kotlin
@Test
fun roundTrip_preservesCommentsAndProjectColorDefault() {
    val edl = EdlV1(
        comments = listOf(CommentV1(7, 1_250, 3_500, "Ball was in", "#22AAFF")),
        commentDefaults = CommentDefaultsV1("#22AAFF"),
        nextCommentId = 8,
    )

    EdlIO.write(edlPath, edl)

    assertEquals(edl, EdlIO.read(edlPath))
}

@Test
fun read_legacyEdl_suppliesEmptyCommentsWhiteDefaultAndFirstId() {
    Files.writeString(edlPath, """{"version":1,"points":[]}""")

    val read = EdlIO.read(edlPath)

    assertEquals(emptyList(), read.comments)
    assertEquals("#FFFFFF", read.commentDefaults.colorHex)
    assertEquals(1, read.nextCommentId)
}
```

- [ ] **Step 2: Run the EDL test to verify it fails**

Run: `mvn -q -Dtest=EdlIOTest test`

Expected: compilation failure because `CommentV1`, `CommentDefaultsV1`, and the new `EdlV1` properties do not yet exist.

- [ ] **Step 3: Implement the additive EDL schema and ID repair**

Add the models and defaults in `EdlIO.kt`, then normalize comments as part of both `read` and `write`. Preserve the first valid positive ID in file order, assign replacement IDs starting above the largest retained ID, normalize accepted colors to uppercase `#RRGGBB`, and set `nextCommentId` to at least one more than every comment ID.

```kotlin
data class CommentV1(
    val id: Int,
    val startMs: Int,
    val durationMs: Int,
    val text: String,
    val colorHex: String = "#FFFFFF",
)

data class CommentDefaultsV1(val colorHex: String = "#FFFFFF")

data class EdlV1(
    val points: List<PointV1> = emptyList(),
    val comments: List<CommentV1> = emptyList(),
    val commentDefaults: CommentDefaultsV1 = CommentDefaultsV1(),
    val nextCommentId: Int = 1,
    val version: Int = 1,
)
```

Keep the existing point UUID repair untouched. Add `EdlIO.normalizeComments(edl: EdlV1): EdlV1` and use it in `read` and `write` so repaired state is retained by the next autosave.

- [ ] **Step 4: Add failing lifecycle tests for the new dispatcher**

Create `CommentDispatcherTest` with creation, deletion, update, and default-color tests:

```kotlin
@Test
fun create_allocatesMonotonicIdsAndUsesProjectDefaultColor() {
    val dispatcher = CommentDispatcher()
    dispatcher.load(CommentState(emptyList(), CommentDefaultsV1("#AA5500"), nextCommentId = 4))

    val first = dispatcher.create(startMs = 1_000, durationMs = 2_000, text = "In")
    dispatcher.delete(4)
    val second = dispatcher.create(startMs = 2_000, durationMs = 2_000, text = "Out")

    assertEquals(CommentV1(4, 1_000, 2_000, "In", "#AA5500"), first)
    assertEquals(5, second?.id)
}

@Test
fun updateColor_changesOnlyThisProjectsNextCommentDefault() {
    val dispatcher = CommentDispatcher()
    dispatcher.load(CommentState(listOf(CommentV1(1, 0, 1_000, "Serve", "#FFFFFF")), CommentDefaultsV1(), 2))

    dispatcher.update(1, CommentPatch(colorHex = "#12ab34"))

    assertEquals("#12AB34", dispatcher.state().comments.single().colorHex)
    assertEquals("#12AB34", dispatcher.state().defaults.colorHex)
}
```

Also assert that blank text, a negative start, a zero/negative duration, an unknown ID, and malformed color each fail without mutating state.

- [ ] **Step 5: Run the dispatcher test to verify it fails**

Run: `mvn -q -Dtest=CommentDispatcherTest test`

Expected: compilation failure because `CommentDispatcher`, `CommentPatch`, and `CommentState` do not yet exist.

- [ ] **Step 6: Implement the minimal comment dispatcher**

Create `CommentDispatcher.kt` beside the existing `MarkupDispatcher.kt`, with the same package (`org.litvin.markup.components`) and a narrow state API:

```kotlin
data class CommentPatch(
    val startMs: Int? = null,
    val durationMs: Int? = null,
    val text: String? = null,
    val colorHex: String? = null,
)

data class CommentState(
    val comments: List<CommentV1>,
    val defaults: CommentDefaultsV1,
    val nextCommentId: Int,
)

class CommentDispatcher {
    var onCommentsChanged: (() -> Unit)? = null
    fun load(state: CommentState)
    fun state(): CommentState
    fun create(startMs: Int, durationMs: Int, text: String, colorHex: String? = null): CommentV1?
    fun update(id: Int, patch: CommentPatch): Boolean
    fun delete(id: Int): Boolean
}
```

Return `null`/`false` and expose a consumable user hint, matching `MarkupDispatcher`’s existing pattern, when validation fails. `create` uses the state default when `colorHex` is null. A successful color update changes the state default. Sort stored comments by `(startMs, id)` without renumbering IDs.

- [ ] **Step 7: Run persistence and lifecycle tests**

Run: `mvn -q -Dtest=EdlIOTest,CommentDispatcherTest test`

Expected: PASS.

- [ ] **Step 8: Commit the persistence boundary**

```bash
git add src/main/kotlin/org/litvin/EdlIO.kt src/main/kotlin/org/litvin/CommentDispatcher.kt src/test/kotlin/org/litvin/EdlIOTest.kt src/test/kotlin/org/litvin/CommentDispatcherTest.kt
git commit -m "feat: persist project rally comments"
```

## Task 2: Map Pinned Comments into Export Time

**Files:**
- Modify: `src/main/kotlin/org/litvin/ScoreboardTimeline.kt`
- Modify: `src/main/kotlin/org/litvin/export/ExportPlanner.kt`
- Modify: `src/main/kotlin/org/litvin/RenderQueue.kt`
- Modify: `src/test/kotlin/org/litvin/export/ExportPlannerTest.kt`

**Interfaces:**
- Consumes `CommentV1` and the `keptPoints` selection from Task 1.
- Produces `CommentOverlaySpan(id: Int, startMs: Long, endMs: Long, text: String, colorHex: String)` in `org.litvin`.
- Adds `includeComments: Boolean` and `commentOverlayTimeline: List<CommentOverlaySpan>` to `ExportRenderPlanRequest`, `ExportRenderPlan`, and the later `RenderJob` construction.

- [ ] **Step 1: Write failing mapping tests**

Add these tests to `ExportPlannerTest`:

```kotlin
@Test
fun trimmedExport_includesCommentStartingInKeptPointAndCarriesDurationAcrossGap() {
    val edl = EdlV1(
        points = listOf(point("p1", 1_000, 4_000), point("p2", 10_000, 14_000)),
        comments = listOf(CommentV1(3, 3_000, 5_000, "Line call", "#FFFFFF")),
    )

    val plan = buildPlan(edl, idleTrim = true, favoriteOnly = false, includeComments = true)

    assertEquals(listOf(CommentOverlaySpan(3, 2_000, 7_000, "Line call", "#FFFFFF")), plan.commentOverlayTimeline)
}

@Test
fun trimmedExport_omitsCommentBeginningInRemovedGapOrExcludedFavorite() {
    val edl = EdlV1(
        points = listOf(point("fav", 0, 1_000, favorite = true), point("other", 2_000, 3_000)),
        comments = listOf(
            CommentV1(1, 1_000, 500, "Boundary", "#FFFFFF"),
            CommentV1(2, 1_500, 500, "Gap", "#FFFFFF"),
            CommentV1(3, 2_100, 500, "Non-favorite", "#FFFFFF"),
        ),
    )

    val plan = buildPlan(edl, idleTrim = true, favoriteOnly = true, includeComments = true)

    assertEquals(emptyList(), plan.commentOverlayTimeline)
}
```

Add a full-video case asserting `CommentV1(5, 6_000, 750, ...)` maps to output `[6_000, 6_750)`, plus a disabled-checkbox case asserting that the plan and job snapshot contain no comment spans.

- [ ] **Step 2: Run mapping tests to verify they fail**

Run: `mvn -q -Dtest=ExportPlannerTest test`

Expected: compilation failure because `CommentOverlaySpan`, `includeComments`, and `commentOverlayTimeline` do not exist.

- [ ] **Step 3: Add the comment overlay value type and planner mapping**

In `ScoreboardTimeline.kt`, add the immutable render value type:

```kotlin
data class CommentOverlaySpan(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val colorHex: String,
)
```

In `ExportPlanner`, add a pure function:

```kotlin
fun buildCommentOverlayTimeline(
    comments: List<CommentV1>,
    keptPoints: List<PointV1>,
    idleTrim: Boolean,
): List<CommentOverlaySpan>
```

For `idleTrim == false`, return comments ordered by `(startMs, id)` with `[startMs, startMs + durationMs)`. For trim, accumulate the output duration of each kept point. Include a comment only when its `startMs` is inside a kept point with `point.startMs <= startMs && startMs < point.endMs`; set its output start to accumulated duration plus `startMs - point.startMs`, and end to output start plus duration. Do not split a span at a source gap. In both modes, ignore invalid persisted comment values defensively.

Thread `includeComments` through `ExportRenderPlanRequest` and only calculate the timeline when it is true. Store the resulting timeline in both `ExportRenderPlan` and `RenderJob`. Add `includeComments: Boolean = false` and `commentOverlayTimeline: List<CommentOverlaySpan> = emptyList()` to `RenderJob` now; Task 3 will consume those snapshots when it writes the ASS file.

- [ ] **Step 4: Run mapping tests**

Run: `mvn -q -Dtest=ExportPlannerTest test`

Expected: PASS, including existing scoreboard/favorite tests.

- [ ] **Step 5: Commit export-time mapping**

```bash
git add src/main/kotlin/org/litvin/ScoreboardTimeline.kt src/main/kotlin/org/litvin/export/ExportPlanner.kt src/main/kotlin/org/litvin/RenderQueue.kt src/test/kotlin/org/litvin/export/ExportPlannerTest.kt
git commit -m "feat: map comments into export time"
```

## Task 3: Burn and Track Comment Overlays

**Files:**
- Modify: `src/main/kotlin/org/litvin/RenderQueue.kt`
- Create: `src/main/kotlin/org/litvin/RenderOverlayScript.kt`
- Modify: `src/main/kotlin/org/litvin/AssOverlayWriter.kt`
- Modify: `src/main/kotlin/org/litvin/CompletedRendersStore.kt`
- Modify: `src/main/kotlin/org/litvin/export/CompletedRendersRepository.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/export/RenderQueueList.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/export/CompletedRendersList.kt`
- Modify: `src/test/kotlin/org/litvin/OverlayAssWriterTest.kt`
- Modify: `src/test/kotlin/org/litvin/CompletedRendersStoreTest.kt`
- Create: `src/test/kotlin/org/litvin/RenderOverlayScriptTest.kt`

**Interfaces:**
- Consumes `CommentOverlaySpan` and `ExportRenderPlan.job.commentOverlayTimeline` from Task 2.
- Produces `RenderJob.includeComments` and `CompletedRender.includeComments` for queue/history UIs.
- Extends `AssOverlayWriter.write` to accept both `scoreboardSpans` and `commentSpans` and create one valid ASS script when either list is non-empty.

- [ ] **Step 1: Write failing subtitle writer tests**

Add a test to `OverlayAssWriterTest` that renders a comment containing special characters and line breaks:

```kotlin
@Test
fun writesWrappedLowerThirdCommentAlongsideScoreboard() {
    AssOverlayWriter.write(
        file = tmp,
        scoreboardSpans = listOf(scoreSpan()),
        commentSpans = listOf(CommentOverlaySpan(12, 1_000, 3_000, "IN {review}\\nGreat rally", "#22AAFF")),
        outWidth = 1920,
        outHeight = 1080,
    )

    val ass = tmp.readText()
    assertTrue(ass.contains("Style: CommentText"))
    assertTrue(ass.contains("Style: CommentBackdrop"))
    assertTrue(ass.contains("\\\\N"))
    assertTrue(ass.contains("\\\\{"))
    assertTrue(ass.contains("22AAFF"))
}
```

Add a second test with a long sentence containing spaces and assert the output contains at least one ASS `\\N` wrap inserted at a word boundary. Retain the existing scoreboard-only assertions.

- [ ] **Step 2: Run ASS tests to verify they fail**

Run: `mvn -q -Dtest=OverlayAssWriterTest test`

Expected: compilation failure because the writer does not yet accept comment spans or define comment styles.

- [ ] **Step 3: Implement combined ASS writing**

Change the writer signature to keep existing scoreboard callers source-compatible through defaults:

```kotlin
fun write(
    file: File,
    scoreboardSpans: List<OverlaySpan> = emptyList(),
    commentSpans: List<CommentOverlaySpan> = emptyList(),
    outWidth: Int,
    outHeight: Int,
    style: ScoreboardStyle = ScoreboardStyles.default(),
)
```

Write the Script Info and Events headers once. Keep the existing scoreboard layers unchanged. Add `CommentText` and `CommentBackdrop` styles, then emit each comment with a higher layer than the scoreboard. Use `\\an2` centered alignment, a lower-third `\\pos(playResX / 2, lowerThirdY)`, scaled large font, and ASS vector drawing for a half-transparent rectangle behind the wrapped lines. Escape `\\`, `{`, `}`, and normalize CRLF/LF into `\\N`. Wrap only at whitespace using a maximum line width derived from output width; preserve user-provided line breaks.

- [ ] **Step 4: Write failing job/history propagation tests**

Extend `CompletedRendersStoreTest`:

```kotlin
assertTrue(item.includeScoreboard)
assertTrue(item.includeComments)
```

Build the fixture `RenderJob` with `includeComments = true` and one `CommentOverlaySpan`. Create `RenderOverlayScriptTest` to assert a comments-only job produces an ASS file and a no-overlay job returns no file:

```kotlin
@Test
fun commentsOnlyJobWritesAssFile() {
    val job = renderJob(includeScoreboard = false, includeComments = true)

    val assFile = RenderOverlayScript.writeFor(job, File(tempDir, "render.part"))

    assertNotNull(assFile)
    assertTrue(assFile.readText().contains("CommentText"))
}
```

- [ ] **Step 5: Run propagation tests to verify they fail**

Run: `mvn -q -Dtest=CompletedRendersStoreTest,OverlayAssWriterTest test`

Expected: compilation or assertion failure because comment inclusion is not yet retained by jobs/history/queue.

- [ ] **Step 6: Implement job, queue, and history propagation**

Add `val includeComments: Boolean = false` to `CompletedRender` and update `FileCompletedRendersRepository.append` to copy it. Create this isolated adapter:

```kotlin
object RenderOverlayScript {
    fun writeFor(job: RenderJob, partOutput: File): File?
}
```

It returns null when both requested overlay timelines are empty; otherwise it writes `${partOutput.absolutePath}.ass` through the combined writer and returns that file. In `RenderQueue`, replace its inline scoreboard-only ASS block with this adapter and preserve the current warning-and-render-without-subtitles behavior if it throws. Update queue and completed-render formatters to show `Scoreboard`, `Comments`, both, or `No overlays` without changing output paths or trim summaries.

- [ ] **Step 7: Run subtitle, queue, and history tests**

Run: `mvn -q -Dtest=OverlayAssWriterTest,RenderOverlayScriptTest,CompletedRendersStoreTest,RenderServiceTest test`

Expected: PASS.

- [ ] **Step 8: Commit the overlay delivery boundary**

```bash
git add src/main/kotlin/org/litvin/RenderQueue.kt src/main/kotlin/org/litvin/RenderOverlayScript.kt src/main/kotlin/org/litvin/AssOverlayWriter.kt src/main/kotlin/org/litvin/CompletedRendersStore.kt src/main/kotlin/org/litvin/export/CompletedRendersRepository.kt src/main/kotlin/org/litvin/ui/tabs/export/RenderQueueList.kt src/main/kotlin/org/litvin/ui/tabs/export/CompletedRendersList.kt src/test/kotlin/org/litvin/OverlayAssWriterTest.kt src/test/kotlin/org/litvin/RenderOverlayScriptTest.kt src/test/kotlin/org/litvin/CompletedRendersStoreTest.kt
git commit -m "feat: render comment overlays"
```

## Task 4: Render Mixed Rallies and Comment Events

**Files:**
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/MarkupContracts.kt`
- Create: `src/main/kotlin/org/litvin/ui/tabs/markup/ui/EditCommentDialog.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/ui/PointsCardsView.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/ui/SwingTimelineComponent.kt`
- Modify: `src/test/kotlin/org/litvin/ui/tabs/markup/ui/SwingTimelineComponentTest.kt`
- Create: `src/test/kotlin/org/litvin/ui/tabs/markup/ui/EventsCardsViewTest.kt`

**Interfaces:**
- Consumes `CommentV1`/`CommentPatch` from Task 1.
- Produces `RallyEventDto`, `CommentDto`, and comment action methods consumed by `SwingMarkupPanel` in Task 5.
- Produces `SwingTimelineComponent` constructor parameters `commentsProvider: () -> List<CommentV1>` and `onCommentSelected: (Int) -> Unit`.

- [ ] **Step 1: Write failing contract and card tests**

Create `EventsCardsViewTest` to state the user-visible list contract:

```kotlin
@Test
fun eventsAreOrderedBySourceTimeAndCommentCardShowsStableNumberAndPreview() {
    val state = MarkupViewState(
        isPlaying = false,
        currentTimeMs = 0,
        selectedVisualIndex = null,
        pendingDraftStartMs = null,
        events = listOf(
            CommentDto(4, 500, 2_000, "Call was in", "#FFFFFF"),
            RallyEventDto(PointDto("rally-a", 1_000, 2_000, null)),
        ),
        autosave = AutosaveState(false, null),
    )

    view.setState(state)

    assertEquals(listOf("Comment #4", "#1"), view.visibleTitles())
    assertTrue(view.visibleText().contains("Call was in"))
}
```

Expose `visibleTitles()` and `visibleText()` as `internal` test helpers rather than inspecting Swing private fields. Add a test that invokes the comment swatch action and asserts `MarkupActions.updateCommentColor(4, "#123456")` receives the normalized color.

- [ ] **Step 2: Run card tests to verify they fail**

Run: `mvn -q -Dtest=EventsCardsViewTest test`

Expected: compilation failure because mixed event DTOs and comment card rendering do not exist.

- [ ] **Step 3: Implement typed presentation contracts and the comment editor**

Replace the point-only list field with a sealed event type while keeping `PointDto` for rally-specific controls:

```kotlin
sealed interface MarkupEventDto { val startMs: Long }

data class RallyEventDto(val point: PointDto) : MarkupEventDto {
    override val startMs get() = point.startMs
}

data class CommentDto(
    val id: Int,
    override val startMs: Long,
    val durationMs: Long,
    val text: String,
    val colorHex: String,
) : MarkupEventDto
```

Make `MarkupViewState.events: List<MarkupEventDto>` the list source. Add `createComment`, `editComment`, `deleteComment`, and `updateCommentColor` to `MarkupActions`; retain and correctly implement the existing `editPoint` action rather than leaving its current card callback as a no-op.

Create `EditCommentDialog.showCreate(parent, initialStartMs, defaultColor, actions)` and `EditCommentDialog.showEdit(parent, comment, actions)`. Use `JTextArea` in a scroll pane for text, `Timecode` for the start field, a decimal seconds field converted with `BigDecimal(...).movePointRight(3).setScale(0, HALF_UP)`, and a `JColorChooser`-backed swatch. Show a validation error and leave the dialog open when any field is invalid.

- [ ] **Step 4: Implement mixed chronological cards**

Keep `PointsCardsView`’s file name for a focused incremental refactor, but change its heading ownership to the container and render `state.events` sorted by `(startMs, stable secondary key)`. Preserve rally favorite/edit/delete UI. Add a comment card with title `Comment #${comment.id}`, formatted start/duration, wrapped preview, direct color swatch, edit, and delete actions. The color swatch must invoke `updateCommentColor`; it must not open the full edit dialog. Make the view constructor accept `chooseCommentColor: (Component, String) -> String?`, defaulting to `JColorChooser.showDialog`; pass a deterministic lambda from the card test. Maintain the current hover action pattern and selection-by-visual-index behavior.

- [ ] **Step 5: Add failing timeline geometry and hit-testing tests**

Extend `SwingTimelineComponentTest` with a 10-second, 200-pixel timeline and comment `CommentV1(12, 5_000, 1_000, "In", "#FFFFFF")`:

```kotlin
@Test
fun clickingRightAttachedCommentBoxSelectsCommentAndSeeksToItsStart() {
    val selected = mutableListOf<Int>()
    val seeks = mutableListOf<Long>()
    val timeline = timelineWithComment(
        comment = CommentV1(12, 5_000, 1_000, "In", "#FFFFFF"),
        onCommentSelected = { selected += it },
        onSeek = { seeks += it },
    )

    timeline.dispatchEvent(mouseEvent(timeline, MouseEvent.MOUSE_PRESSED, x = 104, y = timeline.commentTrackCenterY()))

    assertEquals(listOf(12), selected)
    assertEquals(listOf(5_000L), seeks)
}
```

Add a collision test with comments at 5,000 and 5,100 ms and assert `commentLaneCountForTest() == 2`, label boxes are non-overlapping, and every marker bar spans from the VIDEO track top through the COMMENTS track bottom.

- [ ] **Step 6: Run timeline tests to verify they fail**

Run: `mvn -q -Dtest=SwingTimelineComponentTest test`

Expected: compilation failure because the timeline has no comments provider, marker geometry, label boxes, or comment callback.

- [ ] **Step 7: Implement COMMENTS track, bars, lanes, and hit testing**

Add a third track beneath MARKS. Compute `CommentMarkerLayout(id, lineX, boxBounds, lane)` in a pure internal layout function. Reserve `lineX + 3` as the box's left edge so each `#N` box is visibly attached on the right of the full-height colored line. Greedily assign the earliest lane whose prior box ends before the new box begins; calculate preferred height from the lane count. Draw every marker line from VIDEO’s top to the bottom of COMMENTS, then draw the right-attached label boxes over the COMMENTS track. Test box bounds before generic seek or MARKS hit testing so a click invokes `onCommentSelected(id)` and `onSeekRequested(startMs)` exactly once.

- [ ] **Step 8: Run cards and timeline tests**

Run: `mvn -q -Dtest=EventsCardsViewTest,SwingTimelineComponentTest test`

Expected: PASS.

- [ ] **Step 9: Commit the Rallies presentation components**

```bash
git add src/main/kotlin/org/litvin/ui/tabs/markup/MarkupContracts.kt src/main/kotlin/org/litvin/ui/tabs/markup/ui/EditCommentDialog.kt src/main/kotlin/org/litvin/ui/tabs/markup/ui/PointsCardsView.kt src/main/kotlin/org/litvin/ui/tabs/markup/ui/SwingTimelineComponent.kt src/test/kotlin/org/litvin/ui/tabs/markup/ui/EventsCardsViewTest.kt src/test/kotlin/org/litvin/ui/tabs/markup/ui/SwingTimelineComponentTest.kt
git commit -m "feat: show comments in rallies timeline"
```

## Task 5: Wire Comments into the Rallies Tab and Autosave

**Files:**
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/ui/TransportControls.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/SwingMarkupPanel.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/RalliesScreen.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/EditingAcrossTabsUiFlowIT.kt`

**Interfaces:**
- Consumes `CommentDispatcher`, mixed `MarkupEventDto`, and timeline/card actions from Tasks 1 and 4.
- Produces project load/save behavior that writes `EdlV1(points, comments, commentDefaults, nextCommentId)` and UI component names for flow tests.

- [ ] **Step 1: Write the failing persistence flow**

Add a screen helper and an integration assertion that creates a comment in one project, changes its color, navigates away/back, and confirms the saved EDL state:

```kotlin
fun assertCommentPersisted(projectDirectory: Path, id: Int, startMs: Int, durationMs: Int, color: String) {
    application.eventually("comment #$id to persist") {
        val edl = EdlIO.readForProjectDir(projectDirectory.toString())
        assertEquals(CommentV1(id, startMs, durationMs, "Ball was in", color), edl.comments.single())
        assertEquals(color, edl.commentDefaults.colorHex)
    }
}
```

In `EditingAcrossTabsUiFlowIT`, create `Comment #1` at 1,250 ms for 2 seconds with `#00FF00`, leave Rallies, return, and assert that the EDL comment and project default color remain intact. Use the app’s scripted dialog mechanism to submit the modal rather than calling dispatcher internals.

- [ ] **Step 2: Run the Rallies flow to verify it fails**

Run: `mvn -q -Dtest=EditingAcrossTabsUiFlowIT test`

Expected: compile or UI lookup failure because the Add comment action and comment dialog controls do not exist.

- [ ] **Step 3: Add the control-strip action and component names**

In `TransportControls`, add `JButton("Add comment")` named `rallies-add-comment` beside the point Start/End controls. Add `MarkupActions.addCommentAtPlayhead()`; bind the button to it. Name the dialog controls `rallies-comment-text`, `rallies-comment-start`, `rallies-comment-duration`, `rallies-comment-color`, and `rallies-comment-save` so the UI flow can drive them deterministically.

- [ ] **Step 4: Integrate state and actions in SwingMarkupPanel**

Instantiate a `CommentDispatcher`. On `setProjectManifest` and project refresh, load points into `MarkupDispatcher` and comments/defaults/next ID into `CommentDispatcher`. On either dispatcher change, compose a chronological event list, update the **Rallies & events** header, repaint the timeline, and schedule the existing debounced autosave. The autosave must write all five EDL fields.

Implement actions as follows:

```kotlin
override fun addCommentAtPlayhead() = EditCommentDialog.showCreate(
    parent = this@SwingMarkupPanel,
    initialStartMs = player.currentTimeMs(),
    defaultColor = commentDispatcher.state().defaults.colorHex,
    actions = cardsActions,
)

override fun updateCommentColor(id: Int, colorHex: String) {
    if (commentDispatcher.update(id, CommentPatch(colorHex = colorHex))) scheduleAutosave()
}
```

After create/update/delete, select the relevant visual event by its stable comment ID and scroll it into view. Update `jumpToSelected`, selection restoration, auto-activation, delete-key behavior, and `toggleFavoriteSelectedPoint` so rally-only operations ignore comment cards rather than indexing the wrong event. Wire `SwingTimelineComponent.onCommentSelected` to find the event index and scroll/select it.

- [ ] **Step 5: Run the Rallies flow**

Run: `mvn -q -Dtest=EditingAcrossTabsUiFlowIT test`

Expected: PASS with the comment persisted after tab navigation.

- [ ] **Step 6: Run focused markup regression tests**

Run: `mvn -q -Dtest=MarkupDispatcherTest,MarkupKeybindingsTest,SwingTimelineComponentTest,EventsCardsViewTest,EditingAcrossTabsUiFlowIT test`

Expected: PASS; existing C/V rally marking, favorite hotkey, and normal timeline seeking remain unchanged.

- [ ] **Step 7: Commit the Rallies integration**

```bash
git add src/main/kotlin/org/litvin/ui/tabs/markup/ui/TransportControls.kt src/main/kotlin/org/litvin/ui/tabs/markup/SwingMarkupPanel.kt src/test/kotlin/org/litvin/ui/flow/screens/RalliesScreen.kt src/test/kotlin/org/litvin/ui/flow/EditingAcrossTabsUiFlowIT.kt
git commit -m "feat: manage comments from rallies"
```

## Task 6: Expose Comment Rendering in Export

**Files:**
- Modify: `src/main/kotlin/org/litvin/ui/tabs/export/SwingExportPanel.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/ExportScreen.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/ExportConfigurationUiFlowIT.kt`

**Interfaces:**
- Consumes `ExportRenderPlanRequest.includeComments` and `RenderJob.includeComments` from Tasks 2 and 3.
- Produces a checkbox named `export-comments` and queue snapshots containing the selected comment overlay timeline.

- [ ] **Step 1: Write the failing export configuration flow**

Extend `ExportScreen.configure` with `comments: Boolean`, set the future `export-comments` checkbox, and add these assertions to `ExportConfigurationUiFlowIT`:

```kotlin
assertEquals(true, job.includeScoreboard)
assertEquals(true, job.includeComments)
assertEquals(listOf(1), job.commentOverlayTimeline.map { it.id })
```

Build the fixture EDL with a comment that begins inside `point-1` so it is eligible for the already-trimmed export.

- [ ] **Step 2: Run export flow to verify it fails**

Run: `mvn -q -Dtest=ExportConfigurationUiFlowIT test`

Expected: compilation or component-lookup failure because `comments` and `export-comments` do not yet exist.

- [ ] **Step 3: Add and wire the checkbox**

In `SwingExportPanel`, define:

```kotlin
private val commentsCheck = JCheckBox("Include comments", false).apply {
    name = "export-comments"
    toolTipText = "Burn Rallies comments into the video as centered lower-third text."
}
```

Style it with `UiStyles.styleCheckBox`, place it directly after the Scoreboard checkbox, and pass `commentsCheck.isSelected` to `ExportRenderPlanRequest(includeComments = ...)` in `onInitializeRender`. Do not disable or auto-select it based on comment count; the zero-comment case remains a valid no-op export selection.

- [ ] **Step 4: Run export flow and planner regression tests**

Run: `mvn -q -Dtest=ExportConfigurationUiFlowIT,ExportPlannerTest test`

Expected: PASS; the job records comments independently of Scoreboard and selected trimmed comments are mapped correctly.

- [ ] **Step 5: Commit the export control**

```bash
git add src/main/kotlin/org/litvin/ui/tabs/export/SwingExportPanel.kt src/test/kotlin/org/litvin/ui/flow/screens/ExportScreen.kt src/test/kotlin/org/litvin/ui/flow/ExportConfigurationUiFlowIT.kt
git commit -m "feat: add export comments option"
```

## Task 7: Verify the Complete Feature and Update the Spec Status

**Files:**
- Modify: `docs/superpowers/specs/2026-09-13-rally-comments-design.md`

**Interfaces:**
- Consumes the completed implementation from Tasks 1–6.
- Produces evidence that persistence, Rallies UI, timeline behavior, and export snapshots satisfy the approved design.

- [ ] **Step 1: Run the targeted feature suite**

Run:

```bash
mvn -q -Dtest=EdlIOTest,CommentDispatcherTest,ExportPlannerTest,OverlayAssWriterTest,CompletedRendersStoreTest,SwingTimelineComponentTest,EventsCardsViewTest,EditingAcrossTabsUiFlowIT,ExportConfigurationUiFlowIT test
```

Expected: PASS.

- [ ] **Step 2: Run the full Maven test suite**

Run: `mvn test`

Expected: PASS. If it fails, preserve the failure output and diagnose whether it is caused by the comments feature or a pre-existing dirty-worktree change before modifying code.

- [ ] **Step 3: Perform a manual Swing smoke pass**

Run: `powershell -ExecutionPolicy Bypass -File qa/windows/Run-UiSmoke.ps1`

Expected: the existing smoke test completes without regression. Then manually verify a project with two kept rallies and a gap: create a comment near the end of rally one, confirm the `[#N]` box is immediately right of its full-height timeline line, select it by clicking the box, export with idle trim and Include comments, and confirm the lower-third remains visible at the start of rally two.

- [ ] **Step 4: Mark the spec as implemented and record verification evidence**

Append this exact section to the approved spec after the commands pass:

```markdown
## Implementation Verification

- Targeted feature suite: PASS (`<date and command output reference>`)
- Full Maven suite: PASS (`<date and command output reference>`)
- Windows UI smoke: PASS (`<date and artifact location>`)
```

Replace each angle-bracketed value with the actual date/output or artifact path from this run; do not claim a pass without that evidence.

- [ ] **Step 5: Commit verification documentation**

```bash
git add docs/superpowers/specs/2026-09-13-rally-comments-design.md
git commit -m "docs: verify rally comments feature"
```

## Plan Self-Review

**Spec coverage:** Tasks 1 and 5 cover project-local persistence, numeric identifiers, the project default color, creation/edit/delete, and unified event list. Task 4 covers the three-track timeline, full-height bars, right-attached `#N` boxes, collision lanes, and click behavior. Tasks 2, 3, and 6 cover source-pinned eligibility, continuous output duration, independent export selection, ASS appearance, job snapshots, and completed-render metadata. Task 7 requires automated and manual evidence.

**Placeholder scan:** The plan has no deferred implementation markers. The only angle-bracketed values are explicitly required to be replaced with real verification evidence in Task 7.

**Type consistency:** `CommentV1` is persisted in Task 1, mapped to `CommentOverlaySpan` in Task 2, carried by `RenderJob`/ASS in Task 3, and exposed as `CommentDto` in Tasks 4–5. `includeComments` begins in the export request, reaches the job/history boundary, and is set by the named `export-comments` UI control.
