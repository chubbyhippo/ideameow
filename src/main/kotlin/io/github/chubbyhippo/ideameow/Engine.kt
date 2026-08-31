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
            putAll(View.commands)
            put("meow-negative-argument", MeowCommand { _, state -> state.negative = true })
            put("negative-argument", MeowCommand { _, state -> state.negative = true })
            put("meow-quit", MeowCommand { editor, _ -> Ide.act(editor, "CloseContent") })
            put("meow-keypad", MeowCommand { editor, state -> enterKeypad(editor, state) })
            put("repeat", MeowCommand { editor, state -> DotRepeat.repeatLast(editor, state) })
            put("ignore", MeowCommand { _, _ -> })
        }

    private val KEYPAD_BINDING = Rc.Binding(command = "meow-keypad")

    var repeatMap: Map<Char, Rc.Binding>?
        get() = RepeatRun.map
        set(value) {
            RepeatRun.map = value
        }

    fun enterKeypad(
        editor: Editor,
        state: MeowState,
    ) {
        state.keypadPreviousMode = state.mode
        Meow.setMode(editor, state, MeowMode.KEYPAD)
        WhichKey.scheduleKeypad(editor, "")
    }

    fun handleChar(
        editor: Editor,
        key: Char,
    ): Boolean =
        run {
            val state = Meow.state(editor) ?: return@run false
            if (OverlaySessions.routeKey(editor, state, key)) return@run true
            if (state.mode == MeowMode.INSERT) return@run false

            WhichKey.hide()
            ExpandHints.clear(state)

            val pending = state.pending
            val binding = resolveBinding(state, key, pending)
            val command = binding?.command

            DotRepeat.recordUnitKey(state, key, command, pending)
            executeStep(editor, state, pending, binding, key)
            DotRepeat.recordLastKeys(state, command)

            Meow.updateWidgets()
            true
        }

    private fun resolveBinding(
        state: MeowState,
        key: Char,
        pending: Pending?,
    ): Rc.Binding? {
        if (pending != null) return null
        val repeatBinding = RepeatRun.consume(key)
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
    ): Rc.Binding? =
        run {
            if (key == ' ') return@run KEYPAD_BINDING
            if (state.noremapDepth == 0) {
                val config = Rc.config()
                (if (motion) config.motion[key] else config.normal[key])?.let { return@run it }
            }
            val defaults = Rc.defaults()
            if (motion) defaults.motion[key] else defaults.normal[key]
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

    fun runBinding(
        editor: Editor,
        state: MeowState,
        binding: Rc.Binding,
    ) {
        dispatch(editor, state, binding)
        RepeatRun.armAfter(editor, binding)
    }

    internal fun dispatch(
        editor: Editor,
        state: MeowState,
        binding: Rc.Binding,
    ) {
        run {
            val command = binding.command
            if (command != null) {
                COMMANDS[command]?.invoke(editor, state)
                    ?: Ide.hint(editor, "Unknown meow command: $command")
                return@run
            }
            val actionId = binding.action
            if (actionId != null) {
                Ide.act(editor, actionId)
                return@run
            }
            val keys = binding.keys ?: return@run
            if (state.replayDepth >= Rc.MAX_MAPPING_DEPTH) {
                Ide.hint(editor, "ideameow: mapping recursion is too deep")
                return@run
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
}
