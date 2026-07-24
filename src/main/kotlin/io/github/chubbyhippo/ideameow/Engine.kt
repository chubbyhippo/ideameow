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

import com.intellij.openapi.editor.Editor

fun interface MeowCommand {
    operator fun invoke(
        editor: Editor,
        state: MeowState,
    )
}

object Engine {
    val COMMANDS: Map<String, MeowCommand> =
        buildMap {
            putAll(Motions.commands)
            putAll(Selections.commands)
            putAll(Search.commands)
            putAll(Structures.commands)
            putAll(Grab.commands)
            putAll(Edits.commands)
            putAll(Avy.commands)
            putAll(AceWindow.commands)
            putAll(AceClick.commands)
            putAll(AceResize.commands)
            put("meow-negative-argument", MeowCommand { _, state -> state.negative = true })
            put("negative-argument", MeowCommand { _, state -> state.negative = true })
            put("meow-quit", MeowCommand { editor, _ -> Ide.act(editor, "CloseContent") })
            put("meow-keypad", MeowCommand { editor, state -> enterKeypad(editor, state) })
            put("repeat", MeowCommand { editor, state -> repeatLast(editor, state) })
            put("ignore", MeowCommand { _, _ -> })
        }

    private val KEYPAD_BINDING = Rc.Binding(command = "meow-keypad")

    var repeatMap: Map<Char, Rc.Binding>? = null

    fun enterKeypad(
        editor: Editor,
        state: MeowState,
    ) {
        state.keypadPreviousMode = state.mode
        Meow.setMode(editor, state, MeowMode.KEYPAD)
        WhichKey.scheduleKeypad(editor, "")
    }

    private fun routeSession(
        editor: Editor,
        state: MeowState,
        key: Char,
    ): Boolean {
        when {
            state.mode == MeowMode.KEYPAD -> {
                Keypad.key(editor, state, key)
                state.lastCommand = "keypad"
            }

            state.avy != null -> {
                Avy.key(editor, state, key)
                state.lastCommand = "avy"
            }

            state.aceWindow != null -> {
                AceWindow.key(editor, state, key)
                state.lastCommand = "ace-window"
            }

            state.aceClick != null -> {
                AceClick.key(editor, state, key)
                state.lastCommand = "ace-click"
            }

            state.aceResize != null -> {
                AceResize.key(editor, state, key)
                state.lastCommand = "ace-resize"
            }

            else -> return false
        }
        Meow.updateWidgets()
        return true
    }

    fun handleChar(
        editor: Editor,
        key: Char,
    ): Boolean {
        val state = Meow.state(editor) ?: return false
        if (routeSession(editor, state, key)) return true
        if (state.mode == MeowMode.INSERT) return false

        WhichKey.hide()
        ExpandHints.clear(state)

        val pending = state.pending
        val binding = resolveBinding(state, key, pending)
        val command = binding?.command

        recordUnitKey(state, key, command, pending)
        executeStep(editor, state, pending, binding, key)
        recordLastKeys(state, command)

        Meow.updateWidgets()
        return true
    }

    private fun resolveBinding(
        state: MeowState,
        key: Char,
        pending: Pending?,
    ): Rc.Binding? {
        if (pending != null) return null
        val repeatBinding = repeatMap?.get(key)
        if (repeatBinding == null) repeatMap = null
        val motion = state.mode == MeowMode.MOTION
        return repeatBinding ?: resolve(state, key, motion)
    }

    private fun executeStep(
        editor: Editor,
        state: MeowState,
        pending: Pending?,
        binding: Rc.Binding?,
        key: Char,
    ) {
        when {
            pending != null -> {
                state.pending = null
                resolvePending(editor, state, pending, key)
                state.lastCommand = "pending"
            }

            binding != null -> {
                runBinding(editor, state, binding)
                state.lastCommand = binding.command ?: binding.action ?: state.lastCommand
            }

            else -> {
                state.lastCommand = null
            }
        }
    }

    private fun resolve(
        state: MeowState,
        key: Char,
        motion: Boolean,
    ): Rc.Binding? {
        if (key == ' ') return KEYPAD_BINDING
        if (state.noremapDepth == 0) {
            val config = Rc.config()
            (if (motion) config.motion[key] else config.normal[key])?.let { return it }
        }
        val defaults = Rc.defaults()
        return if (motion) defaults.motion[key] else defaults.normal[key]
    }

    private fun resolvePending(
        editor: Editor,
        state: MeowState,
        pending: Pending,
        key: Char,
    ) {
        when (pending) {
            Pending.FIND -> {
                Motions.findTill(editor, state, key, till = false)
            }

            Pending.TILL -> {
                Motions.findTill(editor, state, key, till = true)
            }

            Pending.INNER, Pending.BOUNDS, Pending.BEGIN, Pending.END -> {
                Structures.thingSelect(editor, state, pending, key)
            }
        }
    }

    private fun repeatLast(
        editor: Editor,
        state: MeowState,
    ) {
        val keys = state.lastKeys
        if (keys.isEmpty()) return
        state.replaying = true
        try {
            for (key in keys) handleChar(editor, key)
        } finally {
            state.replaying = false
        }
    }

    fun runBinding(
        editor: Editor,
        state: MeowState,
        binding: Rc.Binding,
    ) {
        dispatch(editor, state, binding)
        val repeatKeymap = Rc.repeatMapFor(binding) ?: return
        if (repeatMap == null) {
            Ide.hint(editor, "Repeat with ${repeatKeymap.keys.joinToString(", ")}")
        }
        repeatMap = repeatKeymap
    }

    internal fun dispatch(
        editor: Editor,
        state: MeowState,
        binding: Rc.Binding,
    ) {
        val command = binding.command
        if (command != null) {
            COMMANDS[command]?.invoke(editor, state)
                ?: Ide.hint(editor, "Unknown meow command: $command")
            return
        }
        val actionId = binding.action
        if (actionId != null) {
            Ide.act(editor, actionId)
            return
        }
        val keys = binding.keys ?: return
        if (state.replayDepth >= Rc.MAX_MAPPING_DEPTH) {
            Ide.hint(editor, "ideameow: mapping recursion is too deep")
            return
        }
        val savedReplaying = state.replaying
        state.replaying = true
        state.replayDepth++
        if (!binding.recursive) state.noremapDepth++
        try {
            for (key in keys) handleChar(editor, key)
        } finally {
            if (!binding.recursive) state.noremapDepth--
            state.replayDepth--
            state.replaying = savedReplaying
        }
    }
}

private fun recordUnitKey(
    state: MeowState,
    key: Char,
    command: String?,
    pending: Pending?,
) {
    if (state.replaying || command == "repeat") return
    if (pending == null && state.pendingCount == 0 && !state.negative) state.unitKeys.clear()
    state.unitKeys.add(key)
}

private fun recordLastKeys(
    state: MeowState,
    command: String?,
) {
    if (!state.replaying && command != "repeat" && !isPrefixCommand(state, command)) {
        state.lastKeys = state.unitKeys.toList()
    }
}

private fun isPrefixCommand(
    state: MeowState,
    command: String?,
): Boolean =
    state.pending != null ||
        (state.pendingCount != 0 && command?.startsWith("meow-expand-") == true) ||
        (state.negative && command == "meow-negative-argument") ||
        command == "meow-keypad"
