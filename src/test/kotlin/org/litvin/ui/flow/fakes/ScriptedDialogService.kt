package org.litvin.ui.flow.fakes

import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import java.util.ArrayDeque

sealed interface DialogOutcome {
    data object RealModal : DialogOutcome
    data class Confirmation(val response: Boolean) : DialogOutcome
}

data class DialogCall(val kind: String, val title: String, val message: String)

class ScriptedDialogService(
    private val realDialogs: UserDialogService = SwingUserDialogService(),
) : UserDialogService {
    private val outcomes = ArrayDeque<DialogOutcome>()
    private val recordedCalls = mutableListOf<DialogCall>()
    val calls: List<DialogCall> get() = synchronized(this) { recordedCalls.toList() }

    @Synchronized
    fun script(vararg scripted: DialogOutcome) = scripted.forEach { outcomes += it }

    override fun showInfo(parent: Component?, message: String, title: String) {
        requireReal("info", parent, message, title) { realDialogs.showInfo(parent, message, title) }
    }

    override fun showError(parent: Component?, message: String, title: String) {
        requireReal("error", parent, message, title) { realDialogs.showError(parent, message, title) }
    }

    override fun confirm(parent: Component?, message: String, title: String): Boolean {
        val outcome = take("confirm", message, title)
        return when (outcome) {
            DialogOutcome.RealModal -> realDialogs.confirm(parent, message, title)
            is DialogOutcome.Confirmation -> outcome.response
        }
    }

    private fun requireReal(kind: String, parent: Component?, message: String, title: String, show: () -> Unit) {
        when (val outcome = take(kind, message, title)) {
            DialogOutcome.RealModal -> show()
            is DialogOutcome.Confirmation -> error(
                "Scripted confirmation response cannot satisfy $kind dialog '$title'; recorded calls: $calls",
            )
        }
    }

    @Synchronized
    private fun take(kind: String, message: String, title: String): DialogOutcome {
        recordedCalls += DialogCall(kind, title, message)
        return (if (outcomes.isEmpty()) null else outcomes.removeFirst())
            ?: error("Unexpected $kind dialog '$title'; recorded calls: $recordedCalls")
    }
}
