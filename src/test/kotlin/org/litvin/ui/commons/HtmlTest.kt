package org.litvin.ui.commons

import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTest {
    @Test
    fun escapeHtml_escapesSpecialCharacters() {
        val input = "<&>\"' and text"
        val escaped = Html.escapeHtml(input)
        assertEquals("&lt;&amp;&gt;&quot;&#39; and text", escaped)
    }

    @Test
    fun escapeHtml_doubleEscapesAmpersandsInPreEscapedSequences() {
        val input = "&lt;&amp;&gt;&quot;&#39;"
        val escaped = Html.escapeHtml(input)
        // Current implementation replaces '&' first, so previously escaped sequences get their '&' doubled
        assertEquals("&amp;lt;&amp;amp;&amp;gt;&amp;quot;&amp;#39;", escaped)
    }

    @Test
    fun setWrapped_setsHtmlWithDefaultWidthAndEscapes() {
        val label = JLabel()
        Html.setWrapped(label, "Hello <world> & 'friends' \"ok\"")
        val expected = "<html><div style='width:296px'>Hello &lt;world&gt; &amp; &#39;friends&#39; &quot;ok&quot;</div></html>"
        assertEquals(expected, label.text)
    }

    @Test
    fun setWrapped_allowsCustomWidth() {
        val label = JLabel()
        Html.setWrapped(label, "text", widthPx = 420)
        assertEquals("<html><div style='width:420px'>text</div></html>", label.text)
    }
}
