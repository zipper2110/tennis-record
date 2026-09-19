package org.litvin.media.mpv

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.ui.tabs.crop.SwingCropRotatePanel
import org.litvin.ui.tabs.crop.presenter.CropRotateIntent
import org.litvin.ui.tabs.crop.presenter.DefaultCropRotatePresenter
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Manual spike harness for the live Crop/Rotate tab (not a unit test). It does not move the mouse.
 *
 * Run: mvn -q test-compile exec:java -Dexec.classpathScope=test
 *        -Dapp.mainClass=org.litvin.media.mpv.MpvCropTabSpikeMainKt -Dexec.args="<video> <output dir>"
 */
fun main(args: Array<String>) {
    val video = File(args.getOrElse(0) { "D:\\PXL_20260828_100310593.mp4" })
    val outDir = File(args.getOrElse(1) { "target/mpv-crop-spike" }).apply { mkdirs() }
    val projectDir = File(outDir, "project").apply { mkdirs() }
    val manifest = File(projectDir, "project.json")
    ManifestIO.write(
        manifest.absolutePath,
        ProjectManifestV1(id = "spike", name = "Crop spike", createdAt = "2026-09-20", lastOpenedAt = "2026-09-20", sourceVideo = video.absolutePath),
    )

    val scheduler = Executors.newSingleThreadScheduledExecutor()
    val adjustments = AdjustmentsSession(scheduler)
    val presenter = DefaultCropRotatePresenter(adjustments)
    lateinit var frame: JFrame
    lateinit var panel: SwingCropRotatePanel
    SwingUtilities.invokeAndWait {
        panel = SwingCropRotatePanel(MpvSwingMediaPlayerAdapter(), presenter)
        frame = JFrame("Crop tab spike").apply {
            contentPane.add(panel)
            setSize(1500, 860)
            setLocationRelativeTo(null)
            isVisible = true
        }
    }
    Thread.sleep(600)
    SwingUtilities.invokeAndWait {
        panel.setProjectManifest(manifest.absolutePath)
        panel.onActivated()
    }
    Thread.sleep(2500)

    val robot = Robot()
    fun shot(name: String) {
        val bounds = Rectangle(panel.locationOnScreen, panel.size)
        ImageIO.write(robot.createScreenCapture(bounds), "png", File(outDir, "$name.png"))
        println("saved $name")
    }
    shot("1-default")

    SwingUtilities.invokeAndWait {
        presenter.onIntent(CropRotateIntent.ChangeTransform(adjustments.get().copy(rotationDeg = 8.0f, zoom = 1.5f, panX = 0.4f, panY = -0.3f)))
    }
    Thread.sleep(1200)
    shot("2-rotated-zoomed")

    SwingUtilities.invokeAndWait { frame.setSize(1100, 900) }
    Thread.sleep(1500)
    shot("3-after-resize")

    SwingUtilities.invokeAndWait {
        panel.dispose()
        frame.dispose()
    }
    adjustments.close()
    scheduler.shutdownNow()
    System.exit(0)
}
