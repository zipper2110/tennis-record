package org.litvin.ui.commons

import java.awt.Component
import javax.swing.JOptionPane

/**
 * Dialog helpers for consistent error/info reporting across Swing UI.
 *
 * Preferred entry points:
 * - {@link #showError} for exceptions and error messages
 * - {@link #showInfo} for informational messages
 *
 * Migration note (v0.3.0): moved from `org.litvin.SwingDialogUtils` and renamed to `Dialogs`.
 */
object Dialogs {
    fun showError(parent: Component?, throwable: Throwable, title: String = "Error") {
        val message = buildString {
            append(throwable.message ?: throwable.toString())
        }
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE)
    }

    fun showInfo(parent: Component?, message: String, title: String = "Info") {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE)
    }
}
