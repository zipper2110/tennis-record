package org.litvin.ui.privacy

import org.litvin.analytics.AnalyticsController
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.Window
import java.net.URI
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.WindowConstants

object AnalyticsConsentDialog {
    fun show(owner: Window, controller: AnalyticsController, privacyUrl: URI, linkOpener: PrivacyLinkOpener = PrivacyLinkOpener.DesktopBrowser) {
        JDialog(owner, "Optional usage analytics", Dialog.ModalityType.MODELESS).apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            layout = BorderLayout(12, 12)
            add(JTextArea("Help improve Tennis Record by sending optional, anonymous product events. We never collect video, project names, paths, scores, or personal details.").apply {
                isEditable = false; isOpaque = false; lineWrap = true; wrapStyleWord = true
            }, BorderLayout.CENTER)
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT))
            val read = JButton("Read privacy notice")
            read.addActionListener {
                if (!linkOpener.open(privacyUrl)) add(JLabel("Copy this URL: $privacyUrl"), BorderLayout.NORTH)
            }
            actions.add(read)
            actions.add(JButton("No thanks").apply { addActionListener { controller.disable(); dispose() } })
            actions.add(JButton("Enable analytics").apply { addActionListener { controller.enable(); dispose() } })
            add(actions, BorderLayout.SOUTH)
            addWindowListener(object : java.awt.event.WindowAdapter() { override fun windowClosing(e: java.awt.event.WindowEvent) { controller.disable() } })
            setSize(460, 220); setLocationRelativeTo(owner); isVisible = true
        }
    }
}
