package org.litvin.ui.tabs.scoring

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.tabs.scoring.ui.ScoreSettings
import org.litvin.ui.tabs.scoring.ui.ScoreSettingsEditor
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.prefs.AbstractPreferences
import javax.swing.AbstractButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwingScoringPanelScoreSettingsTest {
    private val scheduler = ScheduledThreadPoolExecutor(1)
    private val adjustments = AdjustmentsSession(scheduler, 60_000)
    private val projectDir: File = Files.createTempDirectory("scoring-settings-").toFile()
    private val manifestPath = File(projectDir, "project.trproj").absolutePath
    private val dialogs = RecordingDialogs()
    private var panel: SwingScoringPanel? = null
    private var frame: JFrame? = null

    init {
        ManifestIO.write(
            manifestPath,
            ProjectManifestV1(
                id = "123e4567-e89b-42d3-a456-426614174000",
                name = "Match",
                createdAt = "2026-09-24T00:00:00Z",
                lastOpenedAt = "2026-09-24T00:00:00Z",
                sourceVideo = File(projectDir, "missing.mp4").absolutePath,
            ),
        )
        EdlIO.writeForProjectDir(projectDir.absolutePath, EdlV1(listOf(PointV1(id = "p1", startMs = 1_000, endMs = 2_000))))
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeAndWait {
            panel?.close()
            frame?.dispose()
        }
        assertEquals(emptyList(), dialogs.errors)
        scheduler.shutdownNow()
        projectDir.deleteRecursively()
    }

    @Test
    fun newProjectStartsWithTheDefaultStyleAndShowsScoreSettingsOnlyOnTheFirstVisit() {
        val defaultStyle = ScoreboardSettingsV1(style = ScoreboardStyleId.LED_BOARD, title = "Club")
        val editor = RecordingEditor { current ->
            current.copy(player1Name = "Dima", player2Name = "Vova", rules = MatchRulesV1(deuce = DeuceRule.NO_AD))
        }

        SwingUtilities.invokeAndWait {
            panel = createPanel(FixedDefaults(defaultStyle), editor).apply { setProjectManifest(manifestPath) }
        }
        val created = ScoreIO.readForProjectDir(projectDir.absolutePath)
        assertEquals(defaultStyle, created.scoreboard, "A new project uses the default scoreboard style")
        assertEquals(false, created.scoreSettingsReviewed)

        activateAndFlush()
        assertEquals(1, editor.calls.size, "The first visit opens the score settings")
        assertEquals("Player 1", editor.calls.single().player1Name)

        val saved = ScoreIO.readForProjectDir(projectDir.absolutePath)
        assertEquals("Dima", saved.player1Name)
        assertEquals("Vova", saved.player2Name)
        assertEquals(DeuceRule.NO_AD, saved.rules.deuce)
        assertTrue(saved.scoreSettingsReviewed)

        SwingUtilities.invokeAndWait { panel!!.onDeactivated() }
        activateAndFlush()
        assertEquals(1, editor.calls.size, "The next visits do not open the score settings")
    }

    @Test
    fun cancelOnTheFirstVisitStillMarksTheSettingsAsShown() {
        val editor = RecordingEditor { null }
        SwingUtilities.invokeAndWait {
            panel = createPanel(ScoreboardStyleDefaults.NONE, editor).apply { setProjectManifest(manifestPath) }
        }

        activateAndFlush()

        assertEquals(1, editor.calls.size)
        val saved = ScoreIO.readForProjectDir(projectDir.absolutePath)
        assertTrue(saved.scoreSettingsReviewed)
        assertEquals("Player 1", saved.player1Name)
    }

    @Test
    fun hintShowsAfterEachAutomaticDialogUntilTheUserClosesIt() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val hint = MemoryHint()
        val editor = RecordingEditor { null }
        SwingUtilities.invokeAndWait {
            panel = createPanel(ScoreboardStyleDefaults.NONE, editor, hint).apply { setProjectManifest(manifestPath) }
            frame = JFrame().apply {
                contentPane.add(panel!!)
                setSize(1200, 800)
                isVisible = true
            }
        }

        activateAndFlush()
        assertEquals(1, editor.calls.size)
        assertTrue(balloonShown(), "The balloon shows when the automatic dialog closes")

        SwingUtilities.invokeAndWait { panel!!.onDeactivated() }
        assertFalse(balloonShown(), "Leaving the tab removes the balloon")
        assertFalse(hint.isDismissed(), "Leaving the tab does not close the hint for good")

        // The next project with no reviewed settings shows the dialog and the balloon again
        reopenWithUnreviewedSettings()
        activateAndFlush()
        assertEquals(2, editor.calls.size)
        assertTrue(balloonShown())

        SwingUtilities.invokeAndWait { (find(frame!!.layeredPane, "hint-balloon-close") as AbstractButton).doClick() }
        assertFalse(balloonShown())
        assertTrue(hint.isDismissed(), "The close button closes the hint for good")

        SwingUtilities.invokeAndWait { panel!!.onDeactivated() }
        reopenWithUnreviewedSettings()
        activateAndFlush()
        assertEquals(3, editor.calls.size)
        assertFalse(balloonShown(), "A closed hint does not show again")
    }

    @Test
    fun preferencesKeepTheClosedHint() {
        val preferences = MemoryPreferences()
        assertFalse(PreferencesScoreSettingsHint(preferences).isDismissed())

        PreferencesScoreSettingsHint(preferences).dismiss()

        assertTrue(PreferencesScoreSettingsHint(preferences).isDismissed())
    }

    @Test
    fun existingProjectKeepsItsOwnStyle() {
        val projectStyle = ScoreboardSettingsV1(style = ScoreboardStyleId.RETRO)
        ScoreIO.writeForProjectDir(projectDir.absolutePath, ScoreV1(scoreboard = projectStyle, scoreSettingsReviewed = true))
        val editor = RecordingEditor { null }

        SwingUtilities.invokeAndWait {
            panel = createPanel(FixedDefaults(ScoreboardSettingsV1(style = ScoreboardStyleId.TILES)), editor)
                .apply { setProjectManifest(manifestPath) }
        }
        activateAndFlush()

        assertEquals(projectStyle, ScoreIO.readForProjectDir(projectDir.absolutePath).scoreboard)
        assertEquals(0, editor.calls.size)
    }

    @Test
    fun preferencesKeepTheDefaultStyle() {
        val defaults = PreferencesScoreboardStyleDefaults(MemoryPreferences())
        assertNull(defaults.load())

        val style = ScoreboardSettingsV1(style = ScoreboardStyleId.NIGHT_SESSION, showAppCredit = false, sizePercent = 120)
        defaults.save(style)

        assertEquals(style, defaults.load())
    }

    private fun createPanel(
        defaults: ScoreboardStyleDefaults,
        editor: ScoreSettingsEditor,
        hint: ScoreSettingsHint = ScoreSettingsHint.NONE,
    ) = SwingScoringPanel(FakePlayer(), adjustments, dialogs, defaults, editor, hint)

    private fun reopenWithUnreviewedSettings() {
        ScoreIO.writeForProjectDir(projectDir.absolutePath, ScoreV1(scoreSettingsReviewed = false))
        SwingUtilities.invokeAndWait { panel!!.setProjectManifest(manifestPath) }
    }

    private fun balloonShown(): Boolean {
        var shown = false
        SwingUtilities.invokeAndWait { shown = find(frame!!.layeredPane, "hint-balloon")?.isShowing == true }
        return shown
    }

    private fun find(root: Container, name: String): Component? {
        for (child in root.components) {
            if (child.name == name) return child
            if (child is Container) find(child, name)?.let { return it }
        }
        return null
    }

    /** Activates the tab, then runs the events that the activation queued (the score settings prompt). */
    private fun activateAndFlush() {
        SwingUtilities.invokeAndWait { panel!!.onActivated() }
        SwingUtilities.invokeAndWait { }
        SwingUtilities.invokeAndWait { }
    }

    private class RecordingEditor(private val answer: (ScoreSettings) -> ScoreSettings?) : ScoreSettingsEditor {
        val calls = mutableListOf<ScoreSettings>()
        override fun edit(parent: Component, current: ScoreSettings): ScoreSettings? {
            calls += current
            return answer(current)
        }
    }

    private class MemoryHint : ScoreSettingsHint {
        private var dismissed = false
        override fun isDismissed() = dismissed
        override fun dismiss() {
            dismissed = true
        }
    }

    private class FixedDefaults(private val style: ScoreboardSettingsV1) : ScoreboardStyleDefaults {
        override fun load(): ScoreboardSettingsV1 = style
        override fun save(settings: ScoreboardSettingsV1) = Unit
    }

    private class RecordingDialogs : UserDialogService {
        val errors = mutableListOf<String>()
        override fun showInfo(parent: Component?, message: String, title: String) = Unit
        override fun showError(parent: Component?, message: String, title: String) {
            errors += "$title: $message"
        }
        override fun confirm(parent: Component?, message: String, title: String) = false
    }

    private class MemoryPreferences : AbstractPreferences(null, "") {
        private val values = mutableMapOf<String, String>()
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun removeNodeSpi() = Unit
        override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi(): Array<String> = emptyArray()
        override fun childSpi(name: String) = MemoryPreferences()
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }

    private class FakePlayer : SwingMediaPlayer {
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        override fun load(file: File) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seek(ms: Long) = Unit
        override fun setRate(rate: Float) = Unit
        override fun status() = PlayerStatus.PAUSED
        override fun currentTimeMs() = 0L
        override fun totalDurationMs() = 0L
        override fun isAdjustSupported() = true
        override fun applyColorAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyGeometryAdjustments(adj: AdjustmentsV1) = true
        override fun applyPreviewAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyPreviewRotation(rotationDeg: Float, reason: String) = Unit
        override fun setPreviewOverlay(overlay: VideoOverlay?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long) = 0L
        override fun stepFrameBackward(minimumTimeMs: Long) = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() = Unit
    }
}
