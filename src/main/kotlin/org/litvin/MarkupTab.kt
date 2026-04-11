package org.litvin

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle

/**
 * Markup tab shell (Task 2.1):
 * - Viewer placeholder
 * - Transport controls with Play/Pause and jump ±10s (icons not required yet)
 * - Point Controls: Point Start [I], Point End [O]
 * - Points list area with a visible count badge (non-functional for now)
 */
class MarkupTab(private val dispatcher: MarkupDispatcher) {

    val view: Node = buildView()

    private fun buildView(): Node {
        val root = BorderPane()

        // Right sidebar: Points list
        val pointsHeader = HBox(8.0).apply {
            padding = Insets(8.0)
            alignment = Pos.CENTER_LEFT
            children.add(Label("Marked points").apply { style = "-fx-font-weight: bold; -fx-text-fill: #ddd;" })
            children.add(Region().apply { HBox.setHgrow(this, Priority.ALWAYS) })
            children.add(Label("0 MARKED").apply {
                style = "-fx-background-color: #a1fe00; -fx-text-fill: #2b4900; -fx-padding: 2 6 2 6; -fx-font-weight: bold; -fx-background-radius: 2;"
            })
        }
        val pointsList = ListView<String>().apply {
            placeholder = Label("No points yet")
        }
        val right = VBox(pointsHeader, pointsList).apply {
            prefWidth = 280.0
            style = "-fx-background-color: #1f1f1f; -fx-border-color: #2d2d2d; -fx-border-width: 0 0 0 1;"
        }
        root.right = right

        // Center: Viewer + transport
        val viewer = StackPane().apply {
            children.add(Rectangle(800.0, 450.0, Color.web("#0e0e0e")).apply {
                arcWidth = 2.0; arcHeight = 2.0
                stroke = Color.web("#2d2d2d"); strokeWidth = 1.0
            })
            padding = Insets(8.0)
            style = "-fx-background-color: #111;"
        }

        // Controls row under viewer
        val pointStart = Button("Point Start [I]").apply {
            setOnAction { dispatcher.onPointStartClicked() }
        }
        val pointEnd = Button("Point End [O]").apply {
            setOnAction { dispatcher.onPointEndClicked() }
        }
        val leftGroup = HBox(8.0, pointStart, pointEnd)

        val jumpBack = Button("⟲ 10s").apply { setOnAction { dispatcher.onJumpBackClicked() } }
        val playPause = Button("Play / Pause").apply { setOnAction { dispatcher.onPlayPauseClicked() } }
        val jumpFwd = Button("10s ⟲").apply { setOnAction { dispatcher.onJumpForwardClicked() } }
        val transport = HBox(12.0, jumpBack, playPause, jumpFwd).apply { alignment = Pos.CENTER }

        val timecode = Label("00:00:00.000").apply { style = "-fx-font-weight: bold; -fx-text-fill: white;" }
        val rightGroup = HBox(timecode).apply { alignment = Pos.CENTER_RIGHT }

        val topControls = HBox(16.0, leftGroup, Region(), transport, Region(), rightGroup).apply {
            padding = Insets(8.0)
            (children[1] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
            (children[3] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
            style = "-fx-background-color: #1a1a1a; -fx-border-color: #2d2d2d; -fx-border-width: 1 0 0 0;"
        }

        val centerBox = VBox(viewer, topControls)
        VBox.setVgrow(viewer, Priority.ALWAYS)
        root.center = centerBox

        return root
    }
}
