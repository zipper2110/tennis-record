package org.litvin.app

import org.litvin.AppInfo
import org.litvin.SwingMainApp
import org.litvin.WindowsGpuPreference
import org.litvin.media.MediaScreen
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.help.HelpDialog
import org.litvin.ui.help.HelpPage
import org.litvin.ui.help.HelpPreferences
import org.litvin.ui.tabs.adjustments.SwingColorAdjustmentsPanel
import org.litvin.ui.tabs.crop.SwingCropRotatePanel
import org.litvin.ui.tabs.crop.presenter.DefaultCropRotatePresenter
import org.litvin.ui.tabs.export.ExportSettingsPreferences
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.points.SwingPointsPanel
import org.litvin.ui.tabs.projects.SwingProjectsPanel
import org.litvin.ui.tabs.projects.presenter.DefaultProjectsPresenter
import org.litvin.ui.tabs.scoring.SwingScoringPanel
import org.litvin.ui.tabs.test.SwingTestPanel
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsEvent
import org.litvin.ui.privacy.AnalyticsConsentDialog
import org.litvin.ui.privacy.PrivacySettingsDialog
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.KeyStroke

object SwingApplicationFactory {
    private const val CARD_PROJECTS = "projects"
    private const val CARD_POINTS = "points"
    private const val CARD_EXPORT = "export"
    private const val CARD_SCORING = "scoring"
    private const val CARD_ADJ_COLORS = "adjustments"
    private const val CARD_ADJ_CROP_ROTATE = "adjustments-crop-rotate"
    private const val CARD_TEST = "test"

    internal fun shouldShowGpuRestartNotification(
        show: Boolean,
        testEnabled: Boolean,
        gpuPreferenceChanged: Boolean,
    ): Boolean = show && !testEnabled && gpuPreferenceChanged

    fun create(
        services: AppServices,
        show: Boolean = true,
        onWindowClosed: () -> Unit = {},
    ): SwingApplicationHandle {
        check(EventQueue.isDispatchThread()) { "Swing application must be created on the EDT" }

        val frame = JFrame(AppInfo.displayName).apply {
            name = "app-frame"
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            layout = BorderLayout()
        }
        val closeActions = mutableListOf<() -> Unit>({ services.close() })
        val handle = SwingApplicationHandle(frame, closeActions)

        try {
            val helpDialog = lazy { HelpDialog(frame) }
            fun showHelp(page: HelpPage) = helpDialog.value.open(page)

            val sidebar = JPanel().apply {
                UiStyles.styleSidebarContainer(this)
                preferredSize = Dimension(81, 0)
                foreground = UiStyles.SIDEBAR_FG
            }

            lateinit var btnProjects: UiStyles.SidebarButton
            lateinit var btnPoints: UiStyles.SidebarButton
            lateinit var btnColors: UiStyles.SidebarButton
            lateinit var btnScoring: UiStyles.SidebarButton
            lateinit var btnExport: UiStyles.SidebarButton
            lateinit var btnCropRotate: UiStyles.SidebarButton
            var btnPrivacy: UiStyles.SidebarButton? = null
            var btnTest: UiStyles.SidebarButton? = null

            fun addItem(button: UiStyles.SidebarButton) {
                button.alignmentX = 0f
                button.maximumSize = Dimension(Int.MAX_VALUE, 64)
                sidebar.add(button)
                sidebar.add(Box.createRigidArea(Dimension(0, 6)))
            }

            val cards = JPanel(CardLayout())
            val cardLayout = cards.layout as CardLayout
            val applicationPreferences = services.preferences.node(PreferencesProvider.APPLICATION)

            val pointsPanel = SwingPointsPanel(
                services.mediaPlayers.create(MediaScreen.POINTS),
                services.adjustments,
                services.executors.createExecutor("points-autosave"),
                services.dialogs,
            )
            closeActions += pointsPanel::close

            val colorsPanel = SwingColorAdjustmentsPanel(
                services.mediaPlayers.create(MediaScreen.COLORS),
                services.adjustments,
                services.preferences.node(PreferencesProvider.COLOR_ADJUSTMENTS),
            )
            closeActions += colorsPanel::close

            val cropRotatePanel = SwingCropRotatePanel(
                services.mediaPlayers.create(MediaScreen.CROP),
                DefaultCropRotatePresenter(services.adjustments),
            )
            closeActions += cropRotatePanel::dispose

            val scoringPanel = SwingScoringPanel(
                services.mediaPlayers.create(MediaScreen.SCORING),
                services.adjustments,
                services.dialogs,
            )
            closeActions += scoringPanel::close

            val exportPanel = SwingExportPanel(
                ExportSettingsPreferences(services.preferences.node(PreferencesProvider.EXPORT)),
                services.renderService,
                services.completedRenders,
                services.filePicker,
                services.dialogs,
                services.encoderCapabilities,
            )
            closeActions += exportPanel::close

            val testEnabled = System.getProperty("test") == "true"
            val testPanel = if (testEnabled) SwingTestPanel() else null
            if (testPanel != null) closeActions += testPanel::onDeactivated
            lateinit var projectsPanel: SwingProjectsPanel

            var currentCard: String? = null
            fun currentHelpPage(): HelpPage = when (currentCard) {
                CARD_PROJECTS -> HelpPage.PROJECTS
                CARD_POINTS -> HelpPage.POINTS
                CARD_ADJ_COLORS -> HelpPage.COLORS
                CARD_ADJ_CROP_ROTATE -> HelpPage.CROP
                CARD_SCORING -> HelpPage.SCORING
                CARD_EXPORT -> HelpPage.EXPORT
                else -> HelpPage.OVERVIEW
            }

            fun goTo(card: String) {
                if (card == currentCard) return
                when (currentCard) {
                    CARD_PROJECTS -> projectsPanel.onDeactivated()
                    CARD_POINTS -> pointsPanel.onDeactivated()
                    CARD_ADJ_COLORS -> colorsPanel.onDeactivated()
                    CARD_ADJ_CROP_ROTATE -> cropRotatePanel.onDeactivated()
                    CARD_SCORING -> scoringPanel.onDeactivated()
                    CARD_TEST -> testPanel?.onDeactivated()
                }
                cardLayout.show(cards, card)
                when (card) {
                    CARD_PROJECTS -> projectsPanel.onActivated()
                    CARD_POINTS -> pointsPanel.onActivated()
                    CARD_ADJ_COLORS -> colorsPanel.onActivated()
                    CARD_ADJ_CROP_ROTATE -> cropRotatePanel.onActivated()
                    CARD_SCORING -> scoringPanel.onActivated()
                    CARD_EXPORT -> exportPanel.onActivated()
                    CARD_TEST -> testPanel?.onActivated()
                }
                btnProjects.active = card == CARD_PROJECTS
                btnPoints.active = card == CARD_POINTS
                btnColors.active = card == CARD_ADJ_COLORS
                btnCropRotate.active = card == CARD_ADJ_CROP_ROTATE
                btnScoring.active = card == CARD_SCORING
                btnExport.active = card == CARD_EXPORT
                btnTest?.active = card == CARD_TEST
                currentCard = card
            }

            val projectsPresenter = DefaultProjectsPresenter(
                services.projectsRepository,
                services.preferences.node(PreferencesProvider.PROJECTS),
                services.executors.createExecutor("projects-io"),
            )
            projectsPanel = SwingProjectsPanel(
                projectsPresenter,
                services.filePicker,
                services.dialogs,
            ).apply {
                onProjectOpened = { path ->
                    pointsPanel.setProjectManifest(path)
                    colorsPanel.setProjectManifest(path)
                    cropRotatePanel.setProjectManifest(path)
                    scoringPanel.setProjectManifest(path)
                    exportPanel.setProjectManifest(path)
                    testPanel?.setProjectManifest(path)
                    btnPoints.isVisible = true
                    btnColors.isVisible = true
                    btnCropRotate.isVisible = true
                    btnScoring.isVisible = true
                    btnExport.isVisible = true
                    btnTest?.isVisible = true
                    sidebar.revalidate()
                    sidebar.repaint()
                    frame.title = "Tennis Record — Points"
                    goTo(CARD_POINTS)
                }
            }

            cards.add(projectsPanel, CARD_PROJECTS)
            cards.add(pointsPanel, CARD_POINTS)
            cards.add(colorsPanel, CARD_ADJ_COLORS)
            cards.add(cropRotatePanel, CARD_ADJ_CROP_ROTATE)
            cards.add(scoringPanel, CARD_SCORING)
            cards.add(exportPanel, CARD_EXPORT)
            if (testPanel != null) cards.add(testPanel, CARD_TEST)

            btnProjects = UiStyles.sidebarButton("Projects", UiStyles.folderIcon()) {
                frame.title = "Tennis Record — Projects"
                goTo(CARD_PROJECTS)
            }.apply { name = "nav-projects" }
            addItem(btnProjects)
            btnColors = UiStyles.sidebarButton("Colors", UiStyles.colorsIcon()) {
                frame.title = "Tennis Record — Color"
                goTo(CARD_ADJ_COLORS)
            }.apply { name = "nav-colors" }
            addItem(btnColors)
            btnCropRotate = UiStyles.sidebarButton("Transform", UiStyles.cropRotateIcon()) {
                frame.title = "Tennis Record — Transform"
                goTo(CARD_ADJ_CROP_ROTATE)
            }.apply { name = "nav-crop" }
            addItem(btnCropRotate)
            btnPoints = UiStyles.sidebarButton("Points", UiStyles.pointsIcon()) {
                frame.title = "Tennis Record — Points"
                goTo(CARD_POINTS)
            }.apply { name = "nav-points" }
            addItem(btnPoints)
            btnScoring = UiStyles.sidebarButton("Scoring", UiStyles.targetIcon()) {
                frame.title = "Tennis Record — Scoring"
                goTo(CARD_SCORING)
            }.apply { name = "nav-scoring" }
            addItem(btnScoring)
            btnExport = UiStyles.sidebarButton("Export", UiStyles.exportIcon()) {
                frame.title = "Tennis Record — Export"
                goTo(CARD_EXPORT)
            }.apply { name = "nav-export" }
            addItem(btnExport)
            val analytics = services.analyticsController
            val analyticsConfig = services.analyticsConfig as? AnalyticsBuildConfig.Enabled
            if (analytics != null && analyticsConfig != null && services.analyticsPreferences != null) {
                btnPrivacy = UiStyles.sidebarButton("Privacy", UiStyles.targetIcon()) {
                    PrivacySettingsDialog.show(frame, analytics, services.analyticsPreferences, analyticsConfig.privacyUrl)
                }.apply { name = "nav-privacy" }
                addItem(btnPrivacy!!)
            }
            if (testEnabled) {
                btnTest = UiStyles.sidebarButton("Test", UiStyles.targetIcon()) {
                    frame.title = "Tennis Record — Test"
                    goTo(CARD_TEST)
                }
                addItem(btnTest!!)
            }
            sidebar.add(Box.createVerticalGlue())
            sidebar.add(UiStyles.sidebarButton("Help", UiStyles.helpIcon()) {
                showHelp(currentHelpPage())
            }.apply {
                name = "nav-help"
                toolTipText = "F1 - Help"
                alignmentX = 0f
                maximumSize = Dimension(Int.MAX_VALUE, 64)
            })

            btnPoints.isVisible = false
            btnColors.isVisible = false
            btnCropRotate.isVisible = false
            btnScoring.isVisible = false
            btnExport.isVisible = false

            frame.add(sidebar, BorderLayout.WEST)
            frame.add(cards, BorderLayout.CENTER)
            frame.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(AppShortcuts.HELP.keyStroke), "openContextHelp")
            frame.rootPane.actionMap.put("openContextHelp", object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    showHelp(currentHelpPage())
                }
            })

            val savedX = applicationPreferences.getInt("win.x", Int.MIN_VALUE)
            val savedY = applicationPreferences.getInt("win.y", Int.MIN_VALUE)
            val savedW = applicationPreferences.getInt("win.w", Int.MIN_VALUE)
            val savedH = applicationPreferences.getInt("win.h", Int.MIN_VALUE)
            val savedState = applicationPreferences.getInt("win.state", JFrame.NORMAL)
            if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE && savedW > 0 && savedH > 0) {
                frame.setBounds(savedX, savedY, savedW, savedH)
                frame.extendedState = savedState
            } else {
                frame.setSize(1200, 800)
                frame.setLocationRelativeTo(null)
            }

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent?) {
                    analytics?.record(AnalyticsEvent.SessionEnded)
                    try {
                        handle.close()
                    } finally {
                        onWindowClosed()
                    }
                }
            })

            btnProjects.doClick()
            frame.isVisible = show

            if (show && analytics != null && analyticsConfig != null && services.analyticsPreferences?.resolve()?.needsChoice == true) {
                EventQueue.invokeLater { AnalyticsConsentDialog.show(frame, analytics, analyticsConfig.privacyUrl) }
            }

            if (shouldShowGpuRestartNotification(show, testEnabled, WindowsGpuPreference.wasChangeApplied())) {
                JOptionPane.showMessageDialog(
                    frame,
                    "We set a Windows preference for this app to use the dedicated/external GPU on future launches.\n\n" +
                        "Please restart the application now. If it still uses the integrated GPU, open Windows Graphics Settings → Graphics performance preference, or NVIDIA/AMD control panel, and force the high‑performance GPU for javaw.exe (or your packaged EXE).",
                    "GPU preference set",
                    JOptionPane.INFORMATION_MESSAGE,
                )
            }
            if (show && HelpPreferences.claimFirstLaunchOverview(applicationPreferences)) {
                showHelp(HelpPage.OVERVIEW)
            }

            return handle
        } catch (failure: Throwable) {
            try {
                handle.close()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }
}
