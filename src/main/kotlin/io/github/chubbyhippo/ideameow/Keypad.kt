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

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

object Keypad {
    fun key(
        editor: Editor,
        st: MeowState,
        c: Char,
        ctx: DataContext?,
    ) {
        WhichKey.hide()
        val keypad = Rc.keypad()
        val buf = st.keypad.toString()

        if (buf == "/") {
            describe(editor, c)
            exit(editor, st)
            return
        }
        if (buf.isEmpty()) {
            when (c) {
                in '0'..'9' -> {
                    st.pendingCount = st.pendingCount * 10 + (c - '0')
                    exit(editor, st)
                    return
                }

                '?' -> {
                    exit(editor, st)
                    Messages.showInfoMessage(editor.project, CHEATSHEET, "Meow Cheatsheet")
                    return
                }

                '/' -> {
                    st.keypad.append('/')
                    return
                }
            }
        }

        st.keypad.append(c)
        val cur = st.keypad.toString()
        val binding = keypad[cur]
        if (binding != null) {
            exit(editor, st)
            Engine.runBinding(editor, st, binding, ctx)
            return
        }
        if (keypad.keys.none { it.startsWith(cur) }) {
            exit(editor, st)
            Ide.hint(editor, "SPC ${cur.toCharArray().joinToString(" ")} is undefined")
        } else {
            WhichKey.scheduleKeypad(editor, cur)
        }
    }

    fun exit(
        editor: Editor,
        st: MeowState,
    ) {
        WhichKey.hide()
        Meow.setMode(editor, st, st.keypadPreviousState)
    }

    private fun describe(
        editor: Editor,
        c: Char,
    ) {
        val descs = Rc.keypadDescs()
        val entries =
            Rc
                .keypad()
                .entries
                .filter { it.key.startsWith(c.toString()) }
                .sortedBy { it.key }
                .joinToString("\n") { (seq, b) ->
                    val target = b.action ?: b.command ?: b.keys.orEmpty()
                    val desc = descs[seq]?.let { "  ($it)" } ?: ""
                    "SPC ${seq.toCharArray().joinToString(" ")}  ->  $target$desc"
                }
        Messages.showInfoMessage(
            editor.project,
            entries.ifEmpty { "SPC $c is undefined" },
            "Meow Describe: SPC $c",
        )
    }

    private val CHEATSHEET =
        """
        The bundled default layout (meow's suggested QWERTY) — every key
        below can be rebound from ~/.ideameowrc.

        NORMAL — selection first, then act
          h j k l  move (cancel selection)       H J K L  extend char selection
          w / W    mark word / symbol            e / E    next word / symbol end
          b / B    back word / symbol            x        line (repeat: extend)
          f / t    find / till char (inclusive / exclusive)
          o / O    block / to end of block       m        select join region
          , / .    inner / bounds of thing       [ / ]    to beginning / end of thing
             things: r round  s square  c curly  g string  e symbol  w window
                     b buffer  p paragraph  l line  v visual line  d defun  . sentence
          1-9, 0   expand selection by N units (0 = 10); without selection: count
          -        negative argument              ;        reverse selection
          i / a    insert at start / end          I / A    open line above / below
          c        change                         s        kill (cut)
          d / D    delete char/sel fwd / back     y        save (copy)
          p        yank (paste at point)          r        replace selection with clipboard
          u        undo                           '        repeat last command
          v        visit (regexp search+select)   n        search next (reversed sel = backward)
          z        pop selection (or grab)        g        cancel selection / carets
          G        grab (secondary selection)     R / Y    swap grab / sync grab
          Q / X    goto line                      q        close editor tab
          ESC      insert -> normal; drops extra carets
          BEACON   grab a region (G), then select w/x/f... inside it:
                   a caret lands on every match — edit them all, ESC to finish

        EMACS CHORDS (IDE keymap, not rc-configurable — Settings > Keymap)
          C-f/b/n/p  char/line move            C-a/e      beginning/end of line
          M-f/b      word move                 M-a/e      backward/forward sentence
                     no selection: just moves; with one active: extends it
                     (point motion over an active Emacs mark) — same rule ; reverses

        KEYPAD (SPC — or Alt+; from ANY state, INSERT included; returns there)
          SPC b bookmarks/buffers   SPC x file/buffer/window   SPC c commands   SPC m meta
          SPC w windows   SPC 0-9 count   SPC ? this sheet   SPC / describe key
          SPC c m edit ~/.ideameowrc   SPC c M reload it
          SPC i d track action ids — every performed action shows the id
                  to use in <action>(...) mappings
          REPEAT  some entries start a run (Emacs repeat-mode): after
                  SPC . e keep tapping . / , to walk errors, after SPC w i
                  keep tapping i (or = - o u 0) to keep zooming — any other
                  key ends the run and keeps its normal meaning

        ~/.ideameowrc: nmap <key> <action>(Id) | nmap <key> meow-command | nmap <key> <meow keys>
          mmap ... (MOTION mode) | map <leader><seq> ... | desc <leader><seq> text | set nowhich-key
          repeat <group> <key> <target> — tap-to-continue groups (the REPEAT runs above)
          every binding above is an rc line — the defaults ship as a bundled
          .ideameowrc inside the plugin; ~/.ideameowrc overrides them key by key
        """.trimIndent()
}

internal class KeypadAction : DumbAwareAction() {
    init {
        setEnabledInModalContext(true)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && Meow.state(editor) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val st = Meow.state(editor)
        if (st != null && st.mode != MeowMode.KEYPAD) Engine.enterKeypad(editor, st)
    }
}
