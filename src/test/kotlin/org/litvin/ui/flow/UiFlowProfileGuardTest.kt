package org.litvin.ui.flow

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UiFlowProfileGuardTest {

    @Test
    fun `ui flow harness property is absent during Surefire`() {
        assertNull(
            System.getProperty("tennis.record.uiFlow"),
            "tennis.record.uiFlow must be configured only for the Failsafe UI-flow execution",
        )
    }
}
