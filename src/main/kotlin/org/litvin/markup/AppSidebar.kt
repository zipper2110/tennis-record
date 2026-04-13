package org.litvin.markup

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Reusable sidebar component for all tabs.
 * Pass which section is active to style the correct item.
 */
object AppSidebar {
    enum class Active { PROJECTS, MARKUP, EXPORT, VIDEO_TEST }

    fun build(
        active: Active = Active.MARKUP,
        onProjects: (() -> Unit)? = null,
        onMarkup: (() -> Unit)? = null,
        onExport: (() -> Unit)? = null,
        onVideoTest: (() -> Unit)? = null,
    ): Node {
        val brandIcon = Label("⬢").apply { styleClass.add("brand-icon") }
        val version = Label("v1.0.4").apply { styleClass.add("brand-version") }
        val brandBox = VBox(4.0, brandIcon, version).apply { alignment = Pos.CENTER }

        fun navItem(text: String, isActive: Boolean = false, onClick: (() -> Unit)? = null): Node = VBox(4.0).apply {
            val icon = Label("●").apply { styleClass.add(if (isActive) "nav-icon-active" else "nav-icon") }
            val label = Label(text).apply { styleClass.add(if (isActive) "nav-label-active" else "nav-label") }
            children.addAll(icon, label)
            alignment = Pos.CENTER
            styleClass.add("nav-item")
            isFocusTraversable = false
            if (onClick != null) setOnMouseClicked { onClick.invoke() }
        }

        val nav = VBox(16.0,
            navItem("Projects", isActive = active == Active.PROJECTS) { onProjects?.invoke() },
            navItem("Markup", isActive = active == Active.MARKUP) { onMarkup?.invoke() },
            navItem("Adjust"),
            navItem("Scoring"),
            navItem("Export", isActive = active == Active.EXPORT) { onExport?.invoke() },
            navItem("Video test", isActive = active == Active.VIDEO_TEST) { onVideoTest?.invoke() }
        ).apply { alignment = Pos.TOP_CENTER }

        val settingsBtn = Button("⚙").apply {
            styleClass.add("settings-btn")
            isFocusTraversable = false
        }
        val settingsBox = VBox(settingsBtn).apply { alignment = Pos.CENTER }

        return VBox().apply {
            prefWidth = 80.0; minWidth = 80.0; maxWidth = 80.0
            spacing = 24.0
            padding = Insets(16.0, 0.0, 16.0, 0.0)
            styleClass.add("sidebar")
            children.addAll(VBox(10.0, brandBox, nav).apply {
                alignment = Pos.TOP_CENTER
                VBox.setVgrow(nav, Priority.ALWAYS)
            }, settingsBox)
        }
    }
}
