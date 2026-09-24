package org.litvin.ui.tabs.scoring.ui

import org.litvin.scoring.DeuceRule
import org.litvin.scoring.FinalSetRule
import org.litvin.scoring.MatchFormatPreset
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.MatchStructure
import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.text.AbstractDocument

/** The values that the "Scoring settings" dialog edits. */
data class ScoreSettings(
    val player1Name: String,
    val player2Name: String,
    val player1ColorHex: String,
    val player2ColorHex: String,
    val rules: MatchRulesV1,
)

/** Opens the score settings and returns the saved values, or null after Cancel. Tests replace the dialog with a fake. */
fun interface ScoreSettingsEditor {
    fun edit(parent: Component, current: ScoreSettings): ScoreSettings?
}

/**
 * Modal "Scoring settings" dialog: player names and colors, the match format (point counting rules),
 * and the fully manual scoring option.
 *
 * The format list holds popular formats. The rule fields under it show the rules of the selected format.
 * A change to a rule field selects the matching format, or "Custom".
 */
class ScoreSettingsDialog private constructor(
    owner: Window?,
    initial: ScoreSettings,
) : JDialog(owner, "Scoring settings", Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : ScoreSettingsEditor {
        const val MAX_NAME_LENGTH = 24
        private const val TEXT_WIDTH_PX = 400

        override fun edit(parent: Component, current: ScoreSettings): ScoreSettings? {
            val dialog = ScoreSettingsDialog(SwingUtilities.getWindowAncestor(parent), current)
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
            return dialog.result
        }
    }

    /** A combo box item with a display text. */
    private data class Choice<T>(val value: T, val label: String) {
        override fun toString(): String = label
    }

    private var rules = initial.rules.normalized()
    private var player1Color = initial.player1ColorHex
    private var player2Color = initial.player2ColorHex
    private var result: ScoreSettings? = null
    private var updatingControls = false

    private val player1Name = nameField("score-settings-player-1-name", initial.player1Name)
    private val player2Name = nameField("score-settings-player-2-name", initial.player2Name)
    private val player1ColorButton = colorButton("score-settings-player-1-color", "Player 1") { player1Color }
    private val player2ColorButton = colorButton("score-settings-player-2-color", "Player 2") { player2Color }

    private val format = combo("score-settings-format", MatchFormatPreset.entries.map { Choice(it, it.title) })
    private val formatDescription = JLabel().apply {
        name = "score-settings-format-description"
        foreground = UiStyles.FG_SECONDARY
    }
    private val bestOf = combo(
        "score-settings-sets",
        listOf(Choice(1, "One set"), Choice(3, "Best of 3 sets"), Choice(5, "Best of 5 sets")),
    )
    private val gamesPerSet = combo(
        "score-settings-games-per-set",
        MatchRulesV1.GAMES_PER_SET_OPTIONS.map { Choice(it, "$it games") },
    )
    private val setTiebreak = combo("score-settings-set-tiebreak", setTiebreakChoices(rules.gamesPerSet))
    private val tiebreakPoints = combo(
        "score-settings-tiebreak-points",
        MatchRulesV1.TIEBREAK_POINTS_OPTIONS.map { Choice(it, "$it points") },
    )
    private val finalSet = combo(
        "score-settings-final-set",
        listOf(
            Choice(FinalSetRule.FULL_SET, "Full set"),
            Choice(FinalSetRule.MATCH_TIEBREAK, "${MatchRulesV1.MATCH_TIEBREAK_POINTS}-point match tiebreak"),
        ),
    )
    private val deuce = combo(
        "score-settings-deuce",
        listOf(
            Choice(DeuceRule.ADVANTAGE, "Advantage (win by 2 points)"),
            Choice(DeuceRule.NO_AD, "No-ad (deciding point at 40–40)"),
        ),
    )
    private val manualScoring = JCheckBox("Fully manual scoring").apply {
        name = "score-settings-manual"
        isOpaque = false
        toolTipText = "The app counts points only. You mark each game and set win."
    }
    private val manualHint = JLabel(
        "<html><body style='width: ${TEXT_WIDTH_PX}px'>The app counts points only. " +
            "Use the Game Won and Set Won buttons under the video to mark each win.</body></html>",
    ).apply { foreground = UiStyles.FG_SECONDARY }

    /** Rule rows: the label and the control, so that a disabled rule also dims its label. */
    private val ruleRows = mutableMapOf<JComponent, JLabel>()

    init {
        name = "score-settings-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE

        format.addActionListener {
            val preset = selected(format) ?: return@addActionListener
            update { preset.applyTo(it) }
        }
        bestOf.addActionListener { selected(bestOf)?.let { value -> update { it.copy(bestOfSets = value) } } }
        gamesPerSet.addActionListener { selected(gamesPerSet)?.let { value -> update { it.copy(gamesPerSet = value) } } }
        setTiebreak.addActionListener { selected(setTiebreak)?.let { value -> update { it.copy(setTiebreak = value) } } }
        tiebreakPoints.addActionListener { selected(tiebreakPoints)?.let { value -> update { it.copy(tiebreakPoints = value) } } }
        finalSet.addActionListener { selected(finalSet)?.let { value -> update { it.copy(finalSet = value) } } }
        deuce.addActionListener { selected(deuce)?.let { value -> update { it.copy(deuce = value) } } }
        manualScoring.addActionListener { update { it.copy(manualScoring = manualScoring.isSelected) } }
        player1ColorButton.addActionListener {
            chooseColor("Player 1 color", player1Color)?.let { player1Color = it }
            syncControls()
        }
        player2ColorButton.addActionListener {
            chooseColor("Player 2 color", player2Color)?.let { player2Color = it }
            syncControls()
        }

        val saveButton = UiStyles.primarySmallButton("Save") { save() }.apply { name = "score-settings-save" }
        val cancelButton = JButton("Cancel").apply {
            name = "score-settings-cancel"
            addActionListener { dispose() }
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(cancelButton)
            add(saveButton)
        }

        contentPane = JPanel(BorderLayout(0, 16)).apply {
            border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
            add(form(), BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        rootPane.defaultButton = saveButton
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "score-settings-cancel")
        rootPane.actionMap.put("score-settings-cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = dispose()
        })

        syncControls()
        pack()
        minimumSize = size
    }

    private fun form(): JPanel {
        val form = JPanel(GridBagLayout()).apply { isOpaque = false }
        val c = GridBagConstraints().apply { anchor = GridBagConstraints.WEST }
        var row = 0
        fun section(title: String) {
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.weightx = 1.0
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(if (row == 1) 0 else 14, 0, 6, 0)
            form.add(JLabel(title.uppercase()).apply {
                foreground = UiStyles.FG_SECONDARY
                font = font.deriveFont(Font.BOLD, font.size2D - 1f)
            }, c)
            c.gridwidth = 1
        }
        fun addRow(label: String, component: JComponent, isRule: Boolean = false) {
            val rowLabel = JLabel(label).apply { foreground = UiStyles.FG_SECONDARY }
            c.gridx = 0; c.gridy = row; c.weightx = 0.0; c.fill = GridBagConstraints.NONE
            c.insets = Insets(4, 0, 4, 14)
            form.add(rowLabel, c)
            c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(4, 0, 4, 0)
            form.add(component, c)
            if (isRule) ruleRows[component] = rowLabel
            row++
        }
        fun addWide(component: JComponent, top: Int = 4) {
            c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.weightx = 1.0
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(top, 0, 4, 0)
            form.add(component, c)
            c.gridwidth = 1
        }

        section("Players")
        addRow("Player 1", playerRow(player1Name, player1ColorButton))
        addRow("Player 2", playerRow(player2Name, player2ColorButton))

        section("Match format")
        addRow("Format", format, isRule = true)
        addRow("", formatDescription)
        addRow("Sets", bestOf, isRule = true)
        addRow("Games in a set", gamesPerSet, isRule = true)
        addRow("Set tiebreak", setTiebreak, isRule = true)
        addRow("Tiebreak to", tiebreakPoints, isRule = true)
        addRow("Deciding set", finalSet, isRule = true)
        addRow("Deuce", deuce, isRule = true)

        section("Manual scoring")
        addWide(manualScoring, top = 0)
        addWide(manualHint, top = 0)
        return form
    }

    private fun playerRow(field: JTextField, colorButton: JButton) = JPanel(BorderLayout(8, 0)).apply {
        isOpaque = false
        add(field, BorderLayout.CENTER)
        add(colorButton, BorderLayout.EAST)
    }

    /** Applies [change] to the rules, then refreshes the controls. */
    private fun update(change: (MatchRulesV1) -> MatchRulesV1) {
        if (updatingControls) return
        rules = change(rules).normalized()
        syncControls()
    }

    private fun syncControls() {
        updatingControls = true
        try {
            val preset = MatchFormatPreset.of(rules)
            select(format, preset)
            formatDescription.text = "<html><body style='width: ${TEXT_WIDTH_PX - 120}px'>${preset.description}</body></html>"
            select(bestOf, rules.bestOfSets)
            select(gamesPerSet, rules.gamesPerSet)
            // The tiebreak choice names the game score, for example "Tiebreak at 6–6".
            val tiebreakChoices = setTiebreakChoices(rules.gamesPerSet)
            if (setTiebreak.getItemAt(0) != tiebreakChoices[0]) {
                setTiebreak.removeAllItems()
                tiebreakChoices.forEach(setTiebreak::addItem)
            }
            select(setTiebreak, rules.setTiebreak)
            select(tiebreakPoints, rules.tiebreakPoints)
            select(finalSet, rules.finalSet)
            select(deuce, rules.deuce)
            manualScoring.isSelected = rules.manualScoring

            val automatic = !rules.manualScoring
            val sets = rules.structure == MatchStructure.SETS
            setRuleEnabled(format, automatic)
            setRuleEnabled(bestOf, automatic && sets)
            setRuleEnabled(gamesPerSet, automatic && sets)
            setRuleEnabled(setTiebreak, automatic && sets)
            setRuleEnabled(
                tiebreakPoints,
                automatic && (rules.structure == MatchStructure.SINGLE_TIEBREAK || (sets && rules.setTiebreak)),
            )
            setRuleEnabled(finalSet, automatic && sets && rules.bestOfSets > 1)
            setRuleEnabled(deuce, automatic && rules.structure != MatchStructure.SINGLE_TIEBREAK)
            formatDescription.foreground = if (automatic) UiStyles.FG_SECONDARY else UiStyles.FG_DISABLED
            manualHint.foreground = if (rules.manualScoring) UiStyles.FG_SECONDARY else UiStyles.FG_DISABLED

            player1ColorButton.icon = UiStyles.colorSwatchIcon(colorOf(player1Color))
            player2ColorButton.icon = UiStyles.colorSwatchIcon(colorOf(player2Color))
        } finally {
            updatingControls = false
        }
    }

    private fun setRuleEnabled(component: JComponent, enabled: Boolean) {
        component.isEnabled = enabled
        ruleRows[component]?.foreground = if (enabled) UiStyles.FG_SECONDARY else UiStyles.FG_DISABLED
    }

    private fun save() {
        result = ScoreSettings(
            player1Name = player1Name.text.trim(),
            player2Name = player2Name.text.trim(),
            player1ColorHex = player1Color,
            player2ColorHex = player2Color,
            rules = rules.normalized(),
        )
        dispose()
    }

    private fun chooseColor(title: String, currentHex: String): String? {
        val chosen = JColorChooser.showDialog(this, title, colorOf(currentHex)) ?: return null
        return "#%06X".format(chosen.rgb and 0xFFFFFF)
    }

    private fun colorOf(hex: String): Color =
        Color(hex.trim().removePrefix("#").toIntOrNull(16) ?: 0x4DA3FF)

    private fun nameField(componentName: String, text: String) = JTextField(text, 18).apply {
        name = componentName
        (document as? AbstractDocument)?.documentFilter = MaxLengthFilter(MAX_NAME_LENGTH)
    }

    private fun colorButton(componentName: String, player: String, hex: () -> String) = JButton("Color…").apply {
        name = componentName
        isFocusPainted = false
        toolTipText = "Pick the $player color for the buttons and the scoreboard"
        icon = UiStyles.colorSwatchIcon(colorOf(hex()))
    }

    private fun setTiebreakChoices(games: Int) = listOf(
        Choice(true, "Tiebreak at $games–$games"),
        Choice(false, "No tiebreak (win by 2 games)"),
    )

    private fun <T> combo(componentName: String, choices: List<Choice<T>>) = JComboBox<Choice<T>>().apply {
        name = componentName
        choices.forEach(::addItem)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> selected(combo: JComboBox<Choice<T>>): T? = (combo.selectedItem as? Choice<T>)?.value

    private fun <T> select(combo: JComboBox<Choice<T>>, value: T) {
        for (i in 0 until combo.itemCount) {
            if (combo.getItemAt(i).value == value) {
                if (combo.selectedIndex != i) combo.selectedIndex = i
                return
            }
        }
    }
}
