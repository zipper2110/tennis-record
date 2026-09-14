package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportPresetsTest {
    @Test
    fun exposesTheCodeOwnedRuntimeChoices() {
        assertEquals(
            listOf("Data Saver", "Balanced", "High", "Very High", "Maximum"),
            ExportPresetsIO.load().map(ExportPreset::label),
        )
    }
}
