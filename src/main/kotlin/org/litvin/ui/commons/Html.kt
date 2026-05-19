package org.litvin.ui.commons

import javax.swing.JLabel

/**
 * Small HTML/text helpers for Swing labels.
 * Kept free of any ui.tabs.* dependencies.
 */
object Html {
    @JvmStatic
    fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /**
     * Wrap long text in JLabel using HTML container of fixed width so BoxLayout can grow height.
     */
    @JvmStatic
    fun setWrapped(label: JLabel, text: String, widthPx: Int = 296) {
        label.text = "<html><div style='width:${widthPx}px'>${escapeHtml(text)}</div></html>"
    }
}