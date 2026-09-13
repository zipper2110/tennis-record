package org.litvin.markup.components

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.markup.CommentDefaultsV1
import org.litvin.markup.CommentV1
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1

data class CommentPatch(
    val startMs: Int? = null,
    val durationMs: Int? = null,
    val text: String? = null,
    val colorHex: String? = null,
)

data class CommentState(
    val comments: List<CommentV1>,
    val defaults: CommentDefaultsV1,
    val nextCommentId: Int,
)

class CommentDispatcher {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val comments = mutableListOf<CommentV1>()
    private var defaults = CommentDefaultsV1()
    private var nextCommentId = 1
    private var userMessage: String? = null

    var onCommentsChanged: (() -> Unit)? = null

    fun load(state: CommentState) {
        val normalized = EdlIO.normalizeComments(
            EdlV1(
                comments = state.comments,
                commentDefaults = state.defaults,
                nextCommentId = state.nextCommentId,
            ),
        )
        comments.clear()
        comments += normalized.comments.sortedWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id })
        defaults = normalized.commentDefaults
        nextCommentId = normalized.nextCommentId
        onCommentsChanged?.invoke()
    }

    fun state(): CommentState = CommentState(
        comments = comments.toList(),
        defaults = defaults,
        nextCommentId = nextCommentId,
    )

    fun create(startMs: Int, durationMs: Int, text: String, colorHex: String? = null): CommentV1? {
        val requestedColor = colorHex?.let(EdlIO::normalizeColorHex)
        if (colorHex != null && requestedColor == null) {
            notifyUser("Comment color must use #RRGGBB format")
            return null
        }
        val color = requestedColor ?: defaults.colorHex
        if (!isValid(startMs, durationMs, text, color)) return null

        val comment = CommentV1(
            id = nextCommentId,
            startMs = startMs,
            durationMs = durationMs,
            text = text.trim(),
            colorHex = color,
        )
        comments += comment
        comments.sortWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id })
        nextCommentId += 1
        onCommentsChanged?.invoke()
        return comment
    }

    fun update(id: Int, patch: CommentPatch): Boolean {
        val index = comments.indexOfFirst { it.id == id }
        if (index < 0) {
            notifyUser("Comment #$id was not found")
            return false
        }
        val existing = comments[index]
        val requestedColor = patch.colorHex?.let(EdlIO::normalizeColorHex)
        if (patch.colorHex != null && requestedColor == null) {
            notifyUser("Comment color must use #RRGGBB format")
            return false
        }
        val updated = existing.copy(
            startMs = patch.startMs ?: existing.startMs,
            durationMs = patch.durationMs ?: existing.durationMs,
            text = (patch.text ?: existing.text).trim(),
            colorHex = requestedColor ?: existing.colorHex,
        )
        if (!isValid(updated.startMs, updated.durationMs, updated.text, updated.colorHex)) return false

        comments[index] = updated
        comments.sortWith(compareBy<CommentV1> { it.startMs }.thenBy { it.id })
        if (patch.colorHex != null) defaults = CommentDefaultsV1(updated.colorHex)
        onCommentsChanged?.invoke()
        return true
    }

    fun delete(id: Int): Boolean {
        val index = comments.indexOfFirst { it.id == id }
        if (index < 0) {
            notifyUser("Comment #$id was not found")
            return false
        }
        comments.removeAt(index)
        onCommentsChanged?.invoke()
        return true
    }

    fun consumeUserMessage(): String? = userMessage.also { userMessage = null }

    private fun isValid(startMs: Int, durationMs: Int, text: String, colorHex: String): Boolean {
        val message = when {
            startMs < 0 -> "Comment start time cannot be negative"
            durationMs <= 0 -> "Comment duration must be greater than zero"
            text.isBlank() -> "Comment text cannot be blank"
            EdlIO.normalizeColorHex(colorHex) == null -> "Comment color must use #RRGGBB format"
            else -> null
        }
        if (message == null) return true
        notifyUser(message)
        return false
    }

    private fun notifyUser(message: String) {
        userMessage = message
        logger.info { "User hint: $message" }
    }
}
