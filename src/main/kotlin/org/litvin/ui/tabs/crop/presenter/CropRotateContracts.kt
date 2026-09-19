package org.litvin.ui.tabs.crop.presenter

import org.litvin.adjustments.AdjustmentsV1
import java.io.File

interface CropRotateView {
    fun render(state: CropRotateViewState)
    fun renderEffect(effect: CropRotateViewEffect) {}
}

interface CropRotatePresenter {
    fun attach(view: CropRotateView)
    fun detach()
    fun onActivated()
    fun onDeactivated()
    fun onIntent(intent: CropRotateIntent)
}

data class CropRotateViewState(
    val adjustments: AdjustmentsV1 = AdjustmentsV1(),
    val sourceVideo: File? = null,
)

sealed class CropRotateIntent {
    data class LoadProject(val manifestPath: String) : CropRotateIntent()
    data class ChangeTransform(val adjustments: AdjustmentsV1) : CropRotateIntent()
    data object ResetTransform : CropRotateIntent()
    data object ResetAll : CropRotateIntent()
}

sealed class CropRotateViewEffect {
    data class ShowError(val message: String) : CropRotateViewEffect()
}
