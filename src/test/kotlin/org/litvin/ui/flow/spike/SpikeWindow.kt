package org.litvin.ui.flow.spike

import com.formdev.flatlaf.FlatDarkLaf
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

class SpikeWindow private constructor() : JFrame(TITLE) {
    private val input = EdtReadGuardTextField("replace me", 24).apply { name = INPUT_NAME }
    private val submit = EdtReadGuardButton("Open modal").apply { name = SUBMIT_NAME }
    private val slider = JSlider(0, 10, 2).apply { name = SLIDER_NAME }
    private val choice = JComboBox(arrayOf("alpha", "beta")).apply { name = CHOICE_NAME }
    private val result = JLabel("ready").apply { name = RESULT_NAME }

    init {
        name = WINDOW_NAME
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        contentPane = JPanel(BorderLayout(8, 8)).apply {
            border = javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(
                Box.createVerticalBox().apply {
                    add(input)
                    add(Box.createVerticalStrut(8))
                    add(slider)
                    add(Box.createVerticalStrut(8))
                    add(choice)
                    add(Box.createVerticalStrut(8))
                    add(submit)
                },
                BorderLayout.CENTER,
            )
            add(result, BorderLayout.SOUTH)
        }

        slider.addChangeListener { result.text = "slider=${slider.value}" }
        choice.addActionListener { result.text = "choice=${choice.selectedItem}" }
        submit.addActionListener {
            result.text = input.text
            JOptionPane.showOptionDialog(
                this,
                result.text,
                DIALOG_TITLE,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                arrayOf(DIALOG_BUTTON_TEXT),
                DIALOG_BUTTON_TEXT,
            )
        }
        rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(SHORTCUT, SHORTCUT_ACTION)
        rootPane.actionMap.put(
            SHORTCUT_ACTION,
            object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent) {
                    result.text = SHORTCUT_RESULT
                }
            },
        )

        pack()
        setLocationRelativeTo(null)
    }

    companion object {
        const val TITLE = "AssertJ Swing compatibility spike"
        const val WINDOW_NAME = "spike.window"
        const val INPUT_NAME = "spike.input"
        const val SUBMIT_NAME = "spike.submit"
        const val SLIDER_NAME = "spike.slider"
        const val CHOICE_NAME = "spike.choice"
        const val RESULT_NAME = "spike.result"
        const val DIALOG_TITLE = "AssertJ modal discovery"
        const val DIALOG_BUTTON_TEXT = "Dismiss"
        const val SHORTCUT_RESULT = "shortcut accepted"

        private const val SHORTCUT_ACTION = "spike.shortcut"
        private val SHORTCUT = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)

        fun show(): SpikeWindow {
            val frame = AtomicReference<SpikeWindow>()
            SwingUtilities.invokeAndWait {
                check(FlatDarkLaf.setup()) { "FlatDarkLaf could not be installed" }
                frame.set(SpikeWindow().apply { isVisible = true })
            }
            return frame.get()
        }
    }
}

private class EdtReadGuardTextField(text: String, columns: Int) : JTextField(text, columns) {
    override fun isShowing(): Boolean {
        requireEdtRead("text field visibility")
        return super.isShowing()
    }

    override fun isEnabled(): Boolean {
        requireEdtRead("text field enabled state")
        return super.isEnabled()
    }
}

private class EdtReadGuardButton(text: String) : JButton(text) {
    override fun isShowing(): Boolean {
        requireEdtRead("button visibility")
        return super.isShowing()
    }

    override fun isEnabled(): Boolean {
        requireEdtRead("button enabled state")
        return super.isEnabled()
    }
}

private fun requireEdtRead(description: String) {
    check(SwingUtilities.isEventDispatchThread()) { "$description read off the EDT" }
}
