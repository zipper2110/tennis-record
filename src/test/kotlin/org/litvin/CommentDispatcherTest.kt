package org.litvin

import org.litvin.markup.CommentDefaultsV1
import org.litvin.markup.CommentV1
import org.litvin.markup.components.CommentDispatcher
import org.litvin.markup.components.CommentPatch
import org.litvin.markup.components.CommentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentDispatcherTest {
    @Test
    fun create_allocatesMonotonicIdsAndUsesProjectDefaultColor() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(CommentState(emptyList(), CommentDefaultsV1("#AA5500"), nextCommentId = 4))

        val first = dispatcher.create(startMs = 1_000, durationMs = 2_000, text = "In")
        dispatcher.delete(4)
        val second = dispatcher.create(startMs = 2_000, durationMs = 2_000, text = "Out")

        assertEquals(CommentV1(4, 1_000, 2_000, "In", "#AA5500"), first)
        assertEquals(5, second?.id)
        assertEquals("#AA5500", second?.colorHex)
    }

    @Test
    fun updateColor_updatesTheCommentAndProjectDefault() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(
            CommentState(
                comments = listOf(CommentV1(1, 0, 1_000, "Serve", "#FFFFFF")),
                defaults = CommentDefaultsV1(),
                nextCommentId = 2,
            ),
        )

        assertTrue(dispatcher.update(1, CommentPatch(colorHex = "#12ab34")))

        assertEquals("#12AB34", dispatcher.state().comments.single().colorHex)
        assertEquals("#12AB34", dispatcher.state().defaults.colorHex)
    }

    @Test
    fun rejectsInvalidInputWithoutMutatingCommentState() {
        val dispatcher = CommentDispatcher()
        dispatcher.load(
            CommentState(
                comments = listOf(CommentV1(1, 0, 1_000, "Serve", "#FFFFFF")),
                defaults = CommentDefaultsV1(),
                nextCommentId = 2,
            ),
        )
        val before = dispatcher.state()

        assertNull(dispatcher.create(startMs = -1, durationMs = 1_000, text = "In"))
        assertNull(dispatcher.create(startMs = 0, durationMs = 0, text = "In"))
        assertNull(dispatcher.create(startMs = 0, durationMs = 1_000, text = "   "))
        assertNull(dispatcher.create(startMs = 0, durationMs = 1_000, text = "In", colorHex = "blue"))
        assertFalse(dispatcher.update(99, CommentPatch(text = "Missing")))
        assertFalse(dispatcher.update(1, CommentPatch(colorHex = "blue")))

        assertEquals(before, dispatcher.state())
    }
}
