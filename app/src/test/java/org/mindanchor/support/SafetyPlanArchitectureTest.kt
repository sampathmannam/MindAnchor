package org.mindanchor.support

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class SafetyPlanArchitectureTest {
    private fun source(path: String) = File(path).readText(Charsets.UTF_8)

    @Test
    fun composeOwnsNoSafetyPlanSaveOrDraftState() {
        val screen = source("src/main/java/org/mindanchor/support/SupportScreen.kt")
        listOf("rememberSaveable", "closeAfterSave", "SafetyPlanDraftState", "consumeSaveSuccess")
            .forEach { forbidden -> assertFalse("found $forbidden", forbidden in screen) }
    }

    @Test
    fun viewModelContainsNoTerminalTimeoutOrSavedHandshake() {
        val viewModel = source("src/main/java/org/mindanchor/support/SupportViewModel.kt")
        listOf("withTimeout", "NonCancellable", "compareAndSet", "SafetyPlanSaveState", "Saved")
            .forEach { forbidden -> assertFalse("found $forbidden", forbidden in viewModel) }
    }
}
