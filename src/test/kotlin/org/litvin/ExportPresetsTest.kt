package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportPresetsTest {
    @Test
    fun exposesTheCodeOwnedRuntimeChoices() {
        assertEquals(
            listOf("Original quality", "Balanced", "Fast export", "Custom"),
            ExportPresetsIO.load().map(ExportPreset::label),
        )
    }
}
