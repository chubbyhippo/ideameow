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

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor

/**
 * A named, bindable meow command — the unit every rc binding resolves to.
 * Each command family (Motions, Selections, Search, Structures, Grab, Edits)
 * contributes its commands to [Engine.COMMANDS] under their meow names.
 */
fun interface MeowCommand {
    operator fun invoke(editor: Editor, state: MeowState, ctx: DataContext?)
}

/**
 * The key dispatcher. Like meow in Emacs, the engine binds no keys of its
 * own: every command is registered by its meow name in [COMMANDS], and keys
 * resolve through rc bindings only — ~/.ideameowrc over the bundled default
 * .ideameowrc (see [Rc]). Besides dispatch, this object owns the two pieces
 * of behavior that need the whole-keystroke view: the repeat unit (`'`)
 * and rc-binding replay with its noremap/recursion bookkeeping.
 */
object Engine {

    /**
     * Every command under its meow name (plus Emacs' `repeat` and `ignore`,
     * exactly as meow's suggested layout spells them) — the targets a
     * ~/.ideameowrc line can bind a key to.
     */
    val COMMANDS: Map<String, MeowCommand> = buildMap {
        putAll(Motions.commands)
        putAll(Selections.commands)
        putAll(Search.commands)
        putAll(Structures.commands)
        putAll(Grab.commands)
        putAll(Edits.commands)
        // dispatcher-level commands: counts, the keypad, repeat, quit, no-op
        put("meow-negative-argument", MeowCommand { _, st, _ -> st.negative = true })
        put("meow-quit", MeowCommand { ed, _, ctx -> Ide.act(ed, ctx, "CloseContent") })
        put("meow-keypad", MeowCommand { ed, st, _ ->
            Meow.setMode(ed, st, MeowMode.KEYPAD)
            WhichKey.scheduleKeypad(ed, "")
        })
        put("repeat", MeowCommand { ed, st, ctx -> repeatLast(ed, st, ctx) })
        put("ignore", MeowCommand { _, _, _ -> })
    }

    private val KEYPAD_BINDING = Rc.Binding(command = "meow-keypad")

    /** @return true when the key was consumed (typed handler skips insertion). */
    fun handleChar(editor: Editor, c: Char, ctx: DataContext?): Boolean {
        val st = Meow.state(editor) ?: return false
        if (st.mode == MeowMode.INSERT) return false
        if (st.mode == MeowMode.KEYPAD) {
            Keypad.key(editor, st, c, ctx)
            st.lastCommand = "keypad"
            Meow.updateWidgets()
            return true
        }

        WhichKey.hide()
        ExpandHints.clear(st)

        val pend = st.pending
        val motionish = st.mode == MeowMode.MOTION || editor.isViewer || !editor.document.isWritable
        val binding = if (pend == null) resolve(st, c, motionish) else null
        val cmd = binding?.command

        // the repeat unit: everything since the last complete command, so `'`
        // can replay counts and pending args (2fa) as one stroke
        if (!st.replaying && cmd != "repeat") {
            if (pend == null && st.pendingCount == 0 && !st.negative) st.unit.clear()
            st.unit.add(c)
        }

        if (pend != null) {
            st.pending = null
            resolvePending(editor, st, pend, c)
            st.lastCommand = "pending"
        } else if (binding != null) {
            runBinding(editor, st, binding, ctx)
            // the this-command/last-command handoff: vertical-motion chains
            // keep their goal column only while uninterrupted (see Motions);
            // a keys-replay binding keeps the innermost replayed command
            st.lastCommand = binding.command ?: binding.action ?: st.lastCommand
        } else {
            st.lastCommand = null
        } // undefined key: swallow, never self-insert

        val prefixy = st.pending != null ||
                (st.pendingCount != 0 && cmd?.startsWith("meow-expand-") == true) ||
                (st.negative && cmd == "meow-negative-argument") ||
                cmd == "meow-keypad"
        if (!st.replaying && cmd != "repeat" && !prefixy) st.lastKeys = st.unit.toList()

        Meow.updateWidgets()
        return true
    }

    /** SPC = keypad (reserved), then ~/.ideameowrc maps (skipped inside a
     *  noremap replay), then the bundled default rc; null = undefined key. */
    private fun resolve(st: MeowState, c: Char, motion: Boolean): Rc.Binding? {
        if (c == ' ') return KEYPAD_BINDING
        if (st.noremapDepth == 0) {
            val cfg = Rc.cfg()
            (if (motion) cfg.motion[c] else cfg.normal[c])?.let { return it }
        }
        val d = Rc.defaults()
        return if (motion) d.motion[c] else d.normal[c]
    }

    /** Commands that read one more key: find/till chars and the thing table. */
    private fun resolvePending(editor: Editor, st: MeowState, p: Pending, c: Char) {
        when (p) {
            Pending.FIND -> Motions.findTill(editor, st, c, till = false)
            Pending.TILL -> Motions.findTill(editor, st, c, till = true)
            Pending.INNER, Pending.BOUNDS, Pending.BEGIN, Pending.END ->
                Structures.thingSelect(editor, st, p, c)
        }
    }

    private fun repeatLast(editor: Editor, st: MeowState, ctx: DataContext?) {
        val keys = st.lastKeys
        if (keys.isEmpty()) return
        st.replaying = true
        try {
            for (k in keys) handleChar(editor, k, ctx)
        } finally {
            st.replaying = false
        }
    }

    /** Run a binding: a named meow command, an IDE action, or meow keys
     *  replayed through the engine (noremap bindings skip user maps while
     *  replaying). */
    fun runBinding(editor: Editor, st: MeowState, b: Rc.Binding, ctx: DataContext?) {
        val command = b.command
        if (command != null) {
            COMMANDS[command]?.invoke(editor, st, ctx)
                ?: Ide.hint(editor, "Unknown meow command: $command")
            return
        }
        val actionId = b.action
        if (actionId != null) {
            Ide.act(editor, ctx, actionId)
            return
        }
        val keys = b.keys ?: return
        if (st.replayDepth >= 8) {
            Ide.hint(editor, "ideameow: mapping recursion is too deep")
            return
        }
        val savedReplaying = st.replaying
        st.replaying = true // inner keys must not clobber the ' (repeat) unit
        st.replayDepth++
        if (!b.recursive) st.noremapDepth++
        try {
            for (k in keys) handleChar(editor, k, ctx)
        } finally {
            if (!b.recursive) st.noremapDepth--
            st.replayDepth--
            st.replaying = savedReplaying
        }
    }
}
