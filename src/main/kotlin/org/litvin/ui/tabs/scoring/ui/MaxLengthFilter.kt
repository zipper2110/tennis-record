package org.litvin.ui.tabs.scoring.ui

import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/** Limits a text field to [maxLength] characters. Longer input is cut at the limit. */
internal class MaxLengthFilter(private val maxLength: Int) : DocumentFilter() {
    override fun insertString(fb: FilterBypass, offset: Int, text: String?, attr: AttributeSet?) {
        replace(fb, offset, 0, text, attr)
    }

    override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
        val allowed = maxLength - (fb.document.length - length)
        val clipped = text.orEmpty().take(allowed.coerceAtLeast(0))
        super.replace(fb, offset, length, clipped, attrs)
    }
}
