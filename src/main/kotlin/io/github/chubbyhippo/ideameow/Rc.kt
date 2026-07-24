// Copyright (C) 2026 Chubby Hippo
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation, either version 3 of the License, or (at your option)
// any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
// more details.
//
// You should have received a copy of the GNU General Public License along
// with this program. If not, see <https://www.gnu.org/licenses/>.
//
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.chubbyhippo.ideameow

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.awt.event.InputEvent
import java.io.File
import javax.swing.KeyStroke

data class ChordKey(
    val keyCode: Int,
    val modifiers: Int,
) {
    fun hasNonShiftModifier(): Boolean = modifiers and NON_SHIFT != 0

    companion object {
        private val ALL =
            InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or
                InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK
        private val NON_SHIFT =
            InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or
                InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK

        fun of(
            keyCode: Int,
            modifiers: Int,
        ): ChordKey = ChordKey(keyCode, modifiers and ALL)

        fun fromKeyStroke(keyStroke: KeyStroke): ChordKey = of(keyStroke.keyCode, keyStroke.modifiers)
    }
}

object Rc {
    const val FILE_NAME = ".ideameowrc"
    const val MAX_MAPPING_DEPTH = 8
    private const val DEFAULT_WHICH_KEY_DELAY_MS = 250

    data class Binding(
        val action: String? = null,
        val keys: String? = null,
        val command: String? = null,
        val recursive: Boolean = true,
    )

    class Config {
        val normal = mutableMapOf<Char, Binding>()
        val motion = mutableMapOf<Char, Binding>()
        val keypad = linkedMapOf<String, Binding>()
        val keypadDesc = mutableMapOf<String, String>()
        val chords = linkedMapOf<ChordKey, Binding>()

        val repeat = linkedMapOf<String, LinkedHashMap<Char, Binding>>()
        var whichKey: Boolean? = null
        var whichKeyDelayMs: Int? = null
        val errors = mutableListOf<String>()
    }

    @Volatile
    private var loaded = false

    @Volatile
    var config: Config = Config()
        private set

    @Volatile
    private var defaultsLoaded = false

    @Volatile
    private var defaultConfig: Config = Config()

    fun config(): Config {
        if (!loaded) load()
        return config
    }

    fun defaults(): Config {
        if (!defaultsLoaded) loadDefaults()
        return defaultConfig
    }

    fun defaultsText(): String? = javaClass.getResourceAsStream("/$FILE_NAME")?.bufferedReader()?.use { it.readText() }

    fun setForTest(newConfig: Config) {
        config = newConfig
        loaded = true
        RcFileState.resetForTest()
    }

    fun rcFile(): File = File(System.getProperty("user.home"), FILE_NAME)

    fun parse(lines: List<String>): Config = RcParser.parse(lines)

    fun load() {
        loaded = true
        val file = rcFile()
        config = if (file.isFile) parse(file.readLines()) else Config()
        RcFileState.saveParsed(config)
        if (config.errors.isNotEmpty()) {
            notify(
                "ideameow: problem(s) in ~/$FILE_NAME\n" + config.errors.joinToString("\n"),
                NotificationType.WARNING,
            )
        }
    }

    private fun loadDefaults() {
        defaultsLoaded = true
        val lines = javaClass.getResourceAsStream("/$FILE_NAME")?.bufferedReader()?.readLines()
        defaultConfig = if (lines != null) parse(lines) else Config()
        if (lines == null || defaultConfig.errors.isNotEmpty()) {
            notify(
                "ideameow: broken bundled $FILE_NAME (plugin bug)\n" +
                    defaultConfig.errors.joinToString("\n"),
                NotificationType.ERROR,
            )
        }
    }

    fun keypad(): Map<String, Binding> = LinkedHashMap(defaults().keypad).apply { putAll(config().keypad) }

    fun keypadDescs(): Map<String, String> = HashMap(defaults().keypadDesc).apply { putAll(config().keypadDesc) }

    fun chords(): Map<ChordKey, Binding> {
        val merged = LinkedHashMap(defaults().chords)
        merged.putAll(config().chords)
        merged.values.removeIf { it.command == "ignore" }
        return merged
    }

    fun repeatGroups(): Map<String, Map<Char, Binding>> {
        val merged = LinkedHashMap<String, LinkedHashMap<Char, Binding>>()
        for ((group, members) in defaults().repeat) merged.getOrPut(group) { LinkedHashMap() }.putAll(members)
        for ((group, members) in config().repeat) merged.getOrPut(group) { LinkedHashMap() }.putAll(members)
        for (members in merged.values) members.values.removeIf { it.command == "ignore" }
        merged.values.removeIf { it.isEmpty() }
        return merged
    }

    fun repeatMapFor(binding: Binding): Map<Char, Binding>? =
        repeatGroups().values.firstOrNull { members ->
            members.values.any {
                it.action == binding.action && it.command == binding.command && it.keys == binding.keys
            }
        }

    fun whichKeyEnabled(): Boolean = config().whichKey ?: defaults().whichKey ?: true

    fun whichKeyDelayMs(): Int = config().whichKeyDelayMs ?: defaults().whichKeyDelayMs ?: DEFAULT_WHICH_KEY_DELAY_MS

    fun notify(
        text: String,
        type: NotificationType,
    ) {
        runCatching {
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("ideameow")
                .createNotification(text, type)
                .notify(null)
        }
    }
}
