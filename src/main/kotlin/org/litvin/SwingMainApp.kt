package org.litvin

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Font
import javax.swing.*
import java.util.prefs.Preferences

/**
 * Phase 0.1 — Minimal Swing entry point with JFrame and CardLayout navigation.
 *
 * This runs alongside the existing JavaFX app during migration.
 */
object SwingMainApp {
    private const val CARD_PROJECTS = "projects"
    private const val CARD_MARKUP = "markup"
    private const val CARD_EXPORT = "export"
    private const val CARD_VIDEOTEST = "videotest"

    @JvmStatic
    fun main(args: Array<String>) {
        // Basic HiDPI properties (have effect on some JDKs/platforms)
        System.setProperty("sun.java2d.uiScale.enabled", "true")
        System.setProperty("swing.aatext", "true")
        System.setProperty("awt.useSystemAAFontSettings", "on")

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

                // Sidebar (simple vertical buttons)
                val sidebar = JPanel()
                sidebar.layout = BoxLayout(sidebar, BoxLayout.Y_AXIS)
                sidebar.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

                val btnProjects = JButton("Projects")
                val btnMarkup = JButton("Markup")
                val btnExport = JButton("Export")
                val btnVideoTest = JButton("Video test")

                listOf(btnProjects, btnMarkup, btnExport, btnVideoTest).forEach { b ->
                    b.alignmentX = 0f
                    b.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
                    sidebar.add(b)
                    sidebar.add(Box.createRigidArea(Dimension(0, 8)))
                }

                // Cards container
                val cards = JPanel(CardLayout())
                val cl = cards.layout as CardLayout

                // Track current manifest path to pass to screens
                var currentManifestPath: String? = null

                // Projects screen (Swing Phase 2) and Markup (Phase 3)
                val markupPanel = SwingMarkupPanel()
                val exportPanel = SwingExportPanel()
                val projectsPanel = SwingProjectsPanel().apply {
                    onProjectOpened = { path ->
                        try {
                            currentManifestPath = path
                            markupPanel.setProjectManifest(path)
                            exportPanel.setProjectManifest(path)
                            frame.title = "Tennis Record — Markup (Swing)"
                            cl.show(cards, CARD_MARKUP)
                        } catch (_: Throwable) { }
                    }
                }

                // Video test screen (Phase 1 MVP with controls)
                val videoTestPanel = try {
                    val p = SwingVideoTestPanel()
                    // Release native resources on close
                    frame.addWindowListener(object: java.awt.event.WindowAdapter() {
                        override fun windowClosing(e: java.awt.event.WindowEvent) {
                            try { p.dispose() } catch (_: Throwable) { }
                        }
                    })
                    p
                } catch (t: Throwable) {
                    t.printStackTrace()
                    val fallback = JPanel(BorderLayout())
                    fallback.add(JLabel("Video panel init error: ${'$'}{t.message}", SwingConstants.CENTER), BorderLayout.CENTER)
                    fallback
                }

                cards.add(projectsPanel, CARD_PROJECTS)
                cards.add(markupPanel, CARD_MARKUP)
                cards.add(exportPanel, CARD_EXPORT)
                cards.add(videoTestPanel, CARD_VIDEOTEST)

                // Wire navigation
                btnProjects.addActionListener {
                    frame.title = "Tennis Record — Projects (Swing)"
                    cl.show(cards, CARD_PROJECTS)
                }
                btnMarkup.addActionListener {
                    frame.title = "Tennis Record — Markup (Swing)"
                    cl.show(cards, CARD_MARKUP)
                }
                btnExport.addActionListener {
                    frame.title = "Tennis Record — Export (Swing)"
                    cl.show(cards, CARD_EXPORT)
                }
                btnVideoTest.addActionListener {
                    frame.title = "Tennis Record — Video test (Swing)"
                    cl.show(cards, CARD_VIDEOTEST)
                }

                // Menu bar (simple View menu for navigation too)
                val menuBar = JMenuBar()
                val viewMenu = JMenu("View")
                val miProjects = JMenuItem("Projects")
                val miMarkup = JMenuItem("Markup")
                val miExport = JMenuItem("Export")
                val miVideo = JMenuItem("Video test")
                miProjects.addActionListener { btnProjects.doClick() }
                miMarkup.addActionListener { btnMarkup.doClick() }
                miExport.addActionListener { btnExport.doClick() }
                miVideo.addActionListener { btnVideoTest.doClick() }
                viewMenu.add(miProjects)
                viewMenu.add(miMarkup)
                viewMenu.add(miExport)
                viewMenu.add(miVideo)
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
                frame.addWindowListener(object: java.awt.event.WindowAdapter() {
                    override fun windowClosing(e: java.awt.event.WindowEvent) {
                        try {
                            val b = frame.bounds
                            prefs.putInt("win.x", b.x)
                            prefs.putInt("win.y", b.y)
                            prefs.putInt("win.w", b.width)
                            prefs.putInt("win.h", b.height)
                            prefs.putInt("win.state", frame.extendedState)
                        } catch (_: Throwable) { }
                        try { markupPanel.dispose() } catch (_: Throwable) { }
                    }
                })

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
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Throwable) { }
        // Slightly increase default font size on HiDPI, if needed
        try {
            val base = UIManager.getFont("Label.font")
            if (base != null && base.size < 13) {
                val f = base.deriveFont(base.size2D + 1.0f)
                val keys = UIManager.getDefaults().keys()
                while (keys.hasMoreElements()) {
                    val k = keys.nextElement()
                    if (k.toString().endsWith(".font")) {
                        UIManager.put(k, f)
                    }
                }
            }
        } catch (_: Throwable) { }
        // ToolTip quicker show, nicer focus colors can be adjusted later
        UIManager.put("ToolTip.hideAccelerator", true)
    }
}
