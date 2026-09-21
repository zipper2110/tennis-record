package org.litvin.ui.tabs.crop.presenter

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.projects.ManifestIO
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Crop/Rotate presenter. It keeps the geometry adjustments in the shared adjustments session
 * and tells the view which source video to show. The view owns the live video player.
 */
class DefaultCropRotatePresenter(
    private val adjustments: AdjustmentsSession = AdjustmentsStore.legacySession(),
) : CropRotatePresenter {
    private val disposed = AtomicBoolean(false)

    private var view: CropRotateView? = null
    private var unsubscribeStore: (() -> Unit)? = null
    private var projectDir: String? = null
    private var state = CropRotateViewState()

    override fun attach(view: CropRotateView) {
        this.view = view
        render()
    }

    override fun detach() {
        unsubscribeStore?.invoke()
        unsubscribeStore = null
        view = null
    }

    override fun onActivated() {
        unsubscribeStore?.invoke()
        unsubscribeStore = adjustments.subscribe { newAdjustments ->
            updateState { it.copy(adjustments = newAdjustments) }
        }
        updateState { it.copy(adjustments = adjustments.get()) }
    }

    override fun onDeactivated() {
        adjustments.save(projectDir)
    }

    override fun onIntent(intent: CropRotateIntent) {
        when (intent) {
            is CropRotateIntent.LoadProject -> loadProject(intent.manifestPath)
            is CropRotateIntent.ChangeTransform -> {
                val incoming = intent.adjustments
                val zoom = incoming.zoom.coerceIn(0.1f, 4.0f)
                val panX = incoming.panX.coerceIn(-1.0f, 1.0f)
                val panY = incoming.panY.coerceIn(-1.0f, 1.0f)
                val rotationDeg = incoming.rotationDeg.coerceIn(-180.0f, 180.0f)
                updateState {
                    it.copy(adjustments = it.adjustments.copy(zoom = zoom, panX = panX, panY = panY, rotationDeg = rotationDeg))
                }
                adjustments.set { previous ->
                    previous.copy(zoom = zoom, panX = panX, panY = panY, rotationDeg = rotationDeg)
                }
            }
            CropRotateIntent.ResetTransform -> {
                adjustments.set { previous ->
                    previous.copy(zoom = 1.0f, panX = 0.0f, panY = 0.0f, rotationDeg = 0.0f)
                }
            }
        }
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        onDeactivated()
        detach()
    }

    private fun loadProject(manifestPath: String) {
        projectDir = File(manifestPath).parentFile?.absolutePath
        projectDir?.let { adjustments.load(it) }
        val source = try {
            ManifestIO.read(manifestPath).sourceVideo?.let { File(it) }
        } catch (t: Throwable) {
            postEffect(CropRotateViewEffect.ShowError(t.message ?: "Could not read the project."))
            null
        }
        if (source != null && !source.exists()) {
            postEffect(CropRotateViewEffect.ShowError("Project source video is missing."))
        }
        updateState { it.copy(adjustments = adjustments.get(), sourceVideo = source?.takeIf { file -> file.exists() }) }
    }

    private fun updateState(reducer: (CropRotateViewState) -> CropRotateViewState) {
        state = reducer(state)
        render()
    }

    private fun render() {
        val snapshot = state
        val target = view ?: return
        if (EventQueue.isDispatchThread()) {
            target.render(snapshot)
        } else {
            EventQueue.invokeLater { view?.render(snapshot) }
        }
    }

    private fun postEffect(effect: CropRotateViewEffect) {
        if (EventQueue.isDispatchThread()) {
            view?.renderEffect(effect)
        } else {
            EventQueue.invokeLater { view?.renderEffect(effect) }
        }
    }
}
