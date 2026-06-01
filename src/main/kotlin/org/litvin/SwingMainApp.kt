package org.litvin

import com.formdev.flatlaf.FlatDarkLaf
import org.litvin.ui.UiStyles
import java.awt.*
import java.util.prefs.Preferences
import javax.swing.*
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.tabs.projects.SwingProjectsPanel
import org.litvin.ui.tabs.markup.SwingMarkupPanel
import org.litvin.ui.tabs.scoring.SwingScoringPanel
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.adjustments.SwingColorAdjustmentsPanel
import org.litvin.ui.tabs.crop.SwingCropRotatePanel


/**
 * Phase 0.1 — Minimal Swing entry point with JFrame and CardLayout navigation.
 *
 * This runs alongside the existing JavaFX app during migration.
 */
object SwingMainApp {
    private const val CARD_PROJECTS = "projects"
    private const val CARD_RALLIES = "markup"
    private const val CARD_EXPORT = "export"
    private const val CARD_SCORING = "scoring"
    private const val CARD_ADJ_COLORS = "adjustments"
    private const val CARD_ADJ_CROP_ROTATE = "adjustments-crop-rotate"

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            UIManager.setLookAndFeel(FlatDarkLaf())
            UIManager.put("defaultFont", Font("Segoe UI", Font.PLAIN, 14))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // HiDPI bootstrap — must be set BEFORE any AWT/Swing classes are initialized
        val javaSpec = (System.getProperty("java.specification.version") ?: "11").trim()
        val major = javaSpec.toDoubleOrNull() ?: 11.0

        // Enable Java2D UI scaling support where available
        if (System.getProperty("sun.java2d.uiScale.enabled") == null)
            System.setProperty("sun.java2d.uiScale.enabled", "true")

        // IMPORTANT: For modern JDKs (9+) do NOT force dpiaware/uiScale — it can disable Windows scaling
        if (major < 9) {
            // Mark the app as DPI-aware on Windows (prevents blurry bitmap upscaling on JDK8)
            if (System.getProperty("sun.java2d.dpiaware") == null)
                System.setProperty("sun.java2d.dpiaware", "true")

            // On Java 8, automatic scaling is unreliable: compute scale from screen DPI and force uiScale
            if (System.getProperty("sun.java2d.uiScale") == null) {
                val dpi = Toolkit.getDefaultToolkit().screenResolution.toDouble()
                val scale = dpi / 96.0
                if (scale >= 1.25) {
                    val s = String.format(java.util.Locale.US, "%.2f", scale)
                    System.setProperty("sun.java2d.uiScale", s)
                }
            }
        }

        // Font anti-aliasing hints (Windows 11 optimized; harmless on modern JDKs)
        // Allow override via system property or env var:
        //   -Dtennisrecord.textAA={off|on|lcd|lcd-hrgb|lcd-hbgr|lcd-vrgb|lcd-vbgr}
        //   or environment TENNISRECORD_TEXT_AA with same values.
        try {
            val override = System.getProperty("tennisrecord.textAA")
                ?: System.getenv("TENNISRECORD_TEXT_AA")
            val os = (System.getProperty("os.name") ?: "").lowercase()
            val isWindows = os.contains("win")
            val value = when (override?.lowercase()?.trim()) {
                null, "", "auto" -> if (isWindows) "lcd_hrgb" else "on"
                "off" -> "off"
                "on" -> "on"
                "lcd" -> "lcd"
                "lcd-hrgb", "lcd_hrgb" -> "lcd_hrgb"
                "lcd-hbgr", "lcd_hbgr" -> "lcd_hbgr"
                "lcd-vrgb", "lcd_vrgb" -> "lcd_vrgb"
                "lcd-vbgr", "lcd_vbgr" -> "lcd_vbgr"
                else -> if (isWindows) "lcd_hrgb" else "on"
            }
            System.setProperty("swing.aatext", if (value == "off") "false" else "true")
            System.setProperty("awt.useSystemAAFontSettings", value)
            // Prefer precise glyph positioning for better kerning/metrics
            System.setProperty("sun.java2d.fractionalmetrics", "on")
        } catch (_: Throwable) {
            System.setProperty("swing.aatext", "true")
            System.setProperty("awt.useSystemAAFontSettings", "on")
        }

        // Keep all UI work on the EDT
        EventQueue.invokeLater {
            try {
                // Global Swing uncaught error handler → show friendly dialog
                Thread.setDefaultUncaughtExceptionHandler { _, e ->
                    e.printStackTrace()
                    Dialogs.showError(null, e, "Unexpected error")
                }

                // Try to hint Windows to use the High Performance GPU for Java/this app
                WindowsGpuPreference.ensureHighPerformancePreference()

                // Base theming (Phase 0.4)
                applyBaseTheme()

                val frame = JFrame("Tennis Record — Swing (Skeleton)")
                frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                frame.layout = BorderLayout()

                // Sidebar styled to match the mock
                val sidebar = JPanel().apply {
                    UiStyles.styleSidebarContainer(this)
                    preferredSize = Dimension(100, 0)
                    foreground = UiStyles.SIDEBAR_FG
                }

                // Cards container prepared below (needs cl for actions)

                // Buttons with lime icons
                lateinit var btnProjects: UiStyles.SidebarButton
                lateinit var btnRallies: UiStyles.SidebarButton
                lateinit var btnColors: UiStyles.SidebarButton
                lateinit var btnScoring: UiStyles.SidebarButton
                lateinit var btnExport: UiStyles.SidebarButton
                lateinit var btnCropRotate: UiStyles.SidebarButton

                fun addItem(b: UiStyles.SidebarButton) {
                    b.alignmentX = 0f
                    b.maximumSize = Dimension(Int.MAX_VALUE, 64)
                    sidebar.add(b)
                    sidebar.add(Box.createRigidArea(Dimension(0, 6)))
                }

                // Cards container
                val cards = JPanel(CardLayout())
                val cl = cards.layout as CardLayout

                // Projects screen (Swing Phase 2) and Markup (Phase 3)
                val ralliesPanel = SwingMarkupPanel()
                val colorsPanel = SwingColorAdjustmentsPanel()
                val cropRotatePanel = SwingCropRotatePanel()
                val scoringPanel = SwingScoringPanel()
                val exportPanel = SwingExportPanel()
                lateinit var projectsPanel: SwingProjectsPanel

                // Navigation helper with lifecycle wiring
                var currentCard: String? = null
                fun goTo(card: String) {
                    // Pause media on panels being left
                    when (currentCard) {
                        CARD_PROJECTS -> projectsPanel.onDeactivated()
                        CARD_RALLIES -> ralliesPanel.onDeactivated()
                        CARD_ADJ_COLORS -> colorsPanel.onDeactivated()
                        CARD_ADJ_CROP_ROTATE -> cropRotatePanel.onDeactivated()
                        CARD_SCORING -> scoringPanel.onDeactivated()
                    }
                    // Show target card
                    cl.show(cards, card)
                    // Activate the new panel (no autoplay)
                    when (card) {
                        CARD_PROJECTS -> projectsPanel.onActivated()
                        CARD_RALLIES -> ralliesPanel.onActivated()
                        CARD_ADJ_COLORS -> colorsPanel.onActivated()
                        CARD_ADJ_CROP_ROTATE -> cropRotatePanel.onActivated()
                        CARD_SCORING -> scoringPanel.onActivated()
                        CARD_EXPORT -> exportPanel.onActivated()
                    }
                    // Update sidebar active state to reflect selected tab
                    btnProjects.active = card == CARD_PROJECTS
                    btnRallies.active = card == CARD_RALLIES
                    btnColors.active = card == CARD_ADJ_COLORS
                    btnCropRotate.active = card == CARD_ADJ_CROP_ROTATE
                    btnScoring.active = card == CARD_SCORING
                    btnExport.active = card == CARD_EXPORT
                    currentCard = card
                }

                projectsPanel = SwingProjectsPanel().apply {
                    onProjectOpened = { path ->
                        ralliesPanel.setProjectManifest(path)
                        colorsPanel.setProjectManifest(path)
                        cropRotatePanel.setProjectManifest(path)
                        scoringPanel.setProjectManifest(path)
                        exportPanel.setProjectManifest(path)
                        // Reveal other tabs now that a project is selected
                        btnRallies.isVisible = true
                        btnColors.isVisible = true
                        btnCropRotate.isVisible = true
                        btnScoring.isVisible = true
                        btnExport.isVisible = true
                        sidebar.revalidate(); sidebar.repaint()
                        frame.title = "Tennis Record — Markup"
                        goTo(CARD_RALLIES)
                    }
                }

                cards.add(projectsPanel, CARD_PROJECTS)
                cards.add(ralliesPanel, CARD_RALLIES)
                cards.add(colorsPanel, CARD_ADJ_COLORS)
                cards.add(cropRotatePanel, CARD_ADJ_CROP_ROTATE)
                cards.add(scoringPanel, CARD_SCORING)
                cards.add(exportPanel, CARD_EXPORT)

                // Create sidebar items with icons and actions
                btnProjects = UiStyles.sidebarButton("Projects", UiStyles.folderIcon()) {
                    frame.title = "Tennis Record — Projects"
                    goTo(CARD_PROJECTS)
                }
                addItem(btnProjects)

                btnColors = UiStyles.sidebarButton("Colors", UiStyles.colorsIcon()) {
                    frame.title = "Tennis Record — Color"
                    goTo(CARD_ADJ_COLORS)
                }
                addItem(btnColors)

                btnCropRotate = UiStyles.sidebarButton("Crop", UiStyles.cropRotateIcon()) {
                    frame.title = "Tennis Record — Crop & Rotate"
                    goTo(CARD_ADJ_CROP_ROTATE)
                }
                addItem(btnCropRotate)

                btnRallies = UiStyles.sidebarButton("Rallies", UiStyles.rallyIcon()) {
                    frame.title = "Tennis Record — Rallies"
                    goTo(CARD_RALLIES)
                }
                addItem(btnRallies)

                btnScoring = UiStyles.sidebarButton("Scoring", UiStyles.targetIcon()) {
                    frame.title = "Tennis Record — Scoring"
                    goTo(CARD_SCORING)
                }
                addItem(btnScoring)

                btnExport = UiStyles.sidebarButton("Export", UiStyles.exportIcon()) {
                    frame.title = "Tennis Record — Export"
                    goTo(CARD_EXPORT)
                }
                addItem(btnExport)

                // Hide all non-project tabs until a project is opened
                btnRallies.isVisible = false
                btnColors.isVisible = false
                btnCropRotate.isVisible = false
                btnScoring.isVisible = false
                btnExport.isVisible = false

                frame.add(sidebar, BorderLayout.WEST)
                frame.add(cards, BorderLayout.CENTER)

                // Restore window state from preferences
                val prefs = Preferences.userNodeForPackage(SwingMainApp::class.java)
                val savedX = prefs.getInt("win.x", Int.MIN_VALUE)
                val savedY = prefs.getInt("win.y", Int.MIN_VALUE)
                val savedW = prefs.getInt("win.w", Int.MIN_VALUE)
                val savedH = prefs.getInt("win.h", Int.MIN_VALUE)
                val savedState = prefs.getInt("win.state", JFrame.NORMAL)
                if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE && savedW > 0 && savedH > 0) {
                    frame.setBounds(savedX, savedY, savedW, savedH)
                    frame.extendedState = savedState
                } else {
                    frame.setSize(1200, 800)
                    frame.setLocationRelativeTo(null)
                }

                frame.isVisible = true

                // Start on Projects
                btnProjects.doClick()

                // After setting GPU preference, inform user (mirrors JavaFX behavior)
                if (WindowsGpuPreference.wasChangeApplied()) {
                    JOptionPane.showMessageDialog(
                        frame,
                        "We set a Windows preference for this app to use the dedicated/external GPU on future launches.\n\n" +
                            "Please restart the application now. If it still uses the integrated GPU, open Windows Graphics Settings → Graphics performance preference, or NVIDIA/AMD control panel, and force the high‑performance GPU for javaw.exe (or your packaged EXE).",
                        "GPU preference set",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                JOptionPane.showMessageDialog(null, t.message ?: t.toString(),
                    "Startup error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun applyBaseTheme() {
        // Keep the previously set LAF (FlatLaf) — don't override it here.
        // Improve font rendering and set a consistent default font across components.

        // Prefer modern Segoe on Windows when available
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val family = when {
            families.contains("Segoe UI Variable") -> "Segoe UI Variable"
            families.contains("Segoe UI") -> "Segoe UI"
            else -> "Tahoma"
        }
        val baseSize = (UIManager.getFont("Label.font")?.size2D ?: 13f).coerceAtLeast(13f)
        val baseFont = Font(family, Font.PLAIN, baseSize.toInt())

        // Apply to all UI defaults that are fonts
        val keys = UIManager.getDefaults().keys()
        while (keys.hasMoreElements()) {
            val k = keys.nextElement()
            if (k.toString().endsWith(".font")) {
                UIManager.put(k, baseFont)
            }
        }
        UIManager.put("defaultFont", baseFont)

        // Make tooltips nicer
        UIManager.put("ToolTip.hideAccelerator", true)
    }
}
