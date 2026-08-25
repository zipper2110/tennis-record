package org.litvin.ui.flow.harness

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.litvin.media.MediaScreen
import org.litvin.media.PlayerStatus
import org.litvin.ui.flow.driver.SwingUiDriver
import org.litvin.ui.flow.fakes.FakeMediaPlayer
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Optional
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwingUiFlowExtensionTest {
    @Test
    fun fakeMediaCallbacksPreserveEveryQueuedStatusTransitionOnTheEdt() {
        val player = FakeMediaPlayer(MediaScreen.MARKUP)
        val transitions = mutableListOf<Pair<PlayerStatus, Boolean>>()
        player.onStatusChanged = { transitions += it to SwingUtilities.isEventDispatchThread() }

        player.play()
        player.pause()
        SwingUtilities.invokeAndWait { }

        assertTrue(
            transitions == listOf(PlayerStatus.PLAYING to true, PlayerStatus.PAUSED to true),
            "Each command must retain its own state when its EDT callback is queued: $transitions",
        )
    }

    @Test
    fun successfulTestDeletesItsTemporaryWorkspace() {
        val workspaceParent = createTempDirectory("ui-flow-success-")
        val artifacts = createTempDirectory("ui-flow-success-artifacts-")
        val extension = SwingUiFlowExtension(
            workspaceParent = workspaceParent,
            artifactsRoot = artifacts,
            driverFactory = { NoOpDriver },
        )
        val junit = extensionContext("successfulTestDeletesItsTemporaryWorkspace")

        extension.beforeEach(junit)
        val workspace = extension.activeContext().workspace
        assertTrue(workspace.exists())

        extension.afterTestExecution(junit)
        extension.afterEach(junit)

        assertFalse(workspace.exists(), "A successful flow must remove its isolated workspace")
        assertFalse(artifacts.resolve(javaClass.simpleName).exists())
    }

    @Test
    fun failedTestRetainsWorkspaceUnderActualClassAndMethodNames() {
        val workspaceParent = createTempDirectory("ui-flow-failure-")
        val artifacts = createTempDirectory("ui-flow-failure-artifacts-")
        val extension = SwingUiFlowExtension(
            workspaceParent = workspaceParent,
            artifactsRoot = artifacts,
            driverFactory = { NoOpDriver },
        )
        val methodName = "failedTestRetainsWorkspaceUnderActualClassAndMethodNames"
        val junit = extensionContext(methodName, AssertionError("deliberate failure"))

        extension.beforeEach(junit)
        val workspace = extension.activeContext().workspace
        workspace.resolve("retained-marker.txt").writeText("kept")

        extension.afterTestExecution(junit)
        extension.afterEach(junit)

        val retained = artifacts
            .resolve(javaClass.simpleName)
            .resolve(methodName)
            .resolve("workspace")
            .resolve("retained-marker.txt")
        assertTrue(retained.exists(), "Failed data must be retained below the actual JUnit identity")
        assertTrue(retained.readText() == "kept")
        assertFalse(workspace.exists(), "The temporary source is removed after it is copied")
    }

    private fun extensionContext(methodName: String, failure: Throwable? = null): ExtensionContext {
        val method = javaClass.getDeclaredMethod(methodName)
        return Proxy.newProxyInstance(
            ExtensionContext::class.java.classLoader,
            arrayOf(ExtensionContext::class.java),
        ) { _, called, _ ->
            when (called.name) {
                "getRequiredTestClass", "getTestClass" -> if (called.name.startsWith("getRequired")) javaClass else Optional.of(javaClass)
                "getRequiredTestMethod", "getTestMethod" -> if (called.name.startsWith("getRequired")) method else Optional.of(method)
                "getExecutionException" -> Optional.ofNullable(failure)
                "getDisplayName" -> methodName
                "getUniqueId" -> "[engine:junit-jupiter]/[class:${javaClass.name}]/[method:$methodName]"
                "toString" -> "ExtensionContext(${javaClass.name}#$methodName)"
                else -> defaultValue(called)
            }
        } as ExtensionContext
    }

    private fun defaultValue(method: Method): Any? = when (method.returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        Optional::class.java -> Optional.empty<Any>()
        else -> null
    }

    private object NoOpDriver : SwingUiDriver {
        override fun click(name: String) = Unit
        override fun setText(name: String, value: String) = Unit
        override fun setSlider(name: String, value: Int) = Unit
        override fun select(name: String, value: String) = Unit
        override fun press(keyStroke: KeyStroke) = Unit
        override fun requireShowing(name: String, showing: Boolean) = Unit
        override fun requireEnabled(name: String, enabled: Boolean) = Unit
        override fun requireText(name: String, expected: String) = Unit
        override fun dismissDialog(title: String, buttonText: String) = Unit
        override fun waitUntil(description: String, timeout: Duration, condition: () -> Boolean) = Unit
        override fun close() = Unit
    }
}
