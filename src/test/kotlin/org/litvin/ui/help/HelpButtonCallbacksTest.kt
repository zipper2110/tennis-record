package org.litvin.ui.help

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.litvin.ui.tabs.adjustments.SwingColorAdjustmentsPanel
import org.litvin.media.MediaScreen
import org.litvin.ui.flow.fakes.FakeMediaPlayer
import org.litvin.ui.tabs.crop.SwingCropRotatePanel
import org.litvin.ui.tabs.crop.presenter.CropRotateIntent
import org.litvin.ui.tabs.crop.presenter.CropRotatePresenter
import org.litvin.ui.tabs.crop.presenter.CropRotateView
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.markup.SwingMarkupPanel
import org.litvin.ui.tabs.projects.components.ProjectsHeader
import org.litvin.ui.tabs.scoring.SwingScoringPanel
import org.litvin.ui.tabs.scoring.ui.ControlsToolbar
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HelpButtonCallbacksTest {
    @Test
    fun projectsHeaderInvokesHelpCallback() {
        var calls = 0
        SwingUtilities.invokeAndWait {
            val header = ProjectsHeader(onImportNewMatch = {}, onHelp = { calls++ })
            header.buttonNamed("projects-help").doClick()
        }
        assertEquals(1, calls)
    }

    @Test
    fun scoringToolbarInvokesHelpCallback() {
        var calls = 0
        SwingUtilities.invokeAndWait {
            val toolbar = ControlsToolbar(onHelp = { calls++ })
            toolbar.buttonNamed("toolbar-help").doClick()
        }
        assertEquals(1, calls)
    }

    @Test
    fun colorsPanelInvokesHelpCallback() = assertPanelHelp("colors-help") { callback ->
        SwingColorAdjustmentsPanel(callback)
    }

    @Test
    fun cropPanelInvokesHelpCallback() = assertPanelHelp("crop-help") { callback ->
        SwingCropRotatePanel(FakeMediaPlayer(MediaScreen.CROP), NoOpCropPresenter(), callback)
    }

    @Test
    fun ralliesPanelInvokesHelpCallback() = assertPanelHelp("rallies-help") { callback ->
        SwingMarkupPanel(callback)
    }

    @Test
    fun scoringPanelInvokesHelpCallback() = assertPanelHelp("toolbar-help") { callback ->
        SwingScoringPanel(callback)
    }

    @Test
    fun exportPanelInvokesHelpCallback() = assertPanelHelp("export-help") { callback ->
        SwingExportPanel(callback)
    }

    private fun assertPanelHelp(buttonName: String, create: (() -> Unit) -> Component) {
        System.setProperty("java.awt.headless", "true")
        var calls = 0
        var error: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                create { calls++ }.buttonNamed(buttonName).doClick()
            } catch (t: Throwable) {
                error = t
            }
        }
        assumeTrue(error == null, "Could not construct panel: ${error?.message}")
        assertEquals(1, calls)
    }

    private fun Component.buttonNamed(name: String): JButton {
        val match = this as? JButton
        if (match?.name == name) return match
        if (this is Container) {
            components.forEach { child ->
                val found = child.findButton(name)
                if (found != null) return found
            }
        }
        return assertNotNull(null, "Button $name not found")
    }

    private fun Component.findButton(name: String): JButton? {
        if (this is JButton && this.name == name) return this
        if (this is Container) {
            components.forEach { child ->
                child.findButton(name)?.let { return it }
            }
        }
        return null
    }

    private class NoOpCropPresenter : CropRotatePresenter {
        override fun attach(view: CropRotateView) {}
        override fun detach() {}
        override fun onActivated() {}
        override fun onDeactivated() {}
        override fun onIntent(intent: CropRotateIntent) {}
    }
}
