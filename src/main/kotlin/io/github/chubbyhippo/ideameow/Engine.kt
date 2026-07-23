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
        c: Char,
    ): Boolean {
        when {
            state.mode == MeowMode.KEYPAD -> {
                Keypad.key(editor, state, c)
                state.lastCommand = "keypad"
            }

            state.avy != null -> {
                Avy.key(editor, state, c)
                state.lastCommand = "avy"
            }

            state.aceWindow != null -> {
                AceWindow.key(editor, state, c)
                state.lastCommand = "ace-window"
            }

            state.aceClick != null -> {
                AceClick.key(editor, state, c)
                state.lastCommand = "ace-click"
            }

            state.aceResize != null -> {
                AceResize.key(editor, state, c)
                state.lastCommand = "ace-resize"
            }

            else -> return false
        }
        Meow.updateWidgets()
        return true
    }

    fun handleChar(
        editor: Editor,
        c: Char,
    ): Boolean {
        val state = Meow.state(editor) ?: return false
        if (routeSession(editor, state, c)) return true
        if (state.mode == MeowMode.INSERT) return false

        WhichKey.hide()
        ExpandHints.clear(state)

        val pend = state.pending
        val repeatBinding = if (pend == null) repeatMap?.get(c) else null
        if (pend == null && repeatBinding == null) repeatMap = null
        val motion = state.mode == MeowMode.MOTION
        val binding = if (pend == null) repeatBinding ?: resolve(state, c, motion) else null
        val cmd = binding?.command

        if (!state.replaying && cmd != "repeat") {
            if (pend == null && state.pendingCount == 0 && !state.negative) state.unitKeys.clear()
            state.unitKeys.add(c)
        }

        if (pend != null) {
            state.pending = null
            resolvePending(editor, state, pend, c)
            state.lastCommand = "pending"
        } else if (binding != null) {
            runBinding(editor, state, binding)
            state.lastCommand = binding.command ?: binding.action ?: state.lastCommand
        } else {
            state.lastCommand = null
        }

        val isPrefixCommand =
            state.pending != null ||
                (state.pendingCount != 0 && cmd?.startsWith("meow-expand-") == true) ||
                (state.negative && cmd == "meow-negative-argument") ||
                cmd == "meow-keypad"
        if (!state.replaying && cmd != "repeat" && !isPrefixCommand) state.lastKeys = state.unitKeys.toList()

        Meow.updateWidgets()
        return true
    }

    private fun resolve(
        state: MeowState,
        c: Char,
        motion: Boolean,
    ): Rc.Binding? {
        if (c == ' ') return KEYPAD_BINDING
        if (state.noremapDepth == 0) {
            val config = Rc.config()
            (if (motion) config.motion[c] else config.normal[c])?.let { return it }
        }
        val d = Rc.defaults()
        return if (motion) d.motion[c] else d.normal[c]
    }

    private fun resolvePending(
        editor: Editor,
        state: MeowState,
        p: Pending,
        c: Char,
    ) {
        when (p) {
            Pending.FIND -> {
                Motions.findTill(editor, state, c, till = false)
            }

            Pending.TILL -> {
                Motions.findTill(editor, state, c, till = true)
            }

            Pending.INNER, Pending.BOUNDS, Pending.BEGIN, Pending.END -> {
                Structures.thingSelect(editor, state, p, c)
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
            for (k in keys) handleChar(editor, k)
        } finally {
            state.replaying = false
        }
    }

    fun runBinding(
        editor: Editor,
        state: MeowState,
        b: Rc.Binding,
    ) {
        dispatch(editor, state, b)
        val map = Rc.repeatMapFor(b) ?: return
        if (repeatMap == null) {
            Ide.hint(editor, "Repeat with ${map.keys.joinToString(", ")}")
        }
        repeatMap = map
    }

    internal fun dispatch(
        editor: Editor,
        state: MeowState,
        b: Rc.Binding,
    ) {
        val command = b.command
        if (command != null) {
            COMMANDS[command]?.invoke(editor, state)
                ?: Ide.hint(editor, "Unknown meow command: $command")
            return
        }
        val actionId = b.action
        if (actionId != null) {
            Ide.act(editor, actionId)
            return
        }
        val keys = b.keys ?: return
        if (state.replayDepth >= Rc.MAX_MAPPING_DEPTH) {
            Ide.hint(editor, "ideameow: mapping recursion is too deep")
            return
        }
        val savedReplaying = state.replaying
        state.replaying = true
        state.replayDepth++
        if (!b.recursive) state.noremapDepth++
        try {
            for (k in keys) handleChar(editor, k)
        } finally {
            if (!b.recursive) state.noremapDepth--
            state.replayDepth--
            state.replaying = savedReplaying
        }
    }
}
