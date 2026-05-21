package org.litvin.ui.commons

import javax.swing.JComponent

/**
 * Minimal UI safety helpers to keep method bodies clean from try/catch noise.
 * - No logging, no labels.
 * - Swallows only Exceptions (not Errors).
 */
inline fun uiSafe(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        // swallow
    }
}

fun <T> uiSafe(aThis: T, block: T.() -> Unit) {
    try {
        block(aThis)
    } catch (e: Exception) {
        // swallow
    }
}

inline fun <T> uiSafe(default: T, block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        default
    }

inline fun <T> uiSafeOrNull(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        null
    }

fun uiSafe(block: () -> Unit, onException: (Exception) -> Unit) {}

fun JComponent.uiSafe(block: () -> Unit) {
    uiSafe(this, block)
}
