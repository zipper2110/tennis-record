package org.litvin.ui.flow.fakes

import org.litvin.app.PreferencesProvider
import java.util.prefs.AbstractPreferences
import java.util.prefs.Preferences

class InMemoryPreferencesProvider : PreferencesProvider {
    private val root = MemoryPreferences(null, "")

    override fun node(key: String): Preferences {
        require(key.isNotBlank()) { "Preferences key must not be blank" }
        return root.node(key)
    }

    private class MemoryPreferences(
        parent: AbstractPreferences?,
        name: String,
    ) : AbstractPreferences(parent, name) {
        private val values = linkedMapOf<String, String>()
        private val children = linkedMapOf<String, MemoryPreferences>()

        @Synchronized override fun putSpi(key: String, value: String) { values[key] = value }
        @Synchronized override fun getSpi(key: String): String? = values[key]
        @Synchronized override fun removeSpi(key: String) { values.remove(key) }
        @Synchronized override fun removeNodeSpi() { values.clear(); children.clear() }
        @Synchronized override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        @Synchronized override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()
        @Synchronized override fun childSpi(name: String): AbstractPreferences =
            children.getOrPut(name) { MemoryPreferences(this, name) }
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }
}
