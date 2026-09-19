package org.litvin.media.mpv

import org.litvin.adjustments.AdjustmentsV1
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.sin

/**
 * Manual spike harness for the mpv preview (not a unit test).
 *
 * Run: mvn -q test-compile exec:java -Dexec.classpathScope=test
 *        -Dexec.mainClass=org.litvin.media.mpv.MpvPreviewSpikeMainKt -Dexec.args="<video> <output dir>"
 */
fun main(args: Array<String>) {
    val video = File(args.getOrElse(0) { "D:\\PXL_20260828_100310593.mp4" })
    val outDir = File(args.getOrElse(1) { "target/mpv-spike" }).apply { mkdirs() }
    val report = StringBuilder()
    fun log(line: String) {
        println(line)
        report.appendLine(line)
    }

    val player = MpvSwingMediaPlayerAdapter()
    val ready = CountDownLatch(1)
    val timeEvents = AtomicInteger(0)
    val mouseEvents = AtomicInteger(0)
    val keyEvents = AtomicInteger(0)
    player.onReady = { ready.countDown() }
    player.onTimeChanged = { timeEvents.incrementAndGet() }

    lateinit var frame: JFrame
    lateinit var cards: JPanel
    SwingUtilities.invokeAndWait {
        frame = JFrame("mpv preview spike")
        cards = JPanel(CardLayout())
        cards.add(player.component, "video")
        cards.add(JLabel("Other tab", JLabel.CENTER).apply { font = Font("SansSerif", Font.BOLD, 40) }, "other")
        player.component.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                mouseEvents.incrementAndGet()
            }
        })
        player.component.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                keyEvents.incrementAndGet()
            }
        })
        frame.contentPane.add(cards, BorderLayout.CENTER)
        frame.setSize(1300, 780)
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.isVisible = true
    }
    Thread.sleep(500)
    SwingUtilities.invokeAndWait { player.load(video) }
    val loadStart = System.nanoTime()
    check(ready.await(20, TimeUnit.SECONDS)) { "mpv did not load the file" }
    log("load -> ready: ${(System.nanoTime() - loadStart) / 1_000_000} ms; duration=${player.totalDurationMs()} ms")
    log("hwdec=${player.debugProperty("hwdec-current")} vo=${player.debugProperty("current-vo")} " +
        "size=${player.debugProperty("video-params/w")}x${player.debugProperty("video-params/h")} " +
        "fps=${player.debugProperty("container-fps")} matrix=${player.debugProperty("video-params/colormatrix")}")

    val robot = Robot()
    fun screenshot(name: String) {
        val bounds = Rectangle(player.component.locationOnScreen, player.component.size)
        val image: BufferedImage = robot.createScreenCapture(bounds)
        ImageIO.write(image, "png", File(outDir, "$name.png"))
    }
    fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    // A. Seek, then take a still with and without strong adjustments.
    onEdt { player.seek(300_000) }
    Thread.sleep(1500)
    screenshot("a1-plain")
    onEdt { player.applyPreviewAdjustments(AdjustmentsV1(contrast = 1.6f, rotationDeg = 8.0f)) }
    Thread.sleep(800)
    screenshot("a2-contrast-rotation-paused")
    log("A. paused adjustment applied; shader-opts=${player.debugProperty("glsl-shader-opts")?.take(90)}...")

    // B. Play 15 s at 4K60 while the adjustments change every 50 ms (a slider drag).
    val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
    val drops0 = player.debugProperty("frame-drop-count")?.toLongOrNull() ?: 0L
    val voDrops0 = player.debugProperty("vo-drop-frame-count")?.toLongOrNull() ?: 0L
    timeEvents.set(0)
    onEdt { player.play() }
    Thread.sleep(2000)
    val cpu0 = os.processCpuTime
    val t0 = System.nanoTime()
    var step = 0
    while (System.nanoTime() - t0 < 15_000_000_000L) {
        step++
        val adj = AdjustmentsV1(
            contrast = (1.0 + 0.4 * sin(step / 7.0)).toFloat(),
            brightness = (1.0 + 0.2 * sin(step / 11.0)).toFloat(),
            rotationDeg = (6.0 * sin(step / 10.0)).toFloat(),
            zoom = 1.3f,
        )
        onEdt { player.applyPreviewAdjustments(adj) }
        if (step == 100) screenshot("b-playing-animated")
        Thread.sleep(50)
    }
    val wall = (System.nanoTime() - t0) / 1e9
    val cpuCores = (os.processCpuTime - cpu0) / 1e9 / wall
    val drops = (player.debugProperty("frame-drop-count")?.toLongOrNull() ?: 0L) - drops0
    val voDrops = (player.debugProperty("vo-drop-frame-count")?.toLongOrNull() ?: 0L) - voDrops0
    log(String.format(
        "B. 15 s play with %d live updates: JVM+mpv CPU = %.2f cores; decoder drops=%d; vo drops=%d; " +
            "onTimeChanged=%d (%.0f/s); est fps=%s",
        step, cpuCores, drops, voDrops, timeEvents.get(), timeEvents.get() / (wall + 2.0),
        player.debugProperty("estimated-vf-fps"),
    ))

    // C. Seek and frame-step accuracy (paused).
    onEdt { player.pause() }
    Thread.sleep(300)
    onEdt { player.seek(400_000) }
    Thread.sleep(1200)
    val afterSeek = player.debugProperty("time-pos")
    val forward = mutableListOf<String>()
    repeat(5) {
        onEdt { player.stepFrameForward() }
        Thread.sleep(250)
        forward += player.debugProperty("time-pos") ?: "?"
    }
    val backward = mutableListOf<String>()
    repeat(5) {
        onEdt { player.stepFrameBackward() }
        Thread.sleep(600)
        backward += player.debugProperty("time-pos") ?: "?"
    }
    log("C. seek 400.000 -> time-pos=$afterSeek; forward=$forward; backward=$backward")

    // D. Tab switches: hide the video card and unload, then show it and load again.
    val latencies = mutableListOf<Long>()
    repeat(10) { index ->
        onEdt {
            player.pause()
            player.deactivatePreview("spike switch $index")
            (cards.layout as CardLayout).show(cards, "other")
        }
        Thread.sleep(300)
        val start = System.nanoTime()
        onEdt {
            (cards.layout as CardLayout).show(cards, "video")
            player.activatePreview("spike switch $index")
        }
        while (!player.isFileLoaded && System.nanoTime() - start < 10_000_000_000L) Thread.sleep(10)
        latencies += (System.nanoTime() - start) / 1_000_000
        Thread.sleep(200)
    }
    log("D. 10 tab switches: reload latency ms=$latencies; position after=${player.currentTimeMs()} ms; " +
        "status=${player.status()}")
    Thread.sleep(700)
    screenshot("d-after-tab-switches")

    // E. Scoreboard overlay.
    val overlay = BufferedImage(440, 166, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        g.color = Color(20, 40, 90, 220)
        g.fillRoundRect(0, 0, 440, 166, 24, 24)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 48)
        g.drawString("6-4  3-2  40-15", 24, 100)
        g.dispose()
    }
    onEdt { player.setPreviewOverlayImage(overlay) }
    Thread.sleep(800)
    screenshot("e-overlay")
    log("E. overlay set")

    // F. Mouse and keyboard reach Java through the mpv window.
    onEdt { player.component.requestFocusInWindow() }
    Thread.sleep(300)
    val center = player.component.locationOnScreen.let {
        java.awt.Point(it.x + player.component.width / 2, it.y + player.component.height / 2)
    }
    robot.mouseMove(center.x, center.y)
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    Thread.sleep(400)
    // No focus request here: the key press must reach Java right after a click on the video.
    robot.keyPress(KeyEvent.VK_RIGHT)
    robot.keyRelease(KeyEvent.VK_RIGHT)
    Thread.sleep(300)
    log("F. mouse presses seen by Java=${mouseEvents.get()}; key presses seen by Java=${keyEvents.get()}; " +
        "focus owner=${java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.javaClass?.simpleName}")

    onEdt {
        player.close()
        frame.dispose()
    }
    log("closed")
    File(outDir, "report.txt").writeText(report.toString())
    System.exit(0)
}
