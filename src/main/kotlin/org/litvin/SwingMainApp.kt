package org.litvin

import com.formdev.flatlaf.FlatDarkLaf
import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.app.AppServices
import org.litvin.app.SwingApplicationFactory
import org.litvin.ui.commons.SwingUserDialogService
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.UIManager

object SwingMainApp {
    private val logger = KotlinLogging.logger {}

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.contains("--diagnostics")) {
            kotlin.system.exitProcess(DistributionDiagnostics.run())
        }
        VlcBootstrap.ensureConfigured()

        try {
            UIManager.setLookAndFeel(FlatDarkLaf())
            UIManager.put("defaultFont", Font("Segoe UI", Font.PLAIN, 14))
        } catch (failure: Exception) {
            logger.warn(failure) { "Failed to initialize FlatLaf look and feel." }
        }

        configureDisplayScaling()
        configureTextRendering()

        val services = try {
            AppServices.production()
        } catch (failure: Throwable) {
            logger.error(failure) { "Application startup failed." }
            SwingUserDialogService().showError(null, failure.message ?: failure.toString(), "Startup error")
            return
        }

        EventQueue.invokeLater {
            Thread.setDefaultUncaughtExceptionHandler { _, failure ->
                logger.error(failure) { "Unexpected uncaught Swing error." }
                services.dialogs.showError(null, failure.message ?: failure.toString(), "Unexpected error")
            }

            try {
                WindowsGpuPreference.ensureHighPerformancePreference()
                applyBaseTheme()
                SwingApplicationFactory.create(
                    services = services,
                    onWindowClosed = { kotlin.system.exitProcess(0) },
                )
            } catch (failure: Throwable) {
                logger.error(failure) { "Application startup failed." }
                services.dialogs.showError(null, failure.message ?: failure.toString(), "Startup error")
                try {
                    services.close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
        }
    }

    private fun configureDisplayScaling() {
        val javaSpec = (System.getProperty("java.specification.version") ?: "11").trim()
        val major = javaSpec.toDoubleOrNull() ?: 11.0

        if (System.getProperty("sun.java2d.uiScale.enabled") == null) {
            System.setProperty("sun.java2d.uiScale.enabled", "true")
        }

        if (major < 9) {
            if (System.getProperty("sun.java2d.dpiaware") == null) {
                System.setProperty("sun.java2d.dpiaware", "true")
            }
            if (System.getProperty("sun.java2d.uiScale") == null) {
                val scale = Toolkit.getDefaultToolkit().screenResolution.toDouble() / 96.0
                if (scale >= 1.25) {
                    val value = String.format(java.util.Locale.US, "%.2f", scale)
                    System.setProperty("sun.java2d.uiScale", value)
                }
            }
        }
    }

    private fun configureTextRendering() {
        try {
            val override = System.getProperty("tennisrecord.textAA")
                ?: System.getenv("TENNISRECORD_TEXT_AA")
            val isWindows = (System.getProperty("os.name") ?: "").lowercase().contains("win")
            val value = when (override?.lowercase()?.trim()) {
                null, "", "auto" -> if (isWindows) "lcd_hrgb" else "on"
                "off" -> "off"
                "on" -> "on"
                "lcd" -> "lcd"
                "lcd-hrgb", "lcd_hrgb" -> "lcd_hrgb"
                "lcd-hbgr", "lcd_hbgr" -> "lcd_hbgr"
                "lcd-vrgb", "lcd_vrgb" -> "lcd_vrgb"
                "lcd-vbgr", "lcd_vbgr" -> "lcd_vbgr"
                else -> if (isWindows) "lcd_hrgb" else "on"
            }
            System.setProperty("swing.aatext", if (value == "off") "false" else "true")
            System.setProperty("awt.useSystemAAFontSettings", value)
            System.setProperty("sun.java2d.fractionalmetrics", "on")
        } catch (_: Throwable) {
            System.setProperty("swing.aatext", "true")
            System.setProperty("awt.useSystemAAFontSettings", "on")
        }
    }

    private fun applyBaseTheme() {
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val family = when {
            families.contains("Segoe UI Variable") -> "Segoe UI Variable"
            families.contains("Segoe UI") -> "Segoe UI"
            else -> "Tahoma"
        }
        val baseSize = (UIManager.getFont("Label.font")?.size2D ?: 13f).coerceAtLeast(13f)
        val baseFont = Font(family, Font.PLAIN, baseSize.toInt())

        val keys = UIManager.getDefaults().keys()
        while (keys.hasMoreElements()) {
            val key = keys.nextElement()
            if (key.toString().endsWith(".font")) UIManager.put(key, baseFont)
        }
        UIManager.put("defaultFont", baseFont)
        UIManager.put("ToolTip.hideAccelerator", true)
    }
}
