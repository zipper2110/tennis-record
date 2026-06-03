package org.litvin.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlcPreviewMediaOptionsTest {
    @Test
    fun identityRotationUsesOnlyStartPaused() {
        assertContentEquals(
            arrayOf(":start-paused"),
            VlcPreviewMediaOptions.startPaused()
        )
    }

    @Test
    fun nonCardinalRotationAddsRotateFactoryOptions() {
        assertContentEquals(
            arrayOf("--video-filter=rotate", "--rotate-angle=12.5"),
            VlcPreviewMediaOptions.factoryArguments(-12.5f)
        )
    }

    @Test
    fun cardinalRotationUsesTransformFactoryOptions() {
        assertContentEquals(
            arrayOf("--video-filter=transform", "--transform-type=270"),
            VlcPreviewMediaOptions.factoryArguments(90.0f)
        )
    }

    @Test
    fun rotationIsClampedToModelRange() {
        assertContentEquals(
            arrayOf("--video-filter=transform", "--transform-type=180"),
            VlcPreviewMediaOptions.factoryArguments(270.0f)
        )
    }

    @Test
    fun rotationComparisonUsesSmallEpsilon() {
        assertTrue(VlcPreviewMediaOptions.isSameRotation(10.0f, 10.0005f))
        assertFalse(VlcPreviewMediaOptions.isSameRotation(10.0f, 10.01f))
    }
}
