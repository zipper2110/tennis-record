package org.litvin

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.geometry.Side
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
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

/**
 * Projects screen matching design/projects.html (approximate in JavaFX)
 * - Left sidebar navigation
 * - Header with title/subtitle and primary CTA (Import New Match)
 * - Recent Match Projects grid (placeholder data)
 * - Footer activity bar
 */
class ProjectsTab(private val dispatcher: ProjectsDispatcher) {
    val view: Node = buildView()

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
        val brandIcon = Label("⬢").apply {
            styleClass.add("brand-icon")
        }
        val version = Label("v1.0.4").apply { styleClass.add("brand-version") }
        val brandBox = VBox(4.0, brandIcon, version).apply {
            alignment = Pos.CENTER
        }

        fun navItem(text: String, active: Boolean = false): Node = VBox(4.0).apply {
            val icon = Label("●").apply { styleClass.add(if (active) "nav-icon-active" else "nav-icon") }
            val label = Label(text).apply { styleClass.add(if (active) "nav-label-active" else "nav-label") }
            children.addAll(icon, label)
            alignment = Pos.CENTER
            styleClass.add("nav-item")
        }

        val nav = VBox(16.0,
            navItem("Projects", active = true),
            navItem("Markup"),
            navItem("Adjust"),
            navItem("Scoring"),
            navItem("Export")
        ).apply { alignment = Pos.TOP_CENTER }

        val settingsBtn = Button("⚙").apply {
            styleClass.add("settings-btn")
            setOnAction { println("[INFO] Settings clicked") }
        }
        val settingsBox = VBox(settingsBtn).apply { alignment = Pos.CENTER }

        return VBox().apply {
            prefWidth = 80.0
            minWidth = 80.0
            maxWidth = 80.0
            spacing = 24.0
            padding = Insets(16.0, 0.0, 16.0, 0.0)
            styleClass.add("sidebar")
            children.addAll(VBox(10.0, brandBox, nav).apply { alignment = Pos.TOP_CENTER; VBox.setVgrow(nav, Priority.ALWAYS) }, settingsBox)
        }
    }

    private fun buildMainContent(): Node {
        val container = VBox(16.0).apply {
            padding = Insets(24.0)
            styleClass.add("main")
        }

        // Header with CTA
        val title = Label("tennis record").apply {
            styleClass.add("header-title")
        }
        val subtitle = Label("TENNIS VIDEO ANALYTICS & EDITING SUITE").apply {
            styleClass.add("header-subtitle")
        }
        val titleBox = VBox(2.0, title, subtitle)

        val cta = Button("IMPORT NEW MATCH").apply {
            styleClass.add("cta-primary")
            contentDisplay = ContentDisplay.LEFT
            setOnAction { dispatcher.onNewProjectClicked() }
            isDefaultButton = true
        }

        val header = HBox(16.0, titleBox, Region()).apply {
            children.add(cta)
            HBox.setHgrow(children[1], Priority.ALWAYS) // spacer
            alignment = Pos.BOTTOM_LEFT
        }

        // Recent Projects title
        val recentTitle = Label("Recent Match Projects").apply { styleClass.add("section-title") }

        // Grid of cards (placeholder)
        val grid = FlowPane(16.0, 16.0).apply {
            prefWrapLength = 1000.0
        }
        grid.children.addAll(
            projectCard(
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAfbuvJVHlO56vBJVkpPulrcNzh5B9v5FhGRWiOb1oV_QQWo_mM_5QQrAX1MZ3Ac2ZBiu-XCmGYSipPBy86G4QM7TzaS8h-PYrOJI9nuMgeW3U5Gp_qpjfb1vlG21ubPgVE0wWAaJMZZvktRImnehxWY718pBhOXLGBNuvMZtgkF_4Qq-Zwue-v0U5Gm2dBHGOeoGPuuszniAjvSKASAEgc7CylzMEqr2bHkETkrd28Ztw-JZ6CDbO_dKkvS-_TlfOGLBkHbW7P4IBc",
                title = "Wimbledon Finals 2024",
                subtitle = "ALCARAZ VS. DJOKOVIC • 03:42:15"
            ),
            projectCard(
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuABIWdI4fm6jUrbZx15Mx_04UMYXKUrRr_vJ_wihFujz76jOsX8scj2cinr8wktuZA4N1GcRdHCucEp2a-q6QmDbfHQxgvLRcEtqGrF4cNY56K8w2AYciSbWBEiSNr0-xNRXVcRwfDueu5uejzwL12vyzYOhtHPOk5gLTvBcvmkb8uSlK0EPMaYKkE6Uk_VErxZgL4beLYQzfSeekrvk6H84B6hSK61ELWuejevCB1O7NRBWn5p3eZDjmNicrI36IpV9J-HvtYRM22i",
                title = "Backhand Drill Analysis",
                subtitle = "PLAYER: M. SVATEK • 00:15:20"
            ),
            projectCard(
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCJUFPw8Sw50RAALnMbrvyQ-oGdvn7ZYybB3myBDEh_DcamGSSuxcQD369yG6pi4eUF3G-ghX3EBRsqzMIx0B1oMmxteyHckrU4rJS6DIonWCEIyhED-EWQXJpY1m2BMyYpNQDD6HPKuVRgu61p78YBtJvz1ymrF9oAp-c1hL1SYAEpg3MW3A2y4k5HNrN6FqQDUprEXSUdtRms4qFDSq86rAHMyrl9h4IRQXAAIFkEyysJWy7MC9w71_hSWDp_ZqQVoZMGcco5bsmD",
                title = "Roland Garros R16",
                subtitle = "NADAL VS. ZVEREV • 04:12:44"
            )
        )

        // Footer activity bar
        val footer = buildFooterBar()

        container.children.addAll(header, recentTitle, grid, footer)
        VBox.setVgrow(grid, Priority.ALWAYS)
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
