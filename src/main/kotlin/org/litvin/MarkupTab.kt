package org.litvin

import javafx.animation.AnimationTimer
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.Slider
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import java.io.File

/**
 * Markup tab (Task 2.1 done; extends for 2.2 playback):
 * - Loads project's sourceVideo on enter
 * - Play/Pause/Seek via controls and Spacebar
 * - Time display hh:mm:ss.mmm updates at least every 100 ms while playing
 * - Exposes getPlayheadMs() and seekTo(ms)
 */
class MarkupTab(private val dispatcher: MarkupDispatcher) {

    private val root = BorderPane()
    private val mediaView = MediaView().apply {
        // Disable smoothing to reduce scaling cost and improve performance
        try { this.isSmooth = false } catch (_: Throwable) {}
    }
    private var mediaPlayer: MediaPlayer? = null
    private val timeLabel = Label("00:00:00.000")
    private val seekSlider = Slider(0.0, 1.0, 0.0).apply {
        isDisable = true
        isFocusTraversable = false
        blockIncrement = 1000.0
        majorTickUnit = 10_000.0
        isShowTickMarks = false
        isShowTickLabels = false
    }
    private var isUserSeeking: Boolean = false
    // Cache last UI update time (ms) to avoid redundant updates/layout thrash
    private var lastUiMs: Long = -1L
    private var lastTickNs: Long = 0L
    private val ticker: AnimationTimer = object : AnimationTimer() {
        override fun handle(now: Long) {
            // Throttle to ~100 ms to reduce UI churn
            if (now - lastTickNs >= 100_000_000L) {
                updateTimeUI()
                lastTickNs = now
            }
        }
    }

    // Points UI
    private val pointsCountLabel = Label("0 MARKED").apply {
        style = "-fx-background-color: #a1fe00; -fx-text-fill: #2b4900; -fx-padding: 2 6 2 6; -fx-font-weight: bold; -fx-background-radius: 2;"
    }
    private val pointsList = ListView<String>().apply { placeholder = Label("No points yet") }

    private val viewInternal: Node by lazy { buildView() }
    val view: Node get() = viewInternal

    init {
        setupSeekSliderHandlers()
    }

    fun onEnter() {
        // Attempt to load current project's source video
        try {
            val manifestPath = ProjectsDispatcher.currentProjectPath ?: return
            val manifest = ManifestIO.read(manifestPath)
            val src = manifest.sourceVideo?.takeIf { it.isNotBlank() } ?: return
            loadMedia(File(src))
            // Focus to enable Spacebar
            view.requestFocus()
        } catch (_: Throwable) {
            // ignore for now; keep placeholder if load fails
        }
    }

    fun getPlayheadMs(): Long = mediaPlayer?.currentTime?.toMillis()?.toLong() ?: 0L

    fun seekTo(ms: Long) {
        mediaPlayer?.seek(Duration.millis(ms.coerceAtLeast(0L).toDouble()))
    }

    private fun loadMedia(file: File) {
        try {
            mediaPlayer?.dispose()
            val media = Media(file.toURI().toString())
            val player = MediaPlayer(media)
            player.setOnReady {
                            // Initialize seek slider and time display when media metadata is ready
                            val durMs = player.totalDuration.toMillis()
                            if (durMs.isFinite() && durMs > 0) {
                                seekSlider.max = durMs
                                seekSlider.isDisable = false
                            } else {
                                seekSlider.max = 1.0
                                seekSlider.isDisable = true
                            }
                            updateTimeUI()
                        }
            player.statusProperty().addListener { _, _, new ->
                if (new == MediaPlayer.Status.PLAYING) {
                    ticker.start()
                } else {
                    ticker.stop()
                    // Ensure UI shows the final time when paused/stopped
                    updateTimeUI()
                }
            }
            // While paused or during seeks, reflect time changes without a continuous timer
            player.currentTimeProperty().addListener { _, _, _ ->
                if (player.status != MediaPlayer.Status.PLAYING && !isUserSeeking) {
                    updateTimeUI()
                }
            }
            mediaView.mediaPlayer = player
            mediaPlayer = player
        } catch (t: Throwable) {
            System.err.println("[MARKUP] Failed to load media: ${t.message}")
        }
    }

    private fun togglePlayPause() {
        val p = mediaPlayer ?: return
        when (p.status) {
            MediaPlayer.Status.PLAYING -> p.pause()
            MediaPlayer.Status.PAUSED, MediaPlayer.Status.STOPPED, MediaPlayer.Status.READY, MediaPlayer.Status.HALTED, MediaPlayer.Status.UNKNOWN -> p.play()
            else -> p.play()
        }
        dispatcher.onPlayPauseClicked()
    }

    private fun jumpBy(deltaMs: Long) {
        val p = mediaPlayer ?: return
        val cur = p.currentTime
        val target = cur.toMillis() + deltaMs
        p.seek(Duration.millis(target.coerceAtLeast(0.0)))
        if (p.status != MediaPlayer.Status.PLAYING) updateTimeUI()
        if (deltaMs < 0) dispatcher.onJumpBackClicked() else dispatcher.onJumpForwardClicked()
    }

    private fun updateTimeUI() {
        val ms = getPlayheadMs()
        // Throttle UI updates to reduce layout/render overhead
        val status = mediaPlayer?.status
        val playing = status == MediaPlayer.Status.PLAYING
        if (lastUiMs >= 0) {
            val delta = kotlin.math.abs(ms - lastUiMs)
            val minDelta = if (playing) 80L else 0L // when playing, skip tiny changes (<~80ms)
            if (delta < minDelta) return
        }
        lastUiMs = ms
        // Update time label only when text would change
        timeLabel.text = formatMs(ms)
        // Update slider position if user is not dragging and change is meaningful
        if (!isUserSeeking) {
            val cur = seekSlider.value
            if (kotlin.math.abs(cur - ms) >= 10.0) {
                seekSlider.value = ms.toDouble()
            }
        }
    }

    private fun setupSeekSliderHandlers() {
        // Track when user is changing the slider (dragging)
        seekSlider.valueChangingProperty().addListener { _, _, changing ->
            isUserSeeking = changing
            if (!changing) {
                // Drag ended; perform seek
                mediaPlayer?.seek(Duration.millis(seekSlider.value))
            }
        }
        // Clicking on the slider (without drag)
        seekSlider.addEventHandler(MouseEvent.MOUSE_PRESSED) {
            isUserSeeking = true
        }
        seekSlider.addEventHandler(MouseEvent.MOUSE_RELEASED) {
            isUserSeeking = false
            mediaPlayer?.seek(Duration.millis(seekSlider.value))
        }
    }

    private fun buildView(): Node {
        // Right sidebar: Points list
        val pointsHeader = HBox(8.0).apply {
            padding = Insets(8.0)
            alignment = Pos.CENTER_LEFT
            children.add(Label("Marked points").apply { style = "-fx-font-weight: bold; -fx-text-fill: #ddd;" })
            children.add(Region().apply { HBox.setHgrow(this, Priority.ALWAYS) })
            children.add(pointsCountLabel)
        }
        val right = VBox(pointsHeader, pointsList).apply {
            prefWidth = 280.0
            style = "-fx-background-color: #1f1f1f; -fx-border-color: #2d2d2d; -fx-border-width: 0 0 0 1;"
        }
        root.right = right

        // Center: Viewer + transport
        val viewer = StackPane(mediaView).apply {
            padding = Insets(8.0)
            style = "-fx-background-color: #111;"
        }
        mediaView.fitWidthProperty().bind(viewer.widthProperty())
        mediaView.fitHeightProperty().bind(viewer.heightProperty().subtract(100.0)) // leave room for controls visually
        mediaView.isPreserveRatio = true

        // Controls row under viewer
        val pointStart = Button("Point Start [C]").apply {
            setOnAction {
                dispatcher.onPointStart(getPlayheadMs())
                refreshPointsUI()
            }
        }
        val pointEnd = Button("Point End [V]").apply {
            setOnAction {
                dispatcher.onPointEnd(getPlayheadMs())
                refreshPointsUI()
            }
        }
        val leftGroup = HBox(8.0, pointStart, pointEnd)

        val jumpBack = Button("⟲ 10s").apply { setOnAction { jumpBy(-10_000) } }
        val playPause = Button("Play / Pause").apply { setOnAction { togglePlayPause() } }
        val jumpFwd = Button("10s ⟲").apply { setOnAction { jumpBy(10_000) } }
        val transport = HBox(12.0, jumpBack, playPause, jumpFwd).apply { alignment = Pos.CENTER }

        timeLabel.style = "-fx-font-weight: bold; -fx-text-fill: white;"
        val rightGroup = HBox(timeLabel).apply { alignment = Pos.CENTER_RIGHT }

        val topControls = HBox(16.0, leftGroup, Region(), transport, Region(), rightGroup).apply {
            padding = Insets(8.0)
            (children[1] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
            (children[3] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
            style = "-fx-background-color: #1a1a1a; -fx-border-color: #2d2d2d; -fx-border-width: 1 0 0 0;"
        }

        // Scrubbing slider under the controls
        seekSlider.maxWidth = Double.MAX_VALUE
        val centerBox = VBox(viewer, topControls, seekSlider)
        VBox.setVgrow(viewer, Priority.ALWAYS)
        VBox.setVgrow(seekSlider, Priority.NEVER)
        root.center = centerBox

        // Keyboard: Space toggles play/pause; C/V mark start/end; Arrows seek (Shift = 10s, plain = 1s)
        root.setOnKeyPressed { e ->
            when (e.code) {
                KeyCode.SPACE -> {
                    togglePlayPause()
                    e.consume()
                }
                KeyCode.C -> {
                    if (!e.isShortcutDown && !e.isAltDown && !e.isShiftDown) {
                        dispatcher.onPointStart(getPlayheadMs())
                        refreshPointsUI()
                        e.consume()
                    }
                }
                KeyCode.V -> {
                    if (!e.isShortcutDown && !e.isAltDown && !e.isShiftDown) {
                        dispatcher.onPointEnd(getPlayheadMs())
                        refreshPointsUI()
                        e.consume()
                    }
                }
                KeyCode.LEFT -> {
                    val delta = if (e.isShiftDown) -10_000L else -1_000L
                    jumpBy(delta)
                    e.consume()
                }
                KeyCode.RIGHT -> {
                    val delta = if (e.isShiftDown) 10_000L else 1_000L
                    jumpBy(delta)
                    e.consume()
                }
                else -> {}
            }
        }

        return root
    }

    private fun refreshPointsUI() {
        val items = mutableListOf<String>()
        val completed = dispatcher.getCompletedPoints().sortedBy { it.startMs }
        val pending = dispatcher.getPendingStart()
        if (pending != null) {
            items.add("• Pending — Start ${formatMs(pending.toLong())} — End —")
        }
        items.addAll(completed.mapIndexed { idx, p ->
            val idx1 = idx + 1
            val dur = p.endMs - p.startMs
            "#${idx1.toString().padStart(2, '0')}  ${formatMs(p.startMs.toLong())}  →  ${formatMs(p.endMs.toLong())}  (" + (dur/1000.0).let { String.format("%.3fs", it) } + ")"
        })
        pointsList.items.setAll(items)
        pointsCountLabel.text = "${completed.size} MARKED"
    }

    private fun formatMs(totalMs: Long): String {
        val ms = (totalMs % 1000).toInt()
        val totalSeconds = totalMs / 1000
        val s = (totalSeconds % 60).toInt()
        val totalMinutes = totalSeconds / 60
        val m = (totalMinutes % 60).toInt()
        val h = (totalMinutes / 60).toInt()
        return String.format("%02d:%02d:%02d.%03d", h, m, s, ms)
    }
}
