package org.litvin.ui.tabs.crop

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.OverlayShape
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.io.File
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropEditorOverlayTest {
    @Test
    fun `no overlay is drawn until the player knows the video area`() {
        val player = OverlayPlayer(bounds = null)
        CropEditorOverlay(player) { }.render(AdjustmentsV1())

        assertNull(player.shapes)
    }

    @Test
    fun `full frame crop draws the crop box, eight handles, and the rotation handle`() {
        val player = OverlayPlayer(Rectangle2D.Double(100.0, 50.0, 1600.0, 900.0))
        CropEditorOverlay(player) { }.render(AdjustmentsV1())

        val shapes = assertNotNull(player.shapes)
        val box = shapes.filterIsInstance<OverlayShape.Rect>().single { it.stroke != null && it.width > 100.0 }
        assertEquals(Rectangle2D.Double(100.0, 50.0, 1600.0, 900.0), Rectangle2D.Double(box.x, box.y, box.width, box.height))
        assertEquals(8, shapes.filterIsInstance<OverlayShape.Rect>().count { it.width == 10.0 })
        assertEquals(1, shapes.filterIsInstance<OverlayShape.Circle>().size)
    }

    @Test
    fun `dragging the right edge handle to the left zooms in`() {
        val player = OverlayPlayer(Rectangle2D.Double(0.0, 0.0, 1600.0, 900.0))
        var emitted: AdjustmentsV1? = null
        CropEditorOverlay(player) { emitted = it }.render(AdjustmentsV1())

        player.mouse(MouseEvent.MOUSE_PRESSED, 1600, 450)
        player.mouse(MouseEvent.MOUSE_DRAGGED, 1500, 450)

        val zoom = assertNotNull(emitted).zoom
        assertEquals(1600.0 / 1400.0, zoom.toDouble(), 0.01)
    }

    @Test
    fun `dragging the rotation handle changes only the rotation`() {
        val player = OverlayPlayer(Rectangle2D.Double(0.0, 0.0, 1600.0, 900.0))
        var emitted: AdjustmentsV1? = null
        CropEditorOverlay(player) { emitted = it }.render(AdjustmentsV1())

        // The rotation handle is 24 px above the top-center of the crop box.
        player.mouse(MouseEvent.MOUSE_PRESSED, 800, -24)
        player.mouse(MouseEvent.MOUSE_DRAGGED, 900, -24)

        val next = assertNotNull(emitted)
        assertTrue(next.rotationDeg > 2.0f, "rotation=${next.rotationDeg}")
        assertEquals(1.0f, next.zoom)
    }

    private class OverlayPlayer(private val bounds: Rectangle2D.Double?) : SwingMediaPlayer {
        var shapes: List<OverlayShape>? = null
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        override var onVideoBoundsChanged: (() -> Unit)? = null

        fun mouse(id: Int, x: Int, y: Int) {
            component.dispatchEvent(MouseEvent(component, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1))
        }

        override fun videoBounds(): Rectangle2D.Double? = bounds
        override fun setEditorOverlay(shapes: List<OverlayShape>?) { this.shapes = shapes }
        override fun load(file: File) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seek(ms: Long) = Unit
        override fun setRate(rate: Float) = Unit
        override fun status() = PlayerStatus.PAUSED
        override fun currentTimeMs() = 0L
        override fun totalDurationMs() = 0L
        override fun isAdjustSupported() = true
        override fun applyColorAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyGeometryAdjustments(adj: AdjustmentsV1) = true
        override fun applyPreviewAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyPreviewRotation(rotationDeg: Float, reason: String) = Unit
        override fun setPreviewOverlay(overlay: VideoOverlay?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long) = 0L
        override fun stepFrameBackward(minimumTimeMs: Long) = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() = Unit
    }
}
