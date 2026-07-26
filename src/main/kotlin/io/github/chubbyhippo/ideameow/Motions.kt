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
import kotlin.math.abs

internal object Motions {
    val commands: Map<String, MeowCommand> =
        buildMap {
            put("meow-left", MeowCommand { editor, state -> moveChar(editor, state, -state.takeCount(1)) })
            put("meow-right", MeowCommand { editor, state -> moveChar(editor, state, state.takeCount(1)) })
            put("meow-next", MeowCommand { editor, state -> moveLine(editor, state, state.takeCount(1)) })
            put("meow-prev", MeowCommand { editor, state -> moveLine(editor, state, -state.takeCount(1)) })
            put(
                "meow-left-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = -state.takeCount(1), dy = 0) },
            )
            put(
                "meow-right-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = state.takeCount(1), dy = 0) },
            )
            put(
                "meow-next-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = 0, dy = state.takeCount(1)) },
            )
            put(
                "meow-prev-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = 0, dy = -state.takeCount(1)) },
            )
            put(
                "meow-next-word",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = false, count = state.takeCount(1)) },
            )
            put(
                "meow-next-symbol",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = true, count = state.takeCount(1)) },
            )
            put(
                "meow-back-word",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = false, count = -state.takeCount(1)) },
            )
            put(
                "meow-back-symbol",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = true, count = -state.takeCount(1)) },
            )
            put("meow-mark-word", MeowCommand { editor, state -> markWord(editor, state, symbol = false) })
            put("meow-mark-symbol", MeowCommand { editor, state -> markWord(editor, state, symbol = true) })
            put("meow-line", MeowCommand { editor, state -> line(editor, state) })
            put("meow-goto-line", MeowCommand { editor, state -> gotoLine(editor, state) })
            put("meow-find", MeowCommand { _, state -> state.pending = Pending.FIND })
            put("meow-till", MeowCommand { _, state -> state.pending = Pending.TILL })
            put("forward-char", MeowCommand { editor, state -> charOrExpand(editor, state, state.takeCount(1)) })
            put("backward-char", MeowCommand { editor, state -> charOrExpand(editor, state, -state.takeCount(1)) })
            put(
                "next-line",
                MeowCommand { editor, state ->
                    lineOrExpand(editor, state, state.takeCount(1))
                    state.lastCommand = "next-line"
                },
            )
            put(
                "previous-line",
                MeowCommand { editor, state ->
                    lineOrExpand(editor, state, -state.takeCount(1))
                    state.lastCommand = "previous-line"
                },
            )
            put(
                "move-beginning-of-line",
                MeowCommand { editor, state -> moveToOrExpand(editor, state, SelType.CHAR, ::lineStartOffset) },
            )
            put(
                "move-end-of-line",
                MeowCommand { editor, state -> moveToOrExpand(editor, state, SelType.CHAR, ::lineEndOffset) },
            )
            put(
                "back-to-indentation",
                MeowCommand { editor, state -> moveToOrExpand(editor, state, SelType.CHAR, ::indentationOffset) },
            )
            put("forward-word", MeowCommand { editor, state -> wordOrExpand(editor, state, state.takeCount(1)) })
            put("backward-word", MeowCommand { editor, state -> wordOrExpand(editor, state, -state.takeCount(1)) })
            put(
                "forward-sentence",
                MeowCommand { editor, state -> sentenceOrExpand(editor, state, state.takeCount(1)) },
            )
            put(
                "backward-sentence",
                MeowCommand { editor, state -> sentenceOrExpand(editor, state, -state.takeCount(1)) },
            )
            put("beginning-of-buffer", MeowCommand { editor, state -> bufferBoundary(editor, state, top = true) })
            put("end-of-buffer", MeowCommand { editor, state -> bufferBoundary(editor, state, top = false) })
            put(
                "forward-paragraph",
                MeowCommand { editor, state -> paragraphOrExpand(editor, state, state.takeCount(1)) },
            )
            put(
                "backward-paragraph",
                MeowCommand { editor, state -> paragraphOrExpand(editor, state, -state.takeCount(1)) },
            )
        }

    fun findTill(
        editor: Editor,
        state: MeowState,
        char: Char,
        till: Boolean,
    ) {
        val count = state.takeCount(1)
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val target = nthCharTarget(text, char, caret, abs(count), CharSearch(backward = count < 0, till = till))
        if (target < 0) {
            Ide.hint(editor, "char not found: $char")
            return
        }
        state.lastFind = char
        val spec = Selections.SelectionSpec(if (till) SelType.TILL else SelType.FIND, caret, target, expand = false)
        Selections.select(editor, state, spec)
    }
}
