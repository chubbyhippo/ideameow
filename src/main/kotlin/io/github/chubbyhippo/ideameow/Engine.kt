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
    operator fun invoke(
        editor: Editor,
        state: MeowState,
        ctx: DataContext?,
    )
}

/**
 * The key dispatcher. Like meow in Emacs, the engine binds no keys of its
 * own: every command is registered by its meow name in [COMMANDS], and keys
 * resolve through rc bindings only — ~/.ideameowrc over the bundled default
 * .ideameowrc (see [Rc]). Besides dispatch, this object owns the pieces of
 * behavior that need the whole-keystroke view: the repeat unit (`'`),
 * rc-binding replay with its noremap/recursion bookkeeping, and the repeat
 * transient (Emacs repeat-mode: rc `repeat` groups arm a one-shot map whose
 * member keys re-dispatch — tap `.`/`,` to keep walking errors after SPC . e).
 */
object Engine {
    /**
     * Every command under its meow name (plus Emacs' `repeat` and `ignore`,
     * exactly as meow's suggested layout spells them) — the targets a
     * ~/.ideameowrc line can bind a key to.
     */
    val COMMANDS: Map<String, MeowCommand> =
        buildMap {
            putAll(Motions.commands)
            putAll(Selections.commands)
            putAll(Search.commands)
            putAll(Structures.commands)
            putAll(Grab.commands)
            putAll(Edits.commands)
            putAll(Avy.commands) // native avy-goto-char-timer / avy-goto-line
            // dispatcher-level commands: counts, the keypad, repeat, quit, no-op
            put("meow-negative-argument", MeowCommand { _, st, _ -> st.negative = true })
            put("meow-quit", MeowCommand { ed, _, ctx -> Ide.act(ed, ctx, "CloseContent") })
            put("meow-keypad", MeowCommand { ed, st, _ -> enterKeypad(ed, st) })
            put("repeat", MeowCommand { ed, st, ctx -> repeatLast(ed, st, ctx) })
            put("ignore", MeowCommand { _, _, _ -> })
        }

    private val KEYPAD_BINDING = Rc.Binding(command = "meow-keypad")

    /** meow-keypad (meow-keypad.el): record meow--keypad-previous-state,
     *  then switch — Keypad.exit restores it, so SPC round-trips to NORMAL
     *  and the Alt+; action returns to INSERT. Shared by the rc-dispatched
     *  command and [KeypadAction]. */
    fun enterKeypad(
        editor: Editor,
        st: MeowState,
    ) {
        st.keypadPreviousState = st.mode
        Meow.setMode(editor, st, MeowMode.KEYPAD)
        WhichKey.scheduleKeypad(editor, "")
    }

    /** @return true when the key was consumed (typed handler skips insertion). */
    fun handleChar(
        editor: Editor,
        c: Char,
        ctx: DataContext?,
    ): Boolean {
        val st = Meow.state(editor) ?: return false
        if (st.mode == MeowMode.INSERT) return false
        if (st.mode == MeowMode.KEYPAD) {
            Keypad.key(editor, st, c, ctx)
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

        WhichKey.hide()
        ExpandHints.clear(st)

        val pend = st.pending
        // the repeat transient: a member key of the armed group re-dispatches
        // its binding, shadowing the normal map for exactly that keypress
        // (runBinding re-arms it); any other key ends the run and falls
        // through to the resolve below — Emacs set-transient-map semantics,
        // never swallowed. ESC ends the run too (MeowEscapeHandler).
        val repeatBinding = if (pend == null) st.repeatMap?.get(c) else null
        if (pend == null && repeatBinding == null) st.repeatMap = null
        // like Emacs: read-only buffers stay in NORMAL with every motion
        // working (the modify commands gate themselves via allow-modify);
        // the motion map applies only to the MOTION state proper
        val motionish = st.mode == MeowMode.MOTION
        val binding = if (pend == null) repeatBinding ?: resolve(st, c, motionish) else null
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

        val prefixy =
            st.pending != null ||
                (st.pendingCount != 0 && cmd?.startsWith("meow-expand-") == true) ||
                (st.negative && cmd == "meow-negative-argument") ||
                cmd == "meow-keypad"
        if (!st.replaying && cmd != "repeat" && !prefixy) st.lastKeys = st.unit.toList()

        Meow.updateWidgets()
        return true
    }

    /** SPC = keypad (reserved), then ~/.ideameowrc maps (skipped inside a
     *  noremap replay), then the bundled default rc; null = undefined key. */
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

    /** Commands that read one more key: find/till chars and the thing table. */
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
        ctx: DataContext?,
    ) {
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
     *  replaying). Afterwards, Emacs repeat-mode's post-command arming: a
     *  binding whose target sits in an rc repeat group arms that group's
     *  transient — membership by target identity (the repeat-map symbol
     *  property), no entered-with-key check (repeat-check-key 'no is the
     *  matching Emacs setting, and keypad keys are never members). */
    fun runBinding(
        editor: Editor,
        st: MeowState,
        b: Rc.Binding,
        ctx: DataContext?,
    ) {
        dispatch(editor, st, b, ctx)
        val map = Rc.repeatMapFor(b) ?: return
        if (st.repeatMap == null) {
            // repeat-echo-message, once per run: "Repeat with ., ,"
            Ide.hint(editor, "Repeat with ${map.keys.joinToString(", ")}")
        }
        st.repeatMap = map
    }

    private fun dispatch(
        editor: Editor,
        st: MeowState,
        b: Rc.Binding,
        ctx: DataContext?,
    ) {
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
