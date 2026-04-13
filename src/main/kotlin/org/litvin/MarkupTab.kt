package org.litvin

import javafx.animation.AnimationTimer
import javafx.animation.PauseTransition
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.stage.Popup
import javafx.util.Duration
import org.litvin.media.AppMediaPlayer
import org.litvin.media.JavaFxMediaPlayerAdapter
import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjMediaPlayerAdapter
import java.io.File

/**
 * Markup tab (Task 2.1 done; extends for 2.2 playback):
 * - Loads project's sourceVideo on enter
 * - Play/Pause/Seek via controls and Spacebar
 * - Time display hh:mm:ss.mmm updates at least every 100 ms while playing
 * - Exposes getPlayheadMs() and seekTo(ms)
 */
class MarkupTab(private val dispatcher: MarkupDispatcher) {

    private var timelineArea: StackPane? = null
    private var videoTrackPane: StackPane? = null
    private var marksTrackPane: Pane? = null
    private var playheadLine: Region? = null
    private var zoomLabel: Label? = null
    private var timelineZoomIndex: Int = 0
    private val timelineZoomLevels = doubleArrayOf(1.0, 2.0)
    private var currentVideoFileName: String? = null
    // Request host to route back to Select Source flow when video is missing/unavailable
    var onRequestSelectSource: (() -> Unit)? = null
    // Request host to navigate back to Projects when user clicks Projects in sidebar
    var onRequestNavigateProjects: (() -> Unit)? = null
    // Request host to navigate to Export when user clicks Export in sidebar
    var onRequestNavigateExport: (() -> Unit)? = null
    // Request host to navigate to Video Test when user clicks sidebar item
    var onRequestNavigateVideoTest: (() -> Unit)? = null
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
    // Container for video surface provided by underlying media backend (VLCJ or JavaFX)
    private val videoContainer = StackPane()
    private var appPlayer: org.litvin.media.AppMediaPlayer? = null
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
        maxHeight = Double.MAX_VALUE
    }

    // Track last auto-activated selection to avoid thrash
    private var lastActivatedId: String? = null

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
            // After points change, sync active selection with current time
            updateActiveSelectionFromTime(getPlayheadMs())
        }
    }

    fun onEnter() {
        // Attempt to load current project's source video and EDL
        try {
            val manifestPath = ProjectsDispatcher.currentProjectPath ?: return
            val manifest = ManifestIO.read(manifestPath)
            val src = manifest.sourceVideo?.takeIf { it.isNotBlank() }
            if (src != null) {
                val f = File(src)
                if (f.exists()) {
                    loadMedia(f)
                } else {
                    // File path no longer valid — notify and route to Select Source
                    DialogUtils.error(
                        title = "Source video not found",
                        header = "The selected source video cannot be opened.",
                        content = "File not found: $src\nPlease re-select the source video."
                    )
                    onRequestSelectSource?.invoke()
                }
            } else {
                // No source video set — go to Select Source
                onRequestSelectSource?.invoke()
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

    fun getPlayheadMs(): Long = appPlayer?.currentTimeMs() ?: 0L

    fun seekTo(ms: Long) {
        appPlayer?.seek(ms.coerceAtLeast(0L))
    }

    private fun loadMedia(file: File) {
        currentVideoFileName = file.name
        try {
            // Dispose old player and clear view
            appPlayer?.dispose()
            videoContainer.children.clear()

            // Try VLCJ first, fallback to JavaFX if it fails
            val player: AppMediaPlayer = try {
                VlcjMediaPlayerAdapter()
            } catch (t: Throwable) {
                System.err.println("[MARKUP] VLCJ not available, falling back to JavaFX MediaPlayer: ${t.message}")
                JavaFxMediaPlayerAdapter()
            }
            appPlayer = player

            // Attach view node
            videoContainer.children.add(player.viewNode)
            StackPane.setAlignment(player.viewNode, Pos.CENTER)

            player.onReady = {
                // timeline rebuild when media metadata is ready
                rebuildTimeline()
                updatePlayheadInTimeline()
                // Initialize seek slider and time display when media metadata is ready
                val durMs = player.totalDurationMs().toDouble()
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
            player.onStatusChanged = { st ->
                if (st == PlayerStatus.PLAYING) {
                    ticker.start()
                } else {
                    ticker.stop()
                    updateTimeUI()
                }
                updatePlayButtonIcon()
            }
            player.onTimeChanged = { _ ->
                if (player.status() != PlayerStatus.PLAYING && !isUserSeeking) {
                    updateTimeUI()
                }
            }

            player.load(file)
        } catch (t: Throwable) {
            System.err.println("[MARKUP] Failed to load media: ${t.message}")
        }
    }

    private fun togglePlayPause() {
        val p = appPlayer ?: return
        when (p.status()) {
            PlayerStatus.PLAYING -> p.pause()
            else -> p.play()
        }
        // Update icon immediately for snappy UI; listener will also adjust once status changes
        updatePlayButtonIcon()
        dispatcher.onPlayPauseClicked()
    }

    private fun jumpBy(deltaMs: Long) {
        val p = appPlayer ?: return
        val target = p.currentTimeMs() + deltaMs
        p.seek(target)
        if (p.status() != PlayerStatus.PLAYING) updateTimeUI()
        if (deltaMs < 0) dispatcher.onJumpBackClicked() else dispatcher.onJumpForwardClicked()
    }

    private fun updateTimeUI() {
        val ms = getPlayheadMs()
        // Throttle UI updates to reduce layout/render overhead
        val playing = appPlayer?.status() == PlayerStatus.PLAYING
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
        // Auto-activate point based on current time (2.12)
        updateActiveSelectionFromTime(ms)
        // Update timeline playhead (2.13)
        updatePlayheadInTimeline()
    }

    private fun updatePlayButtonIcon() {
        val icon = playIconLabel ?: return
        val playing = appPlayer?.status() == PlayerStatus.PLAYING
        // Use proper Unicode icons: ▶ (U+25B6) for play, ⏸ (U+23F8) for pause
        icon.text = if (playing) "\u23F8" else "\u25B6"
    }

    private fun setupSeekSliderHandlers() {
        // Track when user is changing the slider (dragging)
        seekSlider.valueChangingProperty().addListener { _, _, changing ->
            isUserSeeking = changing
            if (!changing) {
                // Drag ended; perform seek
                appPlayer?.seek(seekSlider.value.toLong())
                // After seek completes (approx), update active selection once
                updateActiveSelectionFromTime(seekSlider.value.toLong())
            }
        }
        // Clicking on the slider (without drag)
        seekSlider.addEventHandler(MouseEvent.MOUSE_PRESSED) {
            isUserSeeking = true
        }
        seekSlider.addEventHandler(MouseEvent.MOUSE_RELEASED) {
            isUserSeeking = false
            appPlayer?.seek(seekSlider.value.toLong())
            updateActiveSelectionFromTime(seekSlider.value.toLong())
        }
    }

    private fun buildView(): Node {

        // Attach left navigation (shared sidebar)
        root.left = org.litvin.markup.AppSidebar.build(
            active = org.litvin.markup.AppSidebar.Active.MARKUP,
            onProjects = { onRequestNavigateProjects?.invoke() },
            onMarkup = { /* already here */ },
            onExport = { onRequestNavigateExport?.invoke() },
            onVideoTest = { onRequestNavigateVideoTest?.invoke() }
        )

        // Right sidebar: Points list (Markup panel side)
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
        // Let the points list take remaining vertical space and only scroll when content exceeds it
        VBox.setVgrow(pointsList, Priority.ALWAYS)
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
                private val editBtn = Button("✎").apply {
                    style = "-fx-background-color: #2a2a2a; -fx-text-fill: #adaaaa; -fx-padding: 2 6 2 6;"
                    tooltip = javafx.scene.control.Tooltip("Edit")
                }
                private val header = HBox(6.0, title, headerSpacer, duration, goBtn, editBtn, deleteBtn).apply {
                    alignment = Pos.CENTER_LEFT
                }
                private val startLbl = Label().apply { style = "-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: white;" }
                private val endLbl = Label().apply { style = "-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: white;" }
                private val timesRow = HBox(6.0, startLbl, Region(), endLbl).apply {
                    (children[1] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
                }

                // --- Inline edit UI (2.14) ---
                private var editMode = false
                private val startField = javafx.scene.control.TextField().apply { 
                    promptText = "Start (hh:mm:ss.mmm)"
                    prefColumnCount = 10
                    maxWidth = Double.MAX_VALUE
                }
                private val endField = javafx.scene.control.TextField().apply { 
                    promptText = "End (hh:mm:ss.mmm)"
                    prefColumnCount = 10
                    maxWidth = Double.MAX_VALUE
                }
                private val errorLabel = Label("").apply { 
                    style = "-fx-text-fill: #ff7351; -fx-font-size: 10px;"
                    isWrapText = true
                    maxWidth = Double.MAX_VALUE
                }
                private val saveBtn = Button("Save").apply { style = "-fx-background-color: #3a3a3a; -fx-text-fill: #a1fe00; -fx-padding: 2 8;" }
                private val cancelBtn = Button("Cancel").apply { style = "-fx-background-color: #3a3a3a; -fx-text-fill: #adaaaa; -fx-padding: 2 8;" }
                private val editRow = VBox(6.0,
                    HBox(6.0, Label("Start:"), startField).apply { HBox.setHgrow(startField, Priority.ALWAYS) },
                    HBox(6.0, Label("End:"), endField).apply { HBox.setHgrow(endField, Priority.ALWAYS) },
                    HBox(6.0, errorLabel, Region(), saveBtn, cancelBtn).apply { 
                        (children[1] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
                        HBox.setHgrow(errorLabel, Priority.ALWAYS)
                    }
                ).apply { isFillWidth = true }

                private val contentBox = VBox(6.0, timesRow).apply { isFillWidth = true }
                private val box = VBox(4.0, header, contentBox).apply {
                    padding = Insets(8.0)
                    style = "-fx-background-color: rgba(0,0,0,0.35); -fx-background-radius: 3;"
                }
                init {
                    // Make the cell background transparent so the sidebar dark theme shows through
                    style = "-fx-background-color: transparent;"
                    padding = Insets(4.0)
                    addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
                        val r = item ?: return@addEventFilter
                        if (editMode) return@addEventFilter
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

                    fun enterEdit() {
                        val r = item ?: return
                        if (r.isPending) return
                        editMode = true
                        errorLabel.text = ""
                        startField.text = Timecode.format(r.startMs.toLong())
                        val endText = r.endMs?.let { Timecode.format(it.toLong()) } ?: ""
                        endField.text = endText
                        contentBox.children.setAll(editRow)
                        // Focus first field
                        startField.requestFocus()
                    }
                    fun exitEdit() {
                        editMode = false
                        errorLabel.text = ""
                        contentBox.children.setAll(timesRow)
                    }
                    fun trySave() {
                        val r = item ?: return
                        val id = r.id
                        try {
                            val sMs = Timecode.parse(startField.text)
                            val eMs = Timecode.parse(endField.text)
                            val ok = dispatcher.updatePoint(id, sMs, eMs, r.label)
                            if (ok) {
                                // Refresh UI and keep selection on the same id
                                val curId = id
                                refreshPointsUI()
                                val items = pointsList.items
                                val match = items.firstOrNull { it.id == curId }
                                if (match != null) {
                                    pointsList.selectionModel.select(match)
                                    pointsList.scrollTo(match)
                                }
                                autosaveDebounce.playFromStart()
                                maybeShowDispatcherMessage()
                                exitEdit()
                            } else {
                                // Dispatcher set a message; also show inline
                                val msg = dispatcher.consumeUserMessage() ?: "Invalid edit"
                                errorLabel.text = msg
                                showToast(msg)
                            }
                        } catch (t: Throwable) {
                            errorLabel.text = t.message ?: "Invalid time format"
                        }
                    }

                    editBtn.setOnAction { enterEdit() }
                    saveBtn.setOnAction { trySave() }
                    cancelBtn.setOnAction { exitEdit() }

                    // Revert on focus lost from the edit controls (lightweight cancel)
                    editRow.focusedProperty().addListener { _, _, hasFocus ->
                        if (!hasFocus && editMode) {
                            exitEdit()
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
                        // Edit button available only for completed rows
                        editBtn.isVisible = !row.isPending
                        editBtn.isManaged = !row.isPending
                        // Swap content based on edit mode
                        if (editMode) {
                            // Keep fields in sync in case updateItem was called while editing
                            startField.text = Timecode.format(row.startMs.toLong())
                            endField.text = row.endMs?.let { Timecode.format(it.toLong()) } ?: ""
                            contentBox.children.setAll(editRow)
                        } else {
                            contentBox.children.setAll(timesRow)
                        }
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
        val viewer = StackPane(videoContainer).apply {
            padding = Insets(8.0)
            style = "-fx-background-color: #111;"
        }
        // Ensure the video container follows available space leaving room for controls
        videoContainer.maxWidth = Double.MAX_VALUE
        videoContainer.maxHeight = Double.MAX_VALUE

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
        }

        // Scrubbing bar under the controls — custom UI to match mock (thin rounded track + fill + hover thumb)
        // Keep the existing seekSlider for logic and bindings, but hide it from layout.
        seekSlider.isVisible = false
        seekSlider.isManaged = false

        // Custom scrubber nodes
        val scrubberTrack = Pane().apply {
            minHeight = 6.0
            prefHeight = 6.0
            maxHeight = 6.0
            style = "-fx-background-color: #0b0b0b; -fx-background-radius: 9999; -fx-border-color: rgba(255,255,255,0.06); -fx-border-width: 1; -fx-border-radius: 9999;"
        }
        // Clip the scrubber painting strictly to the track bounds to avoid any overlay over the footer timeline
        run {
            val clip = javafx.scene.shape.Rectangle()
            clip.widthProperty().bind(scrubberTrack.widthProperty())
            clip.heightProperty().bind(scrubberTrack.heightProperty())
            scrubberTrack.clip = clip
        }
        val scrubberCanvas = javafx.scene.canvas.Canvas().apply {
            isMouseTransparent = true
        }
        val scrubberThumb = Region().apply {
            prefWidth = 10.0
            prefHeight = 10.0
            style = "-fx-background-color: white; -fx-background-radius: 9999; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 6, 0.5, 0, 1);"
            opacity = 0.0 // visible on hover only
        }
        // Place canvas (for green fill) and thumb inside the track
        scrubberTrack.children.addAll(scrubberCanvas, scrubberThumb)

        // Center the scrubber and bind width similar to previous slider
        val scrubberRow = HBox(scrubberTrack).apply { alignment = Pos.CENTER }
        scrubberTrack.minWidth = 0.0
        scrubberTrack.maxWidthProperty().bind(viewer.widthProperty().subtract(16.0)) // account for viewer padding (8px left/right)
        scrubberRow.maxWidthProperty().bind(viewer.widthProperty())

        // Helper to update fill and thumb positions based on slider value
        fun updateScrubberVisuals() {
            val max = seekSlider.max.coerceAtLeast(1.0)
            val value = seekSlider.value.coerceIn(0.0, max)
            val w = scrubberTrack.width.coerceAtLeast(0.0)
            val h = scrubberTrack.height.coerceAtLeast(0.0)
            val ratio = if (max > 0) value / max else 0.0
            val fillW = (w * ratio).coerceIn(0.0, w)

            // Resize and paint the canvas with a bright green rounded fill for the elapsed portion
            if (scrubberCanvas.width != w) scrubberCanvas.width = w
            if (scrubberCanvas.height != h) scrubberCanvas.height = h
            val gc = scrubberCanvas.graphicsContext2D
            gc.clearRect(0.0, 0.0, w, h)
            gc.fill = javafx.scene.paint.Color.web("#a1fe00")
            val arc = h // full pill
            if (fillW > 0.0 && h > 0.0) {
                gc.fillRoundRect(0.0, 0.0, fillW, h, arc, arc)
            }

            // Position the thumb at the end of the fill
            val thumbX = (fillW - scrubberThumb.prefWidth / 2).coerceIn(-scrubberThumb.prefWidth / 2, w - scrubberThumb.prefWidth / 2)
            val thumbY = (h - scrubberThumb.prefHeight) / 2
            scrubberThumb.relocate(thumbX, thumbY)
        }

        // Update visuals when value/max/size changes
        seekSlider.valueProperty().addListener { _, _, _ -> updateScrubberVisuals() }
        seekSlider.maxProperty().addListener { _, _, _ -> updateScrubberVisuals() }
        scrubberTrack.widthProperty().addListener { _, _, _ -> updateScrubberVisuals() }
        scrubberTrack.heightProperty().addListener { _, _, _ -> updateScrubberVisuals() }

        // Hover shows the thumb
        scrubberTrack.hoverProperty().addListener { _, _, isHover ->
            scrubberThumb.opacity = if (isHover) 1.0 else 0.0
        }

        // Input handling: map mouse to value
        fun positionToValue(x: Double): Double {
            val w = scrubberTrack.width
            if (w <= 0) return 0.0
            val clamped = x.coerceIn(0.0, w)
            val ratio = clamped / w
            return ratio * seekSlider.max
        }
        scrubberTrack.addEventFilter(MouseEvent.MOUSE_PRESSED) { ev ->
            if (seekSlider.isDisable) return@addEventFilter
            isUserSeeking = true
            seekSlider.value = positionToValue(ev.x)
            updateScrubberVisuals()
            ev.consume()
        }
        scrubberTrack.addEventFilter(MouseEvent.MOUSE_DRAGGED) { ev ->
            if (seekSlider.isDisable) return@addEventFilter
            isUserSeeking = true
            seekSlider.value = positionToValue(ev.x)
            updateScrubberVisuals()
            ev.consume()
        }
        scrubberTrack.addEventFilter(MouseEvent.MOUSE_RELEASED) { ev ->
            if (seekSlider.isDisable) return@addEventFilter
            isUserSeeking = false
            appPlayer?.seek(seekSlider.value.toLong())
            updateScrubberVisuals()
            ev.consume()
        }

        // Dim when disabled
        seekSlider.disabledProperty().addListener { _, _, disabled ->
            scrubberTrack.opacity = if (disabled) 0.4 else 1.0
        }

        // Keep width roughly aligned with rendered video content
        fun updateScrubberWidth() {
            val videoW = videoContainer.boundsInParent.width
            val viewerW = viewer.width - 16.0
            val target = kotlin.math.max(0.0, kotlin.math.min(videoW, viewerW))
            if (target > 0) scrubberTrack.prefWidth = target
        }
        videoContainer.boundsInParentProperty().addListener { _, _, _ -> updateScrubberWidth() }
        viewer.widthProperty().addListener { _, _, _ -> updateScrubberWidth() }

        // Initialize visuals/width now
        updateScrubberWidth()
        updateScrubberVisuals()

        // Bottom panel that contains playback controls and slider, with 10px padding
        val bottomPanel = VBox(8.0, topControls, scrubberRow).apply {
            padding = Insets(10.0)
            style = "-fx-background-color: #1a1a1a; -fx-border-color: #2d2d2d; -fx-border-width: 1 0 0 0;"
        }

        val centerBox = VBox(viewer, bottomPanel, seekSlider) // include hidden slider for logic
        VBox.setVgrow(viewer, Priority.ALWAYS)
        VBox.setVgrow(bottomPanel, Priority.NEVER)
        root.center = centerBox

        // Footer timeline (2.13)
        // Header with zoom controls
        val zoomOutBtn = Button("-").apply {
            style = "-fx-background-color: #2a2a2a; -fx-text-fill: #adaaaa; -fx-padding: 2 6 2 6;"
            setOnAction {
                timelineZoomIndex = (timelineZoomIndex - 1).coerceAtLeast(0)
                rebuildTimeline()
            }
        }
        val zoomInBtn = Button("+").apply {
            style = "-fx-background-color: #2a2a2a; -fx-text-fill: #adaaaa; -fx-padding: 2 6 2 6;"
            setOnAction {
                timelineZoomIndex = (timelineZoomIndex + 1).coerceAtMost(timelineZoomLevels.size - 1)
                rebuildTimeline()
            }
        }
        zoomLabel = Label("ZOOM 100% ").apply { style = "-fx-font-size: 10px; -fx-text-fill: #adaaaa; -fx-font-family: monospace;" }
        val headerLeft = HBox(6.0, zoomLabel, zoomOutBtn, zoomInBtn).apply { alignment = Pos.CENTER_LEFT }
        val headerRight = HBox(Label(ProjectsDispatcher.currentProjectPath?.let { File(it).parentFile?.name ?: "" } ?: "" ).apply {
            style = "-fx-font-size: 10px; -fx-text-fill: #adaaaa; -fx-font-family: monospace;"
        }).apply { alignment = Pos.CENTER_RIGHT }
        val tlHeader = HBox(Region(), headerLeft, Region(), headerRight).apply {
            padding = Insets(6.0, 10.0, 6.0, 10.0)
            style = "-fx-background-color: #141414; -fx-border-color: #2d2d2d; -fx-border-width: 1 0 0 0;"
            (children[0] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
            (children[2] as Region).also { HBox.setHgrow(it, Priority.ALWAYS) }
        }
        // Tracks area
        videoTrackPane = StackPane().apply { 
            style = "-fx-background-color: transparent;"
            minWidth = 0.0
        }
        marksTrackPane = Pane().apply { 
            style = "-fx-background-color: transparent;"
            minWidth = 0.0
        }
        val labelsCol = VBox(
            Label("VIDEO").apply { style = "-fx-text-fill: #adaaaa; -fx-font-size: 9px; -fx-font-weight: bold;" },
            Label("MARKS").apply { style = "-fx-text-fill: #adaaaa; -fx-font-size: 9px; -fx-font-weight: bold;" }
        ).apply {
            minWidth = 60.0; prefWidth = 60.0; maxWidth = 60.0
            padding = Insets(4.0, 6.0, 4.0, 6.0)
            style = "-fx-background-color: #1f1f1f; -fx-border-color: #2d2d2d; -fx-border-width: 0 1 0 0;"
            spacing = 12.0
        }
        val tracksCol = VBox().apply {
            spacing = 6.0
            padding = Insets(4.0)
            children.add(Pane().apply { // ruler simple ticks
                prefHeight = 18.0
                style = "-fx-background-color: transparent; -fx-border-color: rgba(255,255,255,0.08); -fx-border-width: 0 0 1 0;"
                properties["type"] = "ruler"
            })
            // Rows for tracks; ensure inner panes grow to fill width
            children.add(HBox(videoTrackPane).apply {
                HBox.setHgrow(videoTrackPane, Priority.ALWAYS)
                maxWidth = Double.MAX_VALUE
            })
            children.add(HBox(marksTrackPane).apply {
                HBox.setHgrow(marksTrackPane, Priority.ALWAYS)
                maxWidth = Double.MAX_VALUE
            })
        }
        timelineArea = StackPane().apply {
            style = "-fx-background-color: #0e0e0e;"
            children.add(HBox(labelsCol, tracksCol).apply { HBox.setHgrow(tracksCol, Priority.ALWAYS) })
        }
        // Playhead removed per request — no vertical indicator rendered in the timeline
        playheadLine = null
        // Bind width of tracks to centerBox width
        timelineArea!!.maxWidthProperty().bind(root.widthProperty())
        timelineArea!!.widthProperty().addListener { _, _, _ -> rebuildTimeline() }
        marksTrackPane!!.widthProperty().addListener { _, _, _ -> rebuildTimeline() }
        // Rebuild when media duration (seekSlider.max) becomes known/changes
        seekSlider.maxProperty().addListener { _, _, _ -> rebuildTimeline() }
        // Place footer under the center content (not in BorderPane.bottom) so the left sidebar spans full height
        val footer = VBox(tlHeader, timelineArea).apply { style = "-fx-background-color: #0e0e0e;" }
        if (centerBox.children.contains(footer).not()) {
            centerBox.children.add(footer)
        }
        // Initial build
        rebuildTimeline()
        updatePlayheadInTimeline()

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

    // Determine which row should be active for time t based on [start, end) half-open intervals.
    private fun findActiveRowForTime(t: Long): Row? {
        // Build a snapshot of completed rows (exclude pending) sorted by startMs
        val items = pointsList.items
        if (items.isEmpty()) return null
        val completed = items.filter { !it.isPending }
        if (completed.isEmpty()) return null
        // Binary search by startMs
        var lo = 0
        var hi = completed.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val r = completed[mid]
            when {
                t < r.startMs -> hi = mid - 1
                t >= (r.endMs ?: Int.MAX_VALUE) -> lo = mid + 1
                else -> { idx = mid; break }
            }
        }
        if (idx >= 0) return completed[idx]
        // Edge case: exactly at end of one and next starts at same time -> select next
        // After the while, lo is the first index with startMs > t, hi is <= t.
        if (lo in completed.indices) {
            val next = completed[lo]
            val prev = if (hi in completed.indices) completed[hi] else null
            if (prev != null && t == (prev.endMs ?: Int.MAX_VALUE).toLong() && next.startMs == prev.endMs) {
                return next
            }
        }
        return null
    }

    // Update points list selection according to current time and pending rules.
    private fun updateActiveSelectionFromTime(t: Long) {
        // Debounce: only change when identity actually changes
        val match = findActiveRowForTime(t)
        val pendingStart = dispatcher.getPendingStart()
        val target: Row? = match ?: run {
            // If no completed match, consider pending row active if t >= pending start
            if (pendingStart != null) {
                val pendingRow = pointsList.items.lastOrNull()?.takeIf { it.isPending }
                if (pendingRow != null && t >= pendingRow.startMs) pendingRow else null
            } else null
        }
        val selected = pointsList.selectionModel.selectedItem
        val targetId = target?.id
        val selectedId = selected?.id
        if (targetId != selectedId) {
            if (target != null) {
                pointsList.selectionModel.select(target)
                pointsList.scrollTo(target)
                lastActivatedId = targetId
            } else {
                // Clear selection if nothing matches
                pointsList.selectionModel.clearSelection()
                lastActivatedId = null
            }
        }
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
        // Rebuild timeline marks to reflect changes
        rebuildTimeline()
    }

    // --- Timeline (2.13) helpers ---
    private fun timelinePxPerMs(): Double {
        val dur = seekSlider.max
        val trackW = marksTrackPane?.width ?: 0.0
        if (dur <= 0.0 || trackW <= 0.0) return 0.0
        val zoom = timelineZoomLevels.getOrElse(timelineZoomIndex) { 1.0 }
        return (trackW / dur) * zoom
    }

    private fun rebuildTimeline() {
        val track = marksTrackPane ?: return
        val videoPane = videoTrackPane ?: return
        val area = timelineArea ?: return
        // Update zoom label
        val zoom = timelineZoomLevels.getOrElse(timelineZoomIndex) { 1.0 }
        val pct = (zoom * 100).toInt()
        zoomLabel?.text = "ZOOM ${pct}%"

        // VIDEO track: single full-width span
        videoPane.children.clear()
        val videoSpan = StackPane().apply {
            minHeight = 24.0; prefHeight = 24.0; maxHeight = 24.0
            style = "-fx-background-color: rgba(220,236,98,0.25); -fx-border-color: rgba(161,254,0,0.5); -fx-border-width: 1 0 1 2; -fx-background-radius: 2;"
        }
        val videoLabel = Label(currentVideoFileName ?: "").apply {
            style = "-fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;"
        }
        videoSpan.children.add(videoLabel)
        // Bind width to the VIDEO track row width (not MARKS)
        videoSpan.maxWidthProperty().bind(videoPane.widthProperty())
        videoSpan.prefWidthProperty().bind(videoPane.widthProperty())
        videoPane.children.add(videoSpan)

        // MARKS track: one span per completed point
        val pxPerMs = timelinePxPerMs()
        if (pxPerMs <= 0.0) {
            // Scale not ready yet; keep existing marks and try again on next rebuild
            updatePlayheadInTimeline()
            return
        }
        track.children.clear()
        val points = dispatcher.getCompletedPoints().sortedBy { it.startMs }
        for (p in points) {
            val x = p.startMs * pxPerMs
            val w = (p.endMs - p.startMs) * pxPerMs
            if (w <= 0.5) continue
            val span = StackPane().apply {
                layoutX = x
                minHeight = 18.0; prefHeight = 18.0; maxHeight = 18.0
                prefWidth = w
                style = "-fx-background-color: rgba(161,254,0,0.2); -fx-border-color: #a1fe00; -fx-border-width: 0 1 0 1; -fx-background-radius: 2;"
                children.add(Label(p.id).apply { style = "-fx-text-fill: #a1fe00; -fx-font-size: 9px; -fx-font-weight: bold;" })
                setOnMouseClicked { seekTo(p.startMs.toLong()) }
            }
            track.children.add(span)
        }

        // Ensure playhead height spans the area
        val ph = playheadLine
        if (ph != null) {
            ph.isManaged = false
            if (!ph.prefHeightProperty().isBound) {
                ph.prefHeightProperty().bind(area.heightProperty())
            }
        }
        updatePlayheadInTimeline()
        // Keep playhead above any newly added mark nodes
        playheadLine?.toFront()
     }
 
     private fun updatePlayheadInTimeline() {
        val area = timelineArea ?: return
        val labelsWidth = 60.0 // fixed width defined in buildView for labels column
        val pxPerMs = timelinePxPerMs()
        if (pxPerMs <= 0) return
        val t = getPlayheadMs().toDouble()
        val x = labelsWidth + 4.0 + t * pxPerMs
        val ph = playheadLine ?: return
        ph.isManaged = false
        ph.layoutX = x
        ph.layoutY = 0.0
    }
}
