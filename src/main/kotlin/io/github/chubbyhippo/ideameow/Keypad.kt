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
import com.intellij.openapi.ui.Messages

/**
 * KEYPAD state. In Emacs, SPC x/c/m reach the C-x / C-c / M- keymaps; here the
 * same key sequences dispatch IDE actions. BUILTIN mirrors the leader scheme
 * of the companion .ideavimrc; ~/.ideameowrc `map <leader>...` entries layer
 * on top (see Rc). SPC 1-9 = digit argument, SPC ? = cheatsheet,
 * SPC / = describe key. A which-key popup lists continuations of a prefix.
 */
object Keypad {

    val BUILTIN: Map<String, String> = mapOf(
        "b" to "RecentFiles",
        " " to "Switcher",
        // SPC x = C-x: files / buffers / windows
        "xf" to "GotoFile", "xs" to "SaveAll", "xb" to "RecentFiles", "xk" to "CloseContent",
        "xd" to "ActivateProjectToolWindow", "xg" to "ActivateVersionControlToolWindow",
        "xo" to "NextSplitter", "xu" to "LocalHistory.ShowHistory", "xv" to "Vcs.Operations.Popup",
        "xc" to "CloseProject", "x0" to "Unsplit", "x1" to "UnsplitAll",
        "x2" to "SplitHorizontally", "x3" to "SplitVertically",
        // SPC x p = C-x p: project.el
        "xpf" to "GotoFile", "xpb" to "RecentFiles", "xpg" to "FindInPath",
        "xpr" to "ReplaceInPath", "xpc" to "CompileProject",
        // SPC c = C-c: personal commands
        "cf" to "GotoFile", "cs" to "SaveAll", "ck" to "CloseContent", "cb" to "RecentFiles",
        "cr" to "FindInPath", "ce" to "EditorSelectWord", "ca" to "ActivateTODOToolWindow",
        "cc" to "NewScratchFile", "cl" to "CopyReference", "cg" to "Vcs.Operations.Popup",
        // SPC c v / V: edit / reload ~/.ideameowrc (mirrors the .ideavimrc keys)
        "cv" to "Ideameow.EditRc", "cV" to "Ideameow.ReloadRc",
        // SPC c j: avy jumps — AceJump actions when the plugin is installed
        "cjw" to "AceAction", "cjc" to "AceAction", "cjl" to "AceLineAction",
        // SPC m = M-: meta
        "mx" to "GotoAction", "mo" to "AceAction", "mr" to "EditorSelectWord",
        "mR" to "EditorUnSelectWord", "my" to "PasteMultiple", "m." to "GotoDeclaration",
        "mg" to "GotoLine",
        "msr" to "FindInPath", "msl" to "Find", "mss" to "Find",
        "msL" to "FindInPath", "mso" to "FileStructurePopup",
        // SPC w: the window map
        "wv" to "SplitVertically", "ws" to "SplitHorizontally", "wd" to "Unsplit",
        "wD" to "UnsplitAll", "wm" to "UnsplitAll", "ww" to "NextSplitter",
        "wW" to "MoveEditorToOppositeTabGroup",
        "wh" to "PrevSplitter", "wj" to "NextSplitter", "wk" to "PrevSplitter", "wl" to "NextSplitter",
        "wi" to "EditorIncreaseFontSize", "w=" to "EditorIncreaseFontSize",
        "wo" to "EditorDecreaseFontSize", "w-" to "EditorDecreaseFontSize",
        "wu" to "EditorResetFontSize", "w0" to "EditorResetFontSize",
    )

    fun key(editor: Editor, st: MeowState, c: Char, ctx: DataContext?) {
        WhichKey.hide()
        val cfg = Rc.cfg()
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
                    Messages.showInfoMessage(editor.project, CHEATSHEET, "Meow QWERTY Cheatsheet")
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
        val binding = cfg.keypad[cur]
        if (binding != null) {
            exit(editor, st)
            Engine.runBinding(editor, st, binding, ctx)
            return
        }
        if (cfg.keypad.keys.none { it.startsWith(cur) }) {
            exit(editor, st)
            Engine.hint(editor, "SPC ${cur.toCharArray().joinToString(" ")} is undefined")
        } else {
            WhichKey.scheduleKeypad(editor, cur)
        }
    }

    fun exit(editor: Editor, st: MeowState) {
        WhichKey.hide()
        Meow.setMode(editor, st, MeowMode.NORMAL)
    }

    private fun describe(editor: Editor, c: Char) {
        val cfg = Rc.cfg()
        val entries = cfg.keypad.entries
            .filter { it.key.startsWith(c.toString()) }
            .sortedBy { it.key }
            .joinToString("\n") { (seq, b) ->
                val target = b.action ?: b.keys.orEmpty()
                val desc = cfg.keypadDesc[seq]?.let { "  ($it)" } ?: ""
                "SPC ${seq.toCharArray().joinToString(" ")}  ->  $target$desc"
            }
        Messages.showInfoMessage(
            editor.project,
            if (entries.isEmpty()) "SPC $c is undefined" else entries,
            "Meow Describe: SPC $c"
        )
    }

    private val CHEATSHEET = """
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

        KEYPAD (SPC)
          SPC b buffers   SPC x file/buffer/window   SPC c commands   SPC m meta
          SPC w windows   SPC 1-9 count   SPC ? this sheet   SPC / describe key
          SPC c v edit ~/.ideameowrc   SPC c V reload it

        ~/.ideameowrc: nmap <key> <action>(Id) | nmap <key> <meow keys>
          map <leader><seq> ... | desc <leader><seq> text | set nowhich-key
    """.trimIndent()
}
