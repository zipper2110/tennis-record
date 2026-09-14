package org.litvin.ui.privacy

import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsPreferences
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.Window
import java.net.URI
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTextArea

object PrivacySettingsDialog {
    fun show(owner: Window, controller: AnalyticsController, preferences: AnalyticsPreferences, privacyUrl: URI) {
        JDialog(owner, "Privacy", Dialog.ModalityType.MODELESS).apply {
            layout = BorderLayout(12, 12)
            val toggle = JCheckBox("Send optional usage analytics", preferences.resolve().isEnabled)
            toggle.addActionListener { if (toggle.isSelected) controller.enable() else controller.disable() }
            add(JTextArea("Collected: approved product action categories only. Excluded: video, audio, filenames, paths, project data, scores, player data, identifiers, and diagnostics.").apply {
                isEditable = false; isOpaque = false; lineWrap = true; wrapStyleWord = true
            }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                add(toggle)
                add(JButton("Privacy notice").apply { addActionListener { PrivacyLinkOpener.DesktopBrowser.open(privacyUrl) } })
                add(JButton("Contact").apply { addActionListener { PrivacyLinkOpener.DesktopBrowser.open(URI("mailto:leetvin@gmail.com")) } })
            }, BorderLayout.SOUTH)
            setSize(520, 210); setLocationRelativeTo(owner); isVisible = true
        }
    }
}
