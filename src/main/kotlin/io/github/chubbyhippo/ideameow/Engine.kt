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
            put("meow-negative-argument", MeowCommand { _, st -> st.negative = true })
            put("negative-argument", MeowCommand { _, st -> st.negative = true })
            put("meow-quit", MeowCommand { ed, _ -> Ide.act(ed, "CloseContent") })
            put("meow-keypad", MeowCommand { ed, st -> enterKeypad(ed, st) })
            put("repeat", MeowCommand { ed, st -> repeatLast(ed, st) })
            put("ignore", MeowCommand { _, _ -> })
        }

    private val KEYPAD_BINDING = Rc.Binding(command = "meow-keypad")

    var repeatMap: Map<Char, Rc.Binding>? = null

    fun enterKeypad(
        editor: Editor,
        st: MeowState,
    ) {
        st.keypadPreviousMode = st.mode
        Meow.setMode(editor, st, MeowMode.KEYPAD)
        WhichKey.scheduleKeypad(editor, "")
    }

    fun handleChar(
        editor: Editor,
        c: Char,
    ): Boolean {
        val st = Meow.state(editor) ?: return false
        if (st.mode == MeowMode.INSERT) return false
        if (st.mode == MeowMode.KEYPAD) {
            Keypad.key(editor, st, c)
            st.lastCommand = "keypad"
            Meow.updateWidgets()
            return true
        }
        if (st.avy != null) {
            Avy.key(editor, st, c)
            st.lastCommand = "avy"
            Meow.updateWidgets()
            return true
        }
        if (st.aceWindow != null) {
            AceWindow.key(editor, st, c)
            st.lastCommand = "ace-window"
            Meow.updateWidgets()
            return true
        }

        WhichKey.hide()
        ExpandHints.clear(st)

        val pend = st.pending
        val repeatBinding = if (pend == null) repeatMap?.get(c) else null
        if (pend == null && repeatBinding == null) repeatMap = null
        val motion = st.mode == MeowMode.MOTION
        val binding = if (pend == null) repeatBinding ?: resolve(st, c, motion) else null
        val cmd = binding?.command

        if (!st.replaying && cmd != "repeat") {
            if (pend == null && st.pendingCount == 0 && !st.negative) st.unitKeys.clear()
            st.unitKeys.add(c)
        }

        if (pend != null) {
            st.pending = null
            resolvePending(editor, st, pend, c)
            st.lastCommand = "pending"
        } else if (binding != null) {
            runBinding(editor, st, binding)
            st.lastCommand = binding.command ?: binding.action ?: st.lastCommand
        } else {
            st.lastCommand = null
        }

        val isPrefixCommand =
            st.pending != null ||
                (st.pendingCount != 0 && cmd?.startsWith("meow-expand-") == true) ||
                (st.negative && cmd == "meow-negative-argument") ||
                cmd == "meow-keypad"
        if (!st.replaying && cmd != "repeat" && !isPrefixCommand) st.lastKeys = st.unitKeys.toList()

        Meow.updateWidgets()
        return true
    }

    private fun resolve(
        st: MeowState,
        c: Char,
        motion: Boolean,
    ): Rc.Binding? {
        if (c == ' ') return KEYPAD_BINDING
        if (st.noremapDepth == 0) {
            val cfg = Rc.cfg()
            (if (motion) cfg.motion[c] else cfg.normal[c])?.let { return it }
        }
        val d = Rc.defaults()
        return if (motion) d.motion[c] else d.normal[c]
    }

    private fun resolvePending(
        editor: Editor,
        st: MeowState,
        p: Pending,
        c: Char,
    ) {
        when (p) {
            Pending.FIND -> {
                Motions.findTill(editor, st, c, till = false)
            }

            Pending.TILL -> {
                Motions.findTill(editor, st, c, till = true)
            }

            Pending.INNER, Pending.BOUNDS, Pending.BEGIN, Pending.END -> {
                Structures.thingSelect(editor, st, p, c)
            }
        }
    }

    private fun repeatLast(
        editor: Editor,
        st: MeowState,
    ) {
        val keys = st.lastKeys
        if (keys.isEmpty()) return
        st.replaying = true
        try {
            for (k in keys) handleChar(editor, k)
        } finally {
            st.replaying = false
        }
    }

    fun runBinding(
        editor: Editor,
        st: MeowState,
        b: Rc.Binding,
    ) {
        dispatch(editor, st, b)
        val map = Rc.repeatMapFor(b) ?: return
        if (repeatMap == null) {
            Ide.hint(editor, "Repeat with ${map.keys.joinToString(", ")}")
        }
        repeatMap = map
    }

    private fun dispatch(
        editor: Editor,
        st: MeowState,
        b: Rc.Binding,
    ) {
        val command = b.command
        if (command != null) {
            COMMANDS[command]?.invoke(editor, st)
                ?: Ide.hint(editor, "Unknown meow command: $command")
            return
        }
        val actionId = b.action
        if (actionId != null) {
            Ide.act(editor, actionId)
            return
        }
        val keys = b.keys ?: return
        if (st.replayDepth >= Rc.MAX_MAPPING_DEPTH) {
            Ide.hint(editor, "ideameow: mapping recursion is too deep")
            return
        }
        val savedReplaying = st.replaying
        st.replaying = true
        st.replayDepth++
        if (!b.recursive) st.noremapDepth++
        try {
            for (k in keys) handleChar(editor, k)
        } finally {
            if (!b.recursive) st.noremapDepth--
            st.replayDepth--
            st.replaying = savedReplaying
        }
    }
}
