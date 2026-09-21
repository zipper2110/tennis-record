package org.litvin.ui.tabs.points

/**
 * Contracts for the Points tab UI and its container.
 *
 * These APIs are intentionally narrow and decoupled from domain types.
 * Leaf Swing components should depend only on these contracts and UI commons.
 */
interface PointsActions {
    fun togglePlayPause()
    fun seekTo(ms: Long)
    fun jumpToSelected()
    fun setStartAtPlayhead()
    fun setEndAtPlayhead()

    fun createPointAt(ms: Long)
    fun editPoint(id: String, patch: PointPatch)
    fun deletePoint(id: String)
    fun toggleFavorite(id: String)

    fun addCommentAtPlayhead() {}
    fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) {}
    fun editComment(id: Int, patch: CommentPatch) {}
    fun deleteComment(id: Int) {}

    /**
     * Selects an item by its visual index (index within the composed view list).
     */
    fun selectByVisualIndex(index: Int)

    fun saveNow()
}

/** Immutable snapshot of the Points view state consumed by leaf components. */
data class PointsViewState(
    val isPlaying: Boolean,
    val currentTimeMs: Long,
    val selectedVisualIndex: Int?,
    val pendingDraftStartMs: Long?,
    val events: List<TimelineEventDto>,
    val autosave: AutosaveState,
)

sealed interface TimelineEventDto {
    val startMs: Long
    val stableKey: String
}

data class PointEventDto(
    val point: PointDto,
) : TimelineEventDto {
    override val startMs: Long get() = point.startMs
    override val stableKey: String get() = "point:${point.id}"
}

data class CommentDto(
    val id: Int,
    override val startMs: Long,
    val durationMs: Long,
    val text: String,
    val colorHex: String,
) : TimelineEventDto {
    override val stableKey: String get() = "comment:$id"
}

/** Lightweight DTO for a point shown in UI components. */
data class PointDto(
    val id: String,
    val startMs: Long,
    val endMs: Long?,
    val label: String?,
    val flags: Set<String> = emptySet(),
    val favorite: Boolean = false,
)

/** Partial update to a point. Fields set to null are not modified. */
data class PointPatch(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val label: String? = null,
    val flags: Set<String>? = null,
    val favorite: Boolean? = null,
)

/** Partial update to a comment. Fields set to null are not modified. */
data class CommentPatch(
    val startMs: Long? = null,
    val durationMs: Long? = null,
    val text: String? = null,
    val colorHex: String? = null,
)

/** Autosave status surfaced to the toolbar and other indicators. */
data class AutosaveState(
    val pending: Boolean,
    val lastSavedAtMs: Long?,
)
