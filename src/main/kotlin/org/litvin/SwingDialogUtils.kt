package org.litvin

import java.awt.Component
import javax.swing.JOptionPane

/**
 * Simple Swing dialog helpers for consistent error/info reporting.
 */
object SwingDialogUtils {
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