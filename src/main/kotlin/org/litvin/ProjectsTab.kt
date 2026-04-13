package org.litvin

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import java.io.File

/**
 * Projects screen matching design/projects.html (approximate in JavaFX)
 * - Left sidebar navigation
 * - Header with title/subtitle and primary CTA (Import New Match)
 * - Recent Match Projects grid (placeholder data)
 * - Footer activity bar
 */
class ProjectsTab(private val dispatcher: ProjectsDispatcher) {

    // MRU UI state
    private var currentPage: Int = 0
    private val pageSize: Int = 12
    private lateinit var listBox: VBox
    private lateinit var paginationBar: HBox
    private lateinit var currentBox: VBox

    val view: Node = buildView()

    private fun refreshCurrent() {
        if (!::currentBox.isInitialized) return
        currentBox.children.clear()
        val path = ProjectsDispatcher.currentProjectPath
        if (path == null) {
            val row = HBox(8.0)
            val title = Label("No open project").apply { styleClass.add("card-title") }
            val subtitle = Label("Use 'IMPORT NEW MATCH' or open from Existing Projects").apply { styleClass.add("card-subtitle") }
            val textBox = VBox(2.0, title, subtitle)
            row.children.addAll(textBox)
            row.styleClass.add("card")
            row.padding = Insets(8.0)
            currentBox.children.add(row)
            return
        }
        // Render current project in the same row layout as recents
        val name: String
        var videoPath: String
        name = try {
            val mf = ManifestIO.read(path)
            videoPath = mf.sourceVideo?.takeIf { it.isNotBlank() } ?: "(no source video)"
            mf.name.ifBlank { File(path).nameWithoutExtension }
        } catch (_: Throwable) {
            videoPath = "(no source video)"
            File(path).nameWithoutExtension
        }
        val row = HBox(8.0)
        val nameLbl = Label(name).apply { styleClass.add("card-title") }
        val pathLbl = Label(videoPath).apply { styleClass.add("card-subtitle") }
        val textBox = VBox(2.0, nameLbl, pathLbl)
        val openBtn = Button("Open").apply {
            styleClass.add("cta-primary")
            setOnAction {
                dispatcher.openProject(path)
                RecentsProvider.refresh()
                refreshList()
                refreshCurrent()
            }
        }
        row.children.addAll(textBox, Region(), HBox(6.0, openBtn))
        HBox.setHgrow(row.children[1], Priority.ALWAYS)
        row.styleClass.add("card")
        row.padding = Insets(8.0)
        currentBox.children.add(row)
    }

    private fun refreshList() {
        val entries = RecentsProvider.current()
        listBox.children.clear()

        if (entries.isEmpty()) {
            listBox.children.add(Label("No projects found. Click 'IMPORT NEW MATCH' to create one."))
            paginationBar.children.setAll()
            return
        }

        val res = Pagination.compute(entries.size, currentPage, pageSize)
        currentPage = res.currentPage
        val totalPages = res.totalPages
        val pageItems = entries.subList(res.fromIndex, res.toIndex)

        pageItems.forEach { e ->
            val row = HBox(8.0)
            val nameLbl = Label(e.name).apply { styleClass.add("card-title") }
            val videoPath = try { ManifestIO.read(e.path).sourceVideo?.takeIf { it.isNotBlank() } ?: "(no source video)" } catch (_: Throwable) { "(no source video)" }
            val pathLbl = Label(videoPath).apply { styleClass.add("card-subtitle") }
            val textBox = VBox(2.0, nameLbl, pathLbl)
            val openBtn = Button("Open").apply {
                styleClass.add("cta-primary")
                setOnAction {
                    dispatcher.openProject(e.path)
                    RecentsProvider.refresh()
                    refreshList()
                    refreshCurrent()
                }
            }
            row.children.addAll(textBox, Region(), HBox(6.0, openBtn))
            HBox.setHgrow(row.children[1], Priority.ALWAYS)
            row.styleClass.add("card")
            row.padding = Insets(8.0)
            listBox.children.add(row)
        }

        val prev = Button("Prev").apply {
            isDisable = currentPage == 0
            setOnAction { currentPage--; refreshList() }
        }
        val pageInfo = Label("Page ${currentPage + 1} / $totalPages")
        val next = Button("Next").apply {
            isDisable = currentPage >= totalPages - 1
            setOnAction { currentPage++; refreshList() }
        }
        paginationBar.children.setAll(HBox(8.0, prev, pageInfo, next))
    }

    private fun buildView(): Node {
        val root = HBox().apply {
            styleClass.add("tr-root")
        }

        // Sidebar (fixed width ~80px)
        val sidebar = buildSidebar()

        // Main content area
        val main = buildMainContent()

        root.children.addAll(sidebar, main)
        return root
    }

    private fun buildSidebar(): Node {
        return org.litvin.markup.AppSidebar.build(
            active = org.litvin.markup.AppSidebar.Active.PROJECTS,
            onProjects = { /* already here */ },
            onMarkup = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.MARKUP) },
            onExport = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.EXPORT) },
            onVideoTest = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.VIDEO_TEST) }
        )
    }

    private fun buildMainContent(): Node {
        val container = VBox(16.0).apply {
            padding = Insets(24.0)
            styleClass.add("main")
        }

        // Header with CTA
        val title = Label("tennis record").apply { styleClass.add("header-title") }
        val subtitle = Label("TENNIS VIDEO ANALYTICS & EDITING SUITE").apply { styleClass.add("header-subtitle") }
        val titleBox = VBox(2.0, title, subtitle)

        val cta = Button("IMPORT NEW MATCH").apply {
            styleClass.add("cta-primary")
            contentDisplay = ContentDisplay.LEFT
            setOnAction {
                dispatcher.onNewProjectClicked()
                // After creating a project, refresh recents and current header
                refreshList()
                refreshCurrent()
            }
            isDefaultButton = true
        }

        val header = HBox(16.0, titleBox, Region()).apply {
            children.add(cta)
            HBox.setHgrow(children[1], Priority.ALWAYS) // spacer
            alignment = Pos.BOTTOM_LEFT
        }

        // Current project section (header + box)
        val currentTitle = Label("Current Project").apply { styleClass.add("section-title") }
        currentBox = VBox(8.0)
        refreshCurrent()

        // Recent Projects title
        val recentTitle = Label("Existing Projects").apply { styleClass.add("section-title") }

        // List with pagination
        listBox = VBox(8.0)
        paginationBar = HBox(8.0)
        refreshList()

        // Footer activity bar
        val footer = buildFooterBar()

        container.children.addAll(header, currentTitle, currentBox, recentTitle, listBox, paginationBar, footer)
        VBox.setVgrow(listBox, Priority.ALWAYS)
        return container
    }

    private fun projectCard(imageUrl: String, title: String, subtitle: String): Node {
        val image = ImageView(Image(imageUrl, 480.0, 0.0, true, true)).apply {
            fitHeight = 180.0
            isPreserveRatio = true
        }

        val gradient = Rectangle().apply {
            height = 180.0
            arcWidth = 6.0; arcHeight = 6.0
            fill = LinearGradient(0.0, 1.0, 0.0, 0.0, true, CycleMethod.NO_CYCLE,
                Stop(0.0, Color.rgb(0,0,0,0.8)), Stop(1.0, Color.TRANSPARENT))
        }

        val chipRow = HBox(6.0,
            chip("4K 60FPS", primary = true),
            chip("PRO TOUR"),
            chip("POINTS: GREEN", primary = true),
            chip("SCORING 85%")
        ).apply { padding = Insets(0.0,0.0,8.0,8.0) }

        val imageStack = StackPane(image, gradient, VBox().apply {
            children.add(Region())
            children.add(chipRow)
            VBox.setVgrow(children[0], Priority.ALWAYS)
            padding = Insets(0.0)
        }).apply { prefHeight = 180.0 }

        val titleLabel = Label(title).apply { styleClass.add("card-title") }
        val subtitleLabel = Label(subtitle).apply { styleClass.add("card-subtitle") }
        val more = Label("⋮").apply { styleClass.add("card-more") }
        val header = HBox(8.0, VBox(2.0, titleLabel, subtitleLabel), Region(), more)
        HBox.setHgrow(header.children[1], Priority.ALWAYS)

        val avatars = HBox(-6.0,
            avatar(Color.web("#66bb6a")),
            avatar(Color.web("#90caf9"))
        )
        val openBtn = Button("Open Project").apply {
            styleClass.add("link-button")
            setOnAction { dispatcher.onOpenProjectClicked() }
        }
        val footer = HBox(12.0, avatars, Region(), openBtn).apply {
            HBox.setHgrow(children[1], Priority.ALWAYS)
            styleClass.add("card-footer")
        }

        val card = VBox(10.0, imageStack, header, footer).apply {
            prefWidth = 320.0
            maxWidth = 340.0
            styleClass.add("card")
            padding = Insets(8.0, 8.0, 12.0, 8.0)
        }
        return card
    }

    private fun chip(text: String, primary: Boolean = false): Node = Label(text).apply {
        styleClass.addAll("chip", if (primary) "chip-primary" else "chip-dim")
        padding = Insets(2.0,6.0,2.0,6.0)
    }

    private fun avatar(color: Color): Node = StackPane(Circle(12.0, color)).apply {
        styleClass.add("avatar")
    }

    private fun buildFooterBar(): Node {
        val pulse = Region().apply {
            prefWidth = 10.0; prefHeight = 10.0
            styleClass.add("pulse-dot")
        }
        val activeLbl = Label("Active Export: SF Masters Highlights").apply { styleClass.add("footer-active-text") }
        val left = HBox(8.0, pulse, activeLbl).apply { alignment = Pos.CENTER_LEFT }

        val metricLabel = VBox(0.0,
            Label("Rendering Frame").apply { styleClass.add("metric-caption") },
            Label("14,204 / 45,900").apply { styleClass.add("metric-value") }
        ).apply { alignment = Pos.CENTER_RIGHT }

        val progressFill = Region().apply { styleClass.add("progress-fill"); prefWidth = 120.0; prefHeight = 6.0 }
        val progressBar = StackPane(Region().apply { styleClass.add("progress-track") }, progressFill).apply { prefWidth = 160.0; maxWidth = 160.0 }

        val right = HBox(16.0, metricLabel, progressBar).apply { alignment = Pos.CENTER_RIGHT }

        return HBox(16.0, left, Region(), right).apply {
            HBox.setHgrow(children[1], Priority.ALWAYS)
            styleClass.add("footer-bar")
            padding = Insets(16.0)
        }
    }
}
