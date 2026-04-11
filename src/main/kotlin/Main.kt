package org.litvin

import javafx.application.Application

fun main() {
    // Prefer hardware acceleration and reasonable pulse
    System.setProperty("prism.order", "d3d,sw")
    System.setProperty("prism.vsync", "true")
    // Optionally reduce AA in scene graph to lower cost; off by default
    // System.setProperty("prism.text", "t2k")
    Application.launch(MainApp::class.java)
}