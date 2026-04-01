package org.litvin

import javafx.scene.control.Alert
import javafx.scene.control.ButtonType

object DialogUtils {
    fun info(title: String, header: String? = null, content: String? = null) {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = title
        alert.headerText = header
        alert.contentText = content
        alert.showAndWait()
    }

    fun warn(title: String, header: String? = null, content: String? = null) {
        val alert = Alert(Alert.AlertType.WARNING)
        alert.title = title
        alert.headerText = header
        alert.contentText = content
        alert.showAndWait()
    }

    fun error(title: String, header: String? = null, content: String? = null) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.headerText = header
        alert.contentText = content
        alert.showAndWait()
    }

    fun confirm(title: String, header: String? = null, content: String? = null): Boolean {
        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = title
        alert.headerText = header
        alert.contentText = content
        val res = alert.showAndWait()
        return res.isPresent && res.get() == ButtonType.OK
    }
}
