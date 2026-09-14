package org.litvin.ui.privacy

import java.awt.Desktop
import java.net.URI

fun interface PrivacyLinkOpener {
    fun open(url: URI): Boolean

    companion object {
        val DesktopBrowser = PrivacyLinkOpener { url ->
            runCatching { Desktop.getDesktop().browse(url) }.isSuccess
        }
    }
}
