package org.litvin

import com.formdev.flatlaf.FlatLightLaf
import java.awt.*
import java.util.prefs.Preferences
import javax.swing.*


/**
 * Phase 0.1 — Minimal Swing entry point with JFrame and CardLayout navigation.
 *
 * This runs alongside the existing JavaFX app during migration.
 */
object SwingMainApp {
    private const val CARD_PROJECTS = "projects"
    private const val CARD_MARKUP = "markup"
    private const val CARD_EXPORT = "export"
    private const val CARD_SCORING = "scoring"

    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeLater(Runnable {
            try {
                UIManager.setLookAndFeel(FlatLightLaf())
                UIManager.put("defaultFont", Font("Segoe UI", Font.PLAIN, 14))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })

        // HiDPI bootstrap — must be set BEFORE any AWT/Swing classes are initialized
        try {
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
                    try {
                        val dpi = java.awt.Toolkit.getDefaultToolkit().screenResolution.toDouble()
                        val scale = dpi / 96.0
                        if (scale >= 1.25) {
                            val s = String.format(java.util.Locale.US, "%.2f", scale)
                            System.setProperty("sun.java2d.uiScale", s)
                        }
                    } catch (_: Throwable) { /* ignore */ }
                }
            }
        } catch (_: Throwable) { /* ignore */ }

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

        UIManager.setLookAndFeel(FlatLightLaf())

        // Keep all UI work on the EDT
        EventQueue.invokeLater {
            try {
                // Global Swing uncaught error handler → show friendly dialog
                Thread.setDefaultUncaughtExceptionHandler { _, e ->
                    e.printStackTrace()
                    SwingDialogUtils.showError(null, e, "Unexpected error")
                }

                // Try to hint Windows to use the High Performance GPU for Java/this app
                try {
                    WindowsGpuPreference.ensureHighPerformancePreference()
                } catch (_: Throwable) { }

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
                lateinit var btnMarkup: UiStyles.SidebarButton
                lateinit var btnScoring: UiStyles.SidebarButton
                lateinit var btnExport: UiStyles.SidebarButton

                fun addItem(b: UiStyles.SidebarButton) {
                    b.alignmentX = 0f
                    b.maximumSize = Dimension(Int.MAX_VALUE, 64)
                    sidebar.add(b)
                    sidebar.add(Box.createRigidArea(Dimension(0, 6)))
                }

                // Cards container
                val cards = JPanel(CardLayout())
                val cl = cards.layout as CardLayout

                // Track current manifest path to pass to screens
                var currentManifestPath: String? = null

                // Projects screen (Swing Phase 2) and Markup (Phase 3)
                var setActive: (String) -> Unit = {}
                val markupPanel = SwingMarkupPanel()
                val scoringPanel = SwingScoringPanel()
                val exportPanel = SwingExportPanel()
                val projectsPanel = SwingProjectsPanel().apply {
                    onProjectOpened = { path ->
                        try {
                            currentManifestPath = path
                            markupPanel.setProjectManifest(path)
                            scoringPanel.setProjectManifest(path)
                            exportPanel.setProjectManifest(path)
                            frame.title = "Tennis Record — Markup (Swing)"
                            cl.show(cards, CARD_MARKUP)
                            setActive(CARD_MARKUP)
                            try { markupPanel.onActivated() } catch (_: Throwable) {}
                        } catch (_: Throwable) { }
                    }
                }

                cards.add(projectsPanel, CARD_PROJECTS)
                cards.add(markupPanel, CARD_MARKUP)
                cards.add(scoringPanel, CARD_SCORING)
                cards.add(exportPanel, CARD_EXPORT)

                // Navigation helper with lifecycle wiring
                var currentCard: String? = null
                fun goTo(card: String) {
                    try {
                        // Pause media on panels being left
                        when (currentCard) {
                            CARD_MARKUP -> try { markupPanel.onDeactivated() } catch (_: Throwable) {}
                            CARD_SCORING -> try { scoringPanel.onDeactivated() } catch (_: Throwable) {}
                        }
                        // Show target card
                        cl.show(cards, card)
                        // Update button active states
                        setActive(card)
                        // Activate the new panel (no autoplay)
                        when (card) {
                            CARD_MARKUP -> try { markupPanel.onActivated() } catch (_: Throwable) {}
                            CARD_SCORING -> try { scoringPanel.onActivated() } catch (_: Throwable) {}
                        }
                        currentCard = card
                    } catch (_: Throwable) { }
                }

                // Create sidebar items with icons and actions
                btnProjects = UiStyles.sidebarButton("Projects", UiStyles.folderIcon()) {
                    frame.title = "Tennis Record — Projects (Swing)"
                    goTo(CARD_PROJECTS)
                }
                addItem(btnProjects)

                btnMarkup = UiStyles.sidebarButton("Markup", UiStyles.slidersIcon()) {
                    frame.title = "Tennis Record — Markup (Swing)"
                    goTo(CARD_MARKUP)
                }
                addItem(btnMarkup)

                btnScoring = UiStyles.sidebarButton("Scoring", UiStyles.targetIcon()) {
                    frame.title = "Tennis Record — Scoring (Swing)"
                    goTo(CARD_SCORING)
                }
                addItem(btnScoring)

                btnExport = UiStyles.sidebarButton("Export", UiStyles.exportIcon()) {
                    frame.title = "Tennis Record — Export (Swing)"
                    goTo(CARD_EXPORT)
                }
                addItem(btnExport)

                // Now that buttons exist, wire active-state updater
                setActive = { card ->
                    btnProjects.active = card == CARD_PROJECTS
                    btnMarkup.active = card == CARD_MARKUP
                    btnScoring.active = card == CARD_SCORING
                    btnExport.active = card == CARD_EXPORT
                }

                // Menu bar (simple View menu for navigation too)
                val menuBar = JMenuBar()
                val viewMenu = JMenu("View")
                val miProjects = JMenuItem("Projects")
                val miMarkup = JMenuItem("Markup")
                val miScoring = JMenuItem("Scoring")
                val miExport = JMenuItem("Export")
                miProjects.addActionListener { btnProjects.doClick() }
                miMarkup.addActionListener { btnMarkup.doClick() }
                miScoring.addActionListener { btnScoring.doClick() }
                miExport.addActionListener { btnExport.doClick() }
                viewMenu.add(miProjects)
                viewMenu.add(miMarkup)
                viewMenu.add(miScoring)
                viewMenu.add(miExport)
                menuBar.add(viewMenu)
                frame.jMenuBar = menuBar

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
                // Persist window state on close + dispose resources
//                frame.addWindowListener(object: java.awt.event.WindowAdapter() {
//                    override fun windowClosing(e: java.awt.event.WindowEvent) {
//                        try {
//                            val b = frame.bounds
//                            prefs.putInt("win.x", b.x)
//                            prefs.putInt("win.y", b.y)
//                            prefs.putInt("win.w", b.width)
//                            prefs.putInt("win.h", b.height)
//                            prefs.putInt("win.state", frame.extendedState)
//                        } catch (_: Throwable) { }
//                        try { markupPanel.dispose() } catch (_: Throwable) { }
//                    }
//                })

                frame.isVisible = true

                // Start on Projects
                btnProjects.doClick()

                // After setting GPU preference, inform user (mirrors JavaFX behavior)
                try {
                    if (WindowsGpuPreference.wasChangeApplied()) {
                        JOptionPane.showMessageDialog(
                            frame,
                            "We set a Windows preference for this app to use the dedicated/external GPU on future launches.\n\n" +
                                "Please restart the application now. If it still uses the integrated GPU, open Windows Graphics Settings → Graphics performance preference, or NVIDIA/AMD control panel, and force the high‑performance GPU for javaw.exe (or your packaged EXE).",
                            "GPU preference set",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                } catch (_: Throwable) { }
            } catch (t: Throwable) {
                t.printStackTrace()
                JOptionPane.showMessageDialog(null, t.message ?: t.toString(), "Startup error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun placeholderPanel(text: String): JPanel {
        val panel = JPanel(BorderLayout())
        val label = JLabel(text, SwingConstants.CENTER)
        label.font = label.font.deriveFont(Font.BOLD, 20f)
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    // Phase 0.4 — base theming and HiDPI-friendly defaults
    private fun applyBaseTheme() {
        // Keep the previously set LAF (FlatLaf) — don't override it here.
        // Improve font rendering and set a consistent default font across components.
        try {
            // Prefer modern Segoe on Windows when available
            val families = try { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet() } catch (_: Throwable) { emptySet() }
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
        } catch (_: Throwable) { }

        // Make tooltips nicer
        UIManager.put("ToolTip.hideAccelerator", true)
    }
}
