package org.litvin.ui.commons

import com.formdev.flatlaf.extras.FlatSVGUtils
import java.awt.Image

/** The Tennis Record brand icon. The SVG file in `resources/icons` is the source. */
object AppIcon {
    private const val TILE_SVG = "icons/app-icon.svg"

    /** Window and taskbar icon images in several sizes. */
    fun windowImages(): List<Image> = FlatSVGUtils.createWindowIconImages("/$TILE_SVG")
}
