package org.litvin

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Projects screen (Swing) — rebuilt to mirror the legacy JavaFX layout/wording.
 *
 * Key UX parity per requirements:
 * - Header: app title/subtitle on the left; primary action button on the right: "IMPORT NEW MATCH".
 * - Section "Current Project" with an empty-state card: "No open project" + helper text.
 * - Section "Existing Projects": vertical list of cards; each card shows title and a secondary line with the
 *   source video path (if present) or manifest path; right-aligned "Open" button; double-click card opens.
 * - Pagination: page size 12; hide the pagination bar when only a single page exists.
 * - Status bar intentionally omitted (out of scope).
 */
class SwingProjectsPanel : JPanel(BorderLayout()) {
    // Preferences for MRU directory for video chooser
    private val prefs: Preferences = Preferences.userNodeForPackage(SwingProjectsPanel::class.java)

    // Header controls
    private val importBtn = primaryButton("IMPORT NEW MATCH") { onNewProject(this) }

    // Scrolling content container for sections and list
    private val scrollContent = JPanel()

    // Existing projects list container (cards go here)
    private val listContainer = JPanel()

    // Current project: state and container
    private var currentProjectPath: String? = null
    private val currentProjectContainer: JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = 0f
        minimumSize = Dimension(200, 48)
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    // Pagination controls
    private val prevBtn = JButton("Prev")
    private val nextBtn = JButton("Next")
    private val pageLabel = JLabel("Page 1 / 1")
    private var currentPage = 1
    private val pageSize = 12
    private var items: List<RecentsProvider.RecentEntry> = emptyList()

    var onProjectOpened: ((String) -> Unit)? = null

    init {
        isOpaque = true
        background = DARK_BG
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        // Header row
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            val titleBox = Box.createVerticalBox().apply {
                add(headerTitle("TENNIS RECORD"))
                add(Box.createVerticalStrut(2))
                add(headerSubtitle("TENNIS VIDEO ANALYTICS & EDITING SUITE"))
            }
            add(titleBox, BorderLayout.WEST)
            add(importBtn, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)

        // Scroll content
        scrollContent.layout = BoxLayout(scrollContent, BoxLayout.Y_AXIS)
        scrollContent.isOpaque = false

        // Current Project section
        scrollContent.add(sectionLabel("Current Project"))
        scrollContent.add(Box.createVerticalStrut(6))
        // Container for current project card (updated dynamically)
        scrollContent.add(currentProjectContainer)
        scrollContent.add(Box.createVerticalStrut(18))
        renderCurrentProjectCard()

        // Existing Projects section
        scrollContent.add(sectionLabel("Recent Match Projects"))
        scrollContent.add(Box.createVerticalStrut(8))


        // List container (added directly; outer scroll pane handles scrolling)
        listContainer.layout = BoxLayout(listContainer, BoxLayout.Y_AXIS)
        listContainer.isOpaque = false
        listContainer.alignmentX = 0f
        scrollContent.add(listContainer)
        scrollContent.add(Box.createVerticalStrut(8))

        // Pagination bar
        val pagination = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            UiStyles.styleSecondary(prevBtn)
            UiStyles.styleSecondary(nextBtn)
            pageLabel.foreground = FG_SECONDARY
            prevBtn.addActionListener { goToPage((currentPage - 1).coerceAtLeast(1)) }
            nextBtn.addActionListener { goToPage((currentPage + 1).coerceAtMost(totalPages())) }
            add(prevBtn)
            add(Box.createHorizontalStrut(8))
            add(pageLabel)
            add(Box.createHorizontalStrut(8))
            add(nextBtn)
            add(Box.createHorizontalGlue())
        }
        scrollContent.add(pagination)

        add(scrollContent.wrapIntoTransparent(), BorderLayout.CENTER)

        // Initial data load
        refreshRecents(false)
    }

    // region — Rendering helpers

    private fun renderCurrentProjectCard() {
        currentProjectContainer.removeAll()
        val card = if (currentProjectPath.isNullOrBlank()) {
            emptyCurrentProjectCard()
        } else {
            buildCurrentProjectCard(currentProjectPath!!)
        }
        currentProjectContainer.add(card, BorderLayout.CENTER)
        currentProjectContainer.revalidate()
        currentProjectContainer.repaint()
    }

    private fun buildCurrentProjectCard(path: String): JComponent {
        val card = cardPanel()
        val inner = JPanel()
        inner.isOpaque = false
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        try {
            val mf = ManifestIO.read(path)
            val title = JLabel(mf.name.ifBlank { File(path).nameWithoutExtension }).apply {
                font = font.deriveFont(Font.BOLD, font.size2D + 1f)
                foreground = FG_PRIMARY
            }
            val help = JLabel(path).apply { foreground = FG_SECONDARY }
            inner.add(title)
            inner.add(Box.createVerticalStrut(4))
            inner.add(help)
        } catch (_: Throwable) {
            val title = JLabel(File(path).nameWithoutExtension).apply {
                font = font.deriveFont(Font.BOLD, font.size2D + 1f)
                foreground = FG_PRIMARY
            }
            val help = JLabel(path).apply { foreground = FG_SECONDARY }
            inner.add(title)
            inner.add(Box.createVerticalStrut(4))
            inner.add(help)
        }
        card.add(inner, BorderLayout.CENTER)
        return card
    }

    private fun emptyCurrentProjectCard(): JComponent {
        val card = cardPanel()
        val inner = JPanel()
        inner.isOpaque = false
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        val title = JLabel("No open project").apply { font = font.deriveFont(Font.BOLD, font.size2D + 1f); foreground = FG_PRIMARY }
        val help = JLabel("Use \"IMPORT NEW MATCH\" or open from Existing Projects").apply { foreground = FG_SECONDARY }
        inner.add(title)
        inner.add(Box.createVerticalStrut(4))
        inner.add(help)
        card.add(inner, BorderLayout.CENTER)
        return card
    }

    private fun sectionLabel(text: String): JComponent = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD, font.size2D + 2f)
        foreground = FG_PRIMARY
        alignmentX = 0f
    }

    private fun headerTitle(text: String): JComponent = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD, 22f)
        foreground = FG_PRIMARY
    }

    private fun headerSubtitle(text: String): JComponent = JLabel(text).apply {
        font = font.deriveFont(font.size2D - 1f)
        foreground = FG_SECONDARY
    }

    private fun cardPanel(): JPanel = object : JPanel(BorderLayout()) {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
        }
    }.apply {
        isOpaque = true
        background = CARD_BG
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        )
        alignmentX = 0f
        // Allow vertical growth; don't clamp to 0 before content is added
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        minimumSize = Dimension(200, 48)
    }

    private fun buildProjectCard(entry: RecentsProvider.RecentEntry): JComponent {
        val card = cardPanel()
        val center = JPanel()
        center.isOpaque = false
        center.layout = BoxLayout(center, BoxLayout.Y_AXIS)

        val title = JLabel(entry.name).apply { foreground = FG_PRIMARY; font = font.deriveFont(Font.BOLD) }
        val secondary = JLabel(resolveSecondaryLine(entry)).apply { foreground = FG_SECONDARY }
        center.add(title)
        center.add(Box.createVerticalStrut(2))
        center.add(secondary)

        val openBtn = UiStyles.primarySmallButton("Open Project") { openManifestPath(entry.path, this@SwingProjectsPanel) }

        val right = JPanel(BorderLayout())
        right.isOpaque = false
        right.add(openBtn, BorderLayout.EAST)

        card.layout = BorderLayout()
        card.add(center, BorderLayout.CENTER)
        card.add(right, BorderLayout.EAST)

        // Open on double-click anywhere on the card
        card.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openBtn.doClick()
                }
            }
        })
        return card
    }

    private fun resolveSecondaryLine(entry: RecentsProvider.RecentEntry): String {
        return try {
            val mf = ManifestIO.read(entry.path)
            val video = mf.sourceVideo
            if (!video.isNullOrBlank()) video else entry.path
        } catch (_: Throwable) {
            entry.path
        }
    }

    private fun Container.wrapIntoTransparent(): JScrollPane {
        val sp = JScrollPane(this)
        sp.border = BorderFactory.createEmptyBorder()
        sp.isOpaque = false
        sp.viewport.isOpaque = false
        sp.verticalScrollBar.unitIncrement = 18
        return sp
    }

    private fun primaryButton(text: String, action: () -> Unit): JButton = object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val r = 12
            val hover = model.isRollover
            val pressed = model.isArmed && model.isPressed
            val c1 = if (pressed) GRADIENT_START.darker() else if (hover) GRADIENT_START_HOVER else GRADIENT_START
            val c2 = if (pressed) GRADIENT_END.darker() else if (hover) GRADIENT_END_HOVER else GRADIENT_END
            val paint = GradientPaint(0f, 0f, c1, w.toFloat(), h.toFloat(), c2)
            g2.paint = paint
            g2.fillRoundRect(0, 0, w, h, r, r)
            // draw children (icon + text)
            super.paintComponent(g)
        }
    }.apply {
        addActionListener { action() }
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isRolloverEnabled = true
        border = BorderFactory.createEmptyBorder(10, 20, 10, 20)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        icon = PlusInCircleIcon(18, ICON_CIRCLE_DARK, ICON_PLUS_LIGHT)
        iconTextGap = 10
        foreground = TEXT_ON_PRIMARY
        font = font.deriveFont(Font.BOLD, font.size2D + 1.5f)
        isFocusPainted = false
    }

    private fun primarySmallButton(text: String, onClick: () -> Unit): JButton = JButton(text).apply {
        addActionListener { onClick() }
        background = GREEN
        foreground = Color.BLACK
        isOpaque = true
        border = BorderFactory.createEmptyBorder(4, 10, 4, 10)
        font = font.deriveFont(Font.BOLD)
        isFocusPainted = false
    }

    private fun styleSecondaryButton(btn: AbstractButton) {
        btn.isOpaque = true
        btn.background = SURFACE_HIGH
        btn.foreground = FG_PRIMARY
        btn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        )
        btn.isFocusPainted = false
        btn.font = btn.font.deriveFont(Font.BOLD)
    }

    // Small painter for the leading plus-in-circle icon on the primary CTA
    private class PlusInCircleIcon(
        private val size: Int,
        private val circleColor: Color,
        private val plusColor: Color
    ) : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            if (g == null) return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val r = size
            // Circle
            g2.color = circleColor
            g2.fillOval(x, y, r, r)
            // Plus
            g2.color = plusColor
            val bar = (r * 0.16).toInt().coerceAtLeast(2)
            val len = (r * 0.52).toInt()
            val cx = x + r / 2
            val cy = y + r / 2
            // horizontal
            g2.fillRoundRect(cx - len / 2, cy - bar / 2, len, bar, bar, bar)
            // vertical
            g2.fillRoundRect(cx - bar / 2, cy - len / 2, bar, len, bar, bar)
        }
    }

    // endregion

    // region — Data & pagination

    private fun refreshRecents(fromCache: Boolean) {
        items = if (fromCache) RecentsProvider.current() else RecentsProvider.refresh()
        currentPage = 1
        renderList()
    }

    private fun renderList() {
        listContainer.removeAll()
        val total = items.size
        if (total == 0) {
            listContainer.add(cardPanel().apply {
                add(JLabel("No projects yet. Click \"IMPORT NEW MATCH\" to create one.").apply { foreground = FG_SECONDARY }, BorderLayout.CENTER)
            })
        } else {
            val (start, endExclusive) = pageBounds()
            items.subList(start, endExclusive).forEach { entry ->
                listContainer.add(buildProjectCard(entry))
                listContainer.add(Box.createVerticalStrut(8))
            }
        }
        listContainer.revalidate()
        listContainer.repaint()
        updatePagination()
    }

    private fun pageBounds(): Pair<Int, Int> {
        val start = (currentPage - 1) * pageSize
        val endExclusive = (start + pageSize).coerceAtMost(items.size)
        return Pair(start, endExclusive)
    }

    private fun goToPage(page: Int) {
        val tp = totalPages()
        val newPage = page.coerceIn(1, if (tp == 0) 1 else tp)
        if (newPage != currentPage) {
            currentPage = newPage
            renderList()
        }
    }

    private fun totalPages(): Int = if (items.isEmpty()) 1 else ((items.size + pageSize - 1) / pageSize)

    private fun updatePagination() {
        val tp = totalPages()
        pageLabel.text = "Page $currentPage / $tp"
        prevBtn.isEnabled = currentPage > 1
        nextBtn.isEnabled = currentPage < tp
        // Hide pagination bar if only 1 page exists
        val paginationBar = pageLabel.parent
        paginationBar?.isVisible = tp > 1
    }

    // endregion

    // region — Actions (open/create)

    private fun onOpenManifest(parent: Component) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Open Project Manifest"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = true
            fileFilter = FileNameExtensionFilter("Tennis Record Project (*.trproj)", "trproj")
            val root = RecentsProvider.projectsRoot()
            if (root.exists()) currentDirectory = root
        }
        val res = chooser.showOpenDialog(parent)
        if (res == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile ?: return
            openManifestPath(file.absolutePath, parent)
        }
    }

    private fun onNewProject(parent: Component) {
        try {
            val lastDirPath = prefs.get("lastVideoDir", null)
            val chooser = JFileChooser().apply {
                dialogTitle = "Select Source Video"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter("Video Files", "mp4", "mov", "mkv", "avi", "m4v", "wmv")
                lastDirPath?.let { p ->
                    val d = File(p)
                    if (d.exists()) currentDirectory = d
                }
            }
            val result = chooser.showOpenDialog(parent)
            if (result != JFileChooser.APPROVE_OPTION) return
            val selected = chooser.selectedFile ?: return
            prefs.put("lastVideoDir", selected.parentFile.absolutePath)

            val projectsRoot = RecentsProvider.projectsRoot().apply { if (!exists()) mkdirs() }

            val baseName = selected.name.substringBeforeLast('.')
            var projectName = baseName
            var projectDir = File(projectsRoot, projectName)
            var suffix = 2
            while (projectDir.exists()) {
                projectName = "$baseName (${suffix++})"
                projectDir = File(projectsRoot, projectName)
            }
            if (!projectDir.mkdirs()) projectDir.mkdirs()

            val now = ManifestIO.nowIsoUtc()
            val manifest = ProjectManifestV1(
                id = java.util.UUID.randomUUID().toString(),
                name = projectName,
                createdAt = now,
                lastOpenedAt = now,
                version = 1,
                sourceVideo = selected.absolutePath
            )
            val manifestPath = File(projectDir, "$projectName.trproj").absolutePath
            ManifestIO.write(manifestPath, manifest)

            // Update current project card, recents & navigate
            currentProjectPath = manifestPath
            renderCurrentProjectCard()
            refreshRecents(false)
            onProjectOpened?.invoke(manifestPath)
        } catch (t: Throwable) {
            SwingDialogUtils.showError(parent, t, "Failed to create project")
        }
    }

    private fun openManifestPath(path: String, parentComponent: Component) {
        try {
            val mf = ManifestIO.read(path)
            val now = ManifestIO.nowIsoUtc()
            var updated = mf.copy(lastOpenedAt = now)
            var chosenVideo: String? = mf.sourceVideo
            if (chosenVideo.isNullOrBlank()) {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Select Source Video for Project: ${mf.name}"
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    isAcceptAllFileFilterUsed = false
                    fileFilter = FileNameExtensionFilter("Video Files", "mp4", "mov", "mkv", "avi", "m4v", "wmv")
                    val lastDirPath = prefs.get("lastVideoDir", null)
                    lastDirPath?.let { p ->
                        val d = File(p)
                        if (d.exists()) currentDirectory = d
                    }
                }
                val res = chooser.showOpenDialog(parentComponent)
                if (res == JFileChooser.APPROVE_OPTION) {
                    val v = chooser.selectedFile
                    if (v != null) {
                        chosenVideo = v.absolutePath
                        prefs.put("lastVideoDir", v.parentFile.absolutePath)
                    }
                }
            }
            if (!chosenVideo.isNullOrBlank()) {
                updated = updated.copy(sourceVideo = chosenVideo)
            }
            ManifestIO.write(path, updated)
            // Update current project card, recents & navigate
            currentProjectPath = path
            renderCurrentProjectCard()
            refreshRecents(false)
            onProjectOpened?.invoke(path)
        } catch (t: Throwable) {
            SwingDialogUtils.showError(parentComponent, t, "Failed to open project")
        }
    }

    // endregion

    companion object Theme {
        // Palette inspired by design/projects.html dark theme
        private val DARK_BG = Color(0x0E, 0x0E, 0x0E)            // background / surface-dim
        private val SURFACE_HIGH = Color(0x20, 0x20, 0x1F)       // surface-container-high
        private val CARD_BG = Color(0x1A, 0x1A, 0x1A)            // surface-container
        private val CARD_BORDER = Color(0x26, 0x26, 0x26)        // surface-variant border
        private val FG_PRIMARY = Color(0xFF, 0xFF, 0xFF)         // on-surface
        private val FG_SECONDARY = Color(0xAD, 0xAA, 0xAA)       // on-surface-variant
        private val GREEN = Color(0xA1, 0xFE, 0x00)              // primary-fixed

        // CTA gradient (mock: light lime to bright neon green)
        private val GRADIENT_START = Color(0xDD, 0xFF, 0xB0)     // #ddffb0
        private val GRADIENT_END = Color(0xA1, 0xFE, 0x00)       // #a1fe00
        private val GRADIENT_START_HOVER = GRADIENT_START.brighter()
        private val GRADIENT_END_HOVER = GRADIENT_END.brighter()

        // CTA content colors to match mock
        private val TEXT_ON_PRIMARY = Color(0x2B, 0x49, 0x00)    // dark olive text
        private val ICON_CIRCLE_DARK = Color(0x3C, 0x43, 0x00)   // deep olive circle
        private val ICON_PLUS_LIGHT = Color(0xED, 0xFF, 0xC8)    // pale lime for plus
    }
}
