package org.litvin.export

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportResolutionsTest {
    @Test
    fun prefersTheSourceResolutionOverAStoredResolutionWhenOpeningExport() {
        val sourceResolution = ExportResolution("4K", 3840, 2160)

        val selected = ExportResolutions.preferredOption(
            options = ExportResolutions.availableFor(sourceResolution),
            savedResolution = "1080p",
        )

        assertEquals("4K (source)", selected?.label)
    }
}
