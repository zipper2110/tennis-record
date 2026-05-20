package org.litvin.ui.tabs.markup

/**
 * Contracts for the Markup tab UI and its container.
 *
 * These APIs are intentionally narrow and decoupled from domain types.
 * Leaf Swing components should depend only on these contracts and UI commons.
 */
interface MarkupActions {
    fun togglePlayPause()
    fun seekTo(ms: Long)
    fun jumpToSelected()
    fun setStartAtPlayhead()
    fun setEndAtPlayhead()

    fun createPointAt(ms: Long)
    fun editPoint(id: String, patch: PointPatch)
    fun deletePoint(id: String)

    /**
     * Selects an item by its visual index (index within the composed view list).
     */
    fun selectByVisualIndex(index: Int)

    fun saveNow()
}

/** Immutable snapshot of the Markup view state consumed by leaf components. */
data class MarkupViewState(
    val isPlaying: Boolean,
    val currentTimeMs: Long,
    val selectedVisualIndex: Int?,
    val pendingDraftStartMs: Long?,
    val points: List<PointDto>,
    val autosave: AutosaveState,
)

/** Lightweight DTO for a markup point shown in UI components. */
data class PointDto(
    val id: String,
    val startMs: Long,
    val endMs: Long?,
    val label: String?,
    val flags: Set<String> = emptySet(),
)

/** Partial update to a point. Fields set to null are not modified. */
data class PointPatch(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val label: String? = null,
    val flags: Set<String>? = null,
)

/** Autosave status surfaced to the toolbar and other indicators. */
data class AutosaveState(
    val pending: Boolean,
    val lastSavedAtMs: Long?,
)
