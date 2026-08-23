package org.litvin.ui.commons

import java.awt.Component
import javax.swing.JOptionPane

interface UserDialogService {
    fun showInfo(parent: Component?, message: String, title: String = "Info")
    fun showError(parent: Component?, message: String, title: String = "Error")
    fun confirm(parent: Component?, message: String, title: String): Boolean
}

class SwingUserDialogService : UserDialogService {
    override fun showInfo(parent: Component?, message: String, title: String) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE)
    }

    override fun showError(parent: Component?, message: String, title: String) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE)
    }

    override fun confirm(parent: Component?, message: String, title: String): Boolean =
        JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
}
