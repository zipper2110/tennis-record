package org.litvin

import javafx.animation.AnimationTimer
import javafx.animation.PauseTransition
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TableRow
import javafx.beans.property.SimpleStringProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.stage.Popup
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
    private var toastPopup: Popup? = null

    private fun showToast(message: String) {
        val owner = root.scene?.window ?: return
        // Reuse popup if visible
        toastPopup?.hide()
        val box = HBox().apply {
            style = "-fx-background-color: rgba(0,0,0,0.85); -fx-padding: 8 12; -fx-background-radius: 6; -fx-border-radius: 6; -fx-border-color: rgba(255,255,255,0.15); -fx-border-width: 1;"
            children.add(Label(message).apply { style = "-fx-text-fill: white; -fx-font-size: 12px;" })
        }
        val popup = Popup().apply {
            isAutoFix = true
            isAutoHide = true
            content.clear()
            content.add(box)
        }
        toastPopup = popup
        val x = owner.x + owner.width / 2 - 150
        val y = owner.y + owner.height - 100
        popup.show(owner, x, y)
        PauseTransition(Duration.millis(2200.0)).apply {
            setOnFinished { popup.hide() }
            play()
        }
    }

    private fun maybeShowDispatcherMessage() {
        val msg = dispatcher.consumeUserMessage()
        if (msg != null) showToast(msg)
    }

    private val root = BorderPane()
    private val mediaView = MediaView().apply {
        // Disable smoothing to reduce scaling cost and improve performance
        try { this.isSmooth = false } catch (_: Throwable) {}
    }
    private var mediaPlayer: MediaPlayer? = null
    private val timeLabel = Label("00:00:00.000")
    private var playIconLabel: Label? = null
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
    // Points list rendered as card-like rows (closer to HTML mock)
    private data class Row(
        val order: Int,
        val id: String,
        val startMs: Int,
        val endMs: Int?,
        val label: String?,
        val isPending: Boolean = false,
    )
    private val pointsList = javafx.scene.control.ListView<Row>().apply {
        placeholder = Label("No points yet").apply { style = "-fx-text-fill: #777;" }
        isFocusTraversable = true
        style = "-fx-background-color: #1f1f1f; -fx-control-inner-background: #1f1f1f; -fx-control-inner-background-alt: #1f1f1f; -fx-background-insets: 0; -fx-padding: 8;"
    }

    private val viewInternal: Node by lazy { buildView() }
    val view: Node get() = viewInternal

    // Autosave debounce (300 ms)
    private val autosaveDebounce = PauseTransition(Duration.millis(300.0)).apply {
        setOnFinished { performSaveEdl() }
    }

    init {
        setupSeekSliderHandlers()
        setupWindowCloseFlush()
        // Subscribe to points changes for autosave and UI refresh
        dispatcher.onPointsChanged = {
            // schedule save with debounce
            autosaveDebounce.stop()
            autosaveDebounce.playFromStart()
            // Also refresh UI count/list ordering
            refreshPointsUI()
        }
    }

    fun onEnter() {
        // Attempt to load current project's source video and EDL
        try {
            val manifestPath = ProjectsDispatcher.currentProjectPath ?: return
            val manifest = ManifestIO.read(manifestPath)
            val src = manifest.sourceVideo?.takeIf { it.isNotBlank() }
            if (src != null) {
                loadMedia(File(src))
            }
            // Load existing points from edl.json for this project
            val projectDir = EdlIO.projectDirFromManifest(manifestPath)
            val edl = EdlIO.readForProjectDir(projectDir)
            dispatcher.setPoints(edl.points)
            refreshPointsUI()
            // Focus to enable Spacebar
            view.requestFocus()
        } catch (t: Throwable) {
            System.err.println("[MARKUP] Failed to initialize Markup tab: ${t.message}")
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
                            updatePlayButtonIcon()
                        }
            player.statusProperty().addListener { _, _, new ->
                if (new == MediaPlayer.Status.PLAYING) {
                    ticker.start()
                } else {
                    ticker.stop()
                    // Ensure UI shows the final time when paused/stopped
                    updateTimeUI()
                }
                // Update play/pause icon according to new status
                updatePlayButtonIcon()
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
        // Update icon immediately for snappy UI; listener will also adjust once status changes
        updatePlayButtonIcon()
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
        timeLabel.text = Timecode.format(ms)
        // Update slider position if user is not dragging and change is meaningful
        if (!isUserSeeking) {
            val cur = seekSlider.value
            if (kotlin.math.abs(cur - ms) >= 10.0) {
                seekSlider.value = ms.toDouble()
            }
        }
    }

    private fun updatePlayButtonIcon() {
        val icon = playIconLabel ?: return
        val status = mediaPlayer?.status
        // Use proper Unicode icons: ▶ (U+25B6) for play, ⏸ (U+23F8) for pause
        icon.text = if (status == MediaPlayer.Status.PLAYING) "\u23F8" else "\u25B6"
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
        val totalsLabel = Label("Total: 0 points — 00:00:00.000").apply {
            style = "-fx-text-fill: #adaaaa; -fx-font-size: 11px; -fx-padding: 6;"
        }
        val right = VBox(pointsHeader, pointsList, totalsLabel).apply {
            prefWidth = 280.0
            style = "-fx-background-color: #1f1f1f; -fx-border-color: #2d2d2d; -fx-border-width: 0 0 0 1;"
        }
        // store totalsLabel reference for updates
        right.properties["totalsLabel"] = totalsLabel
        root.right = right

        // List cells: card-like to resemble the mock (title/duration and start/end row)
        pointsList.setCellFactory {
            object : javafx.scene.control.ListCell<Row>() {
                private val title = Label().apply { style = "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #adaaaa; -fx-text-transform: uppercase; -fx-letter-spacing: 1px;" }
                private val duration = Label().apply { style = "-fx-font-size: 10px; -fx-text-fill: #adaaaa; -fx-font-family: monospace;" }
                private val goBtn = Button("◎").apply {
                    style = "-fx-background-color: #2a2a2a; -fx-text-fill: #adaaaa; -fx-padding: 2 6 2 6;"
                    setOnAction {
                        val r = item ?: return@setOnAction
                        seekTo(r.startMs.toLong())
                        root.requestFocus()
                    }
                }
                private val deleteBtn = Button("✕").apply {
                    style = "-fx-background-color: #2a2a2a; -fx-text-fill: #ff7351; -fx-padding: 2 6 2 6;"
                    tooltip = javafx.scene.control.Tooltip("Delete this point")
                    setOnAction {
                        val r = item ?: return@setOnAction
                        if (r.isPending) return@setOnAction
                        if (DialogUtils.confirm("Delete point", content = "Delete ${'$'}{r.id}?")) {
                            dispatcher.deletePoint(r.id)
                            refreshPointsUI()
                        }
                    }
                }
                private val headerSpacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
                private val header = HBox(6.0, title, headerSpacer, duration, goBtn, deleteBtn).apply {
                    alignment = Pos.CENTER_LEFT
                }
                private val startLbl = Label().apply { style = "-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: white;" }
                private val endLbl = Label().apply { style = "-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: white;" }
                private val timesRow = HBox(6.0, startLbl, Region(), endLbl).apply {
                    (children[1] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
                }
                private val box = VBox(4.0, header, timesRow).apply {
                    padding = Insets(8.0)
                    style = "-fx-background-color: rgba(0,0,0,0.35); -fx-background-radius: 3;"
                }
                init {
                    // Make the cell background transparent so the sidebar dark theme shows through
                    style = "-fx-background-color: transparent;"
                    padding = Insets(4.0)
                    addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
                        val r = item ?: return@addEventFilter
                        if (ev.clickCount == 2) {
                            val cur = getPlayheadMs()
                            val startL = r.startMs.toLong()
                            val endL = r.endMs?.toLong()
                            val target = if (endL != null && kotlin.math.abs(cur - startL) < 50L) endL else startL
                            seekTo(target)
                        } else if (ev.clickCount == 1) {
                            seekTo(r.startMs.toLong())
                            root.requestFocus()
                        }
                    }
                }
                private fun applySelectionStyles(selected: Boolean) {
                    if (selected) {
                        box.style = "-fx-background-color: #2c2c2c; -fx-background-insets: 0 0 0 4; -fx-background-radius: 3; -fx-border-color: #a1fe00; -fx-border-width: 0 0 0 4;"
                        title.style = "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #a1fe00; -fx-text-transform: uppercase; -fx-letter-spacing: 1px;"
                        startLbl.style = "-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: white;"
                        endLbl.style = startLbl.style
                    } else {
                        box.style = "-fx-background-color: rgba(0,0,0,0.25); -fx-background-radius: 3;"
                        title.style = "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #adaaaa; -fx-text-transform: uppercase; -fx-letter-spacing: 1px;"
                        startLbl.style = "-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: #adaaaa;"
                        endLbl.style = startLbl.style
                    }
                }
                override fun updateItem(row: Row?, empty: Boolean) {
                    super.updateItem(row, empty)
                    if (empty || row == null) {
                        graphic = null
                        text = null
                    } else {
                        val baseTitle = row.label?.takeIf { it.isNotBlank() } ?: "Point ${row.order}"
                        title.text = if (row.isPending) "Pending $baseTitle" else baseTitle
                        if (row.endMs != null) {
                            val durSec = ((row.endMs - row.startMs).coerceAtLeast(0) / 1000.0)
                            duration.text = String.format("%ds", durSec.toInt())
                        } else {
                            duration.text = "—"
                        }
                        startLbl.text = Timecode.format(row.startMs.toLong())
                        endLbl.text = row.endMs?.let { Timecode.format(it.toLong()) } ?: "—"
                        // Hide delete button for pending row
                        deleteBtn.isVisible = !row.isPending
                        deleteBtn.isManaged = !row.isPending
                        applySelectionStyles(isSelected)
                        graphic = box
                    }
                }
                override fun updateSelected(selected: Boolean) {
                    super.updateSelected(selected)
                    applySelectionStyles(selected)
                }
            }
        }

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
            styleClass.add("markup-action-btn")
            setOnAction {
                dispatcher.onPointStart(getPlayheadMs())
                refreshPointsUI()
                maybeShowDispatcherMessage()
            }
        }
        val pointEnd = Button("Point End [V]").apply {
            styleClass.add("markup-action-btn")
            setOnAction {
                dispatcher.onPointEnd(getPlayheadMs())
                refreshPointsUI()
                maybeShowDispatcherMessage()
            }
        }
        val leftGroup = HBox(8.0, pointStart, pointEnd)

        val jumpBack = Button().apply {
            styleClass.add("markup-transport-btn")
            // icon + text stacked horizontally so we can size them independently
            val icon = Label("⟲").apply { styleClass.add("transport-icon") }
            val text = Label("10s")
            graphic = HBox(6.0, icon, text).apply { alignment = Pos.CENTER }
            setOnAction { jumpBy(-10_000) }
        }
        val playPause = Button().apply {
            styleClass.add("markup-play-btn")
            // square with rounded corners
            prefWidth = 64.0
            prefHeight = 64.0
            minWidth = 64.0
            minHeight = 64.0
            maxWidth = 64.0
            maxHeight = 64.0
            // stack icon over caption
            val icon = Label("\u25B6").apply { styleClass.add("play-icon") } // ▶
            playIconLabel = icon
            val caption = Label("SPACE").apply {
                styleClass.add("play-caption")
                translateY = -10.0 /* move caption 10px higher to tighten the gap */
            }
            graphic = VBox(0.0, icon, caption).apply { alignment = Pos.CENTER }
            setOnAction { togglePlayPause() }
        }
        val jumpFwd = Button().apply {
            styleClass.add("markup-transport-btn")
            val icon = Label("↻").apply { styleClass.add("transport-icon") }
            val text = Label("10s")
            graphic = HBox(6.0, icon, text).apply { alignment = Pos.CENTER }
            setOnAction { jumpBy(10_000) }
        }
        val transport = HBox(16.0, jumpBack, playPause, jumpFwd).apply { alignment = Pos.CENTER }

        timeLabel.styleClass.add("markup-timecode")
        val rightGroup = HBox(timeLabel).apply { alignment = Pos.CENTER_RIGHT }

        val topControls = HBox(16.0, leftGroup, Region(), transport, Region(), rightGroup).apply {
            padding = Insets(8.0)
            (children[1] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
            (children[3] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
            style = "-fx-background-color: #1a1a1a; -fx-border-color: #2d2d2d; -fx-border-width: 1 0 0 0;"
        }

        // Scrubbing slider under the controls
        // Make slider match the actual rendered video width (preserveRatio may letterbox),
        // so we center the slider and bind its prefWidth to MediaView's real bounds.
        val sliderRow = HBox(seekSlider).apply { alignment = Pos.CENTER }
        // Constrain slider to never exceed the viewer width
        seekSlider.minWidth = 0.0
        seekSlider.maxWidthProperty().bind(viewer.widthProperty().subtract(16.0)) // account for viewer padding (8px left/right)
        sliderRow.maxWidthProperty().bind(viewer.widthProperty())
        // Update slider width whenever MediaView's bounds change
        fun updateSliderWidth() {
            val videoW = mediaView.boundsInParent.width
            val viewerW = viewer.width - 16.0 // approximate inner width (viewer has 8px padding on each side)
            val target = kotlin.math.max(0.0, kotlin.math.min(videoW, viewerW))
            if (target > 0) seekSlider.prefWidth = target
        }
        mediaView.boundsInParentProperty().addListener { _, _, _ -> updateSliderWidth() }
        viewer.widthProperty().addListener { _, _, _ -> updateSliderWidth() }
        // Initialize width once now
        updateSliderWidth()
        val centerBox = VBox(viewer, topControls, sliderRow)
        VBox.setVgrow(viewer, Priority.ALWAYS)
        VBox.setVgrow(sliderRow, Priority.NEVER)
        root.center = centerBox

        // Keyboard: Space toggles play/pause; C/V mark start/end; Arrows seek (Shift = 10s, plain = 1s)
        root.setOnKeyPressed { e ->
            when (e.code) {
                KeyCode.SPACE -> {
                    togglePlayPause()
                    e.consume()
                }
                KeyCode.S -> {
                    if (e.isShortcutDown) { // Ctrl+S on Windows/Linux, Cmd+S on macOS
                        autosaveDebounce.stop()
                        performSaveEdl()
                        e.consume()
                    }
                }
                KeyCode.C -> {
                    if (!e.isShortcutDown && !e.isAltDown && !e.isShiftDown) {
                        dispatcher.onPointStart(getPlayheadMs())
                        refreshPointsUI()
                        maybeShowDispatcherMessage()
                        e.consume()
                    }
                }
                KeyCode.V -> {
                    if (!e.isShortcutDown && !e.isAltDown && !e.isShiftDown) {
                        dispatcher.onPointEnd(getPlayheadMs())
                        refreshPointsUI()
                        maybeShowDispatcherMessage()
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
                KeyCode.DELETE -> {
                    val sel = pointsList.selectionModel.selectedItem
                    if (sel != null && !sel.isPending) {
                        if (DialogUtils.confirm("Delete point", content = "Delete ${sel.id}?")) {
                            dispatcher.deletePoint(sel.id)
                            refreshPointsUI()
                        }
                        e.consume()
                    }
                }
                else -> {}
            }
        }

        // Also handle Delete when the points list has focus
        pointsList.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED) { ke ->
            if (ke.code == KeyCode.DELETE) {
                val sel = pointsList.selectionModel.selectedItem
                if (sel != null) {
                    if (DialogUtils.confirm("Delete point", content = "Delete ${sel.id}?")) {
                        dispatcher.deletePoint(sel.id)
                        refreshPointsUI()
                    }
                    ke.consume()
                }
            }
        }

        return root
    }

    private fun performSaveEdl() {
        try {
            val manifestPath = ProjectsDispatcher.currentProjectPath ?: return
            val projectDir = EdlIO.projectDirFromManifest(manifestPath)
            val points = dispatcher.getCompletedPoints().sortedBy { it.startMs }
            EdlIO.writeForProjectDir(projectDir, EdlV1(points = points, version = 1))
            println("[MARKUP][AUTOSAVE] edl.json saved (${points.size} points)")
        } catch (t: Throwable) {
            System.err.println("[MARKUP][AUTOSAVE][ERROR] ${t.message}")
        }
    }

    private fun setupWindowCloseFlush() {
        // Ensure pending autosave is flushed when the window closes
        root.sceneProperty().addListener { _, _, newScene ->
            newScene?.windowProperty()?.addListener { _, _, newWin ->
                (newWin as? javafx.stage.Stage)?.setOnCloseRequest {
                    try {
                        autosaveDebounce.stop()
                        performSaveEdl()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun refreshPointsUI() {
        val completed = dispatcher.getCompletedPoints().sortedBy { it.startMs }
        val baseRows = completed.mapIndexed { idx, p ->
            Row(
                order = idx + 1,
                id = p.id,
                startMs = p.startMs,
                endMs = p.endMs,
                label = p.label,
            )
        }.toMutableList()
        val pendingStart = dispatcher.getPendingStart()
        val selectRow: Row? = if (pendingStart != null) {
            val pendingRow = Row(
                order = baseRows.size + 1,
                id = "(pending)",
                startMs = pendingStart,
                endMs = null,
                label = null,
                isPending = true,
            )
            baseRows.add(pendingRow)
            pendingRow
        } else {
            baseRows.lastOrNull()
        }
        pointsList.items.setAll(baseRows)
        if (selectRow != null) {
            pointsList.selectionModel.select(selectRow)
            pointsList.scrollTo(selectRow)
        }
        pointsCountLabel.text = "${completed.size} MARKED"
        // Update totals label if present
        val rightPane = root.right as? VBox
        val totalsLabel = rightPane?.properties?.get("totalsLabel") as? Label
        val totalMs = Timecode.totalDuration(completed)
        totalsLabel?.text = "Total: ${completed.size} point" + (if (completed.size == 1) "" else "s") + " — ${Timecode.format(totalMs)}"
    }
}
