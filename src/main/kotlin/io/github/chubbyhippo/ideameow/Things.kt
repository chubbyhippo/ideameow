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
import com.intellij.openapi.editor.VisualPosition
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

object Things {
    data class Bounds(
        val start: Int,
        val end: Int,
    )

    fun inner(
        editor: Editor,
        ch: Char,
        offset: Int,
    ): Bounds? = compute(editor, ch, offset, inner = true)

    fun bounds(
        editor: Editor,
        ch: Char,
        offset: Int,
    ): Bounds? = compute(editor, ch, offset, inner = false)

    private fun compute(
        editor: Editor,
        ch: Char,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        val text = editor.document.charsSequence
        return when (ch) {
            'r' -> pair(text, offset, '(', ')', inner)
            's' -> pair(text, offset, '[', ']', inner)
            'c' -> pair(text, offset, '{', '}', inner)
            'g' -> string(text, offset, inner)
            'e' -> symbol(text, offset)
            'w' -> window(editor)
            'b' -> Bounds(0, text.length)
            'p' -> paragraph(editor, offset, inner)
            'l' -> line(editor, offset, inner)
            'v' -> visualLine(editor, offset)
            'd' -> defun(editor, offset)
            '.' -> sentence(text, offset, inner)
            else -> null
        }
    }

    fun pair(
        text: CharSequence,
        offset: Int,
        open: Char,
        close: Char,
        inner: Boolean,
    ): Bounds? {
        var depth = 0
        var start = -1
        var i = offset - 1
        while (i >= 0) {
            val c = text[i]
            if (c == close) {
                depth++
            } else if (c == open) {
                if (depth == 0) {
                    start = i
                    break
                }
                depth--
            }
            i--
        }
        if (start < 0) return null
        depth = 0
        var end = -1
        var j = offset
        while (j < text.length) {
            val c = text[j]
            if (c == open && j != start) {
                depth++
            } else if (c == close) {
                if (depth == 0) {
                    end = j
                    break
                }
                depth--
            }
            j++
        }
        if (end < 0) return null
        return if (inner) Bounds(start + 1, end) else Bounds(start, end + 1)
    }

    private fun string(
        text: CharSequence,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '"' || c == '\'' || c == '`') {
                val triple = i + 2 < n && text[i + 1] == c && text[i + 2] == c
                val len = if (triple) 3 else 1
                val open = i
                var j = i + len
                var closeEnd = -1
                while (j < n) {
                    val d = text[j]
                    if (!triple && d == '\n') break
                    if (d == '\\') {
                        j += 2
                        continue
                    }
                    val closes = if (triple) j + 2 < n && text[j + 1] == c && text[j + 2] == c else true
                    if (d == c && closes) {
                        closeEnd = j + len
                        break
                    }
                    j++
                }
                if (closeEnd < 0) {
                    i = open + len
                    continue
                }
                if (offset in open until closeEnd) {
                    return if (inner) Bounds(open + len, closeEnd - len) else Bounds(open, closeEnd)
                }
                i = closeEnd
                continue
            }
            i++
        }
        return null
    }

    fun isWordChar(c: Char) = Character.isLetterOrDigit(c)

    fun isSymbolChar(c: Char) = isWordChar(c) || c == '_' || c == '$'

    private fun symbol(
        text: CharSequence,
        offset: Int,
    ): Bounds? {
        var o = offset
        if (o >= text.length || !isSymbolChar(text[o])) {
            if (o > 0 && isSymbolChar(text[o - 1])) o-- else return null
        }
        val (s, e) = Words.spanAt(text, o, ::isSymbolChar)
        return Bounds(s, e)
    }

    private fun window(editor: Editor): Bounds {
        val (startLine, endLine) = Ide.visibleLines(editor)
        val doc = editor.document
        return Bounds(doc.getLineStartOffset(startLine), doc.getLineEndOffset(endLine))
    }

    private fun paragraph(
        editor: Editor,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        val doc = editor.document
        if (doc.lineCount == 0) return null

        fun blank(l: Int) = doc.charsSequence.subSequence(doc.getLineStartOffset(l), doc.getLineEndOffset(l)).isBlank()
        val ln = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
        if (blank(ln)) return null
        var first = ln
        var last = ln
        while (first > 0 && !blank(first - 1)) first--
        while (last < doc.lineCount - 1 && !blank(last + 1)) last++
        val start = doc.getLineStartOffset(first)
        if (inner) return Bounds(start, doc.getLineEndOffset(last))
        var stop = last
        while (stop < doc.lineCount - 1 && blank(stop + 1)) stop++
        val end = if (stop < doc.lineCount - 1) doc.getLineStartOffset(stop + 1) else doc.getLineEndOffset(stop)
        return Bounds(start, end)
    }

    private fun line(
        editor: Editor,
        offset: Int,
        inner: Boolean,
    ): Bounds {
        val doc = editor.document
        val ln = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
        val end = doc.getLineEndOffset(ln)
        return if (inner) {
            Bounds(doc.getLineStartOffset(ln), end)
        } else {
            Bounds(doc.getLineStartOffset(ln), (end + 1).coerceAtMost(doc.textLength))
        }
    }

    private fun visualLine(
        editor: Editor,
        offset: Int,
    ): Bounds {
        val vLine = editor.offsetToVisualPosition(offset).line
        val start = editor.visualPositionToOffset(VisualPosition(vLine, 0))
        var end = editor.visualPositionToOffset(VisualPosition(vLine + 1, 0))
        if (end <= start) {
            end = editor.document.textLength
        } else if (end > 0 && editor.document.charsSequence[end - 1] == '\n') {
            end--
        }
        return Bounds(start, end)
    }

    private fun defun(
        editor: Editor,
        offset: Int,
    ): Bounds? {
        val project = editor.project
        if (project != null) {
            val pdm = PsiDocumentManager.getInstance(project)
            pdm.commitDocument(editor.document)
            val file = pdm.getPsiFile(editor.document)
            if (file != null) {
                var el = file.findElementAt(offset.coerceIn(0, (editor.document.textLength - 1).coerceAtLeast(0)))
                while (el != null && el !is PsiFile) {
                    val t =
                        el.node
                            ?.elementType
                            ?.toString()
                            ?.uppercase() ?: ""
                    if (t.contains("METHOD") || t.contains("FUNCTION") || t.startsWith("FUN") || t.contains("LAMBDA")) {
                        return Bounds(el.textRange.startOffset, el.textRange.endOffset)
                    }
                    el = el.parent
                }
            }
        }
        val text = editor.document.charsSequence
        var b = pair(text, offset, '{', '}', false) ?: return null
        while (true) {
            val outer = pair(text, b.start, '{', '}', false) ?: break
            b = outer
        }
        return b
    }

    private fun sentence(
        text: CharSequence,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        if (text.isEmpty()) return null

        fun blankLineBefore(pos: Int) = pos > 1 && text[pos - 1] == '\n' && text[pos - 2] == '\n'

        fun blankLineAt(pos: Int) = text[pos] == '\n' && pos + 1 < text.length && text[pos + 1] == '\n'

        var s = offset.coerceIn(0, text.length - 1)
        while (s > 0) {
            if (text[s - 1] in SENTENCE_ENDERS || blankLineBefore(s)) break
            s--
        }
        while (s < text.length && text[s].isWhitespace()) s++
        var e = offset.coerceIn(0, text.length)
        while (e < text.length && text[e] !in SENTENCE_ENDERS && !blankLineAt(e)) e++
        if (e < text.length && text[e] in SENTENCE_ENDERS) e++
        if (e <= s) return null
        if (inner) return Bounds(s, e)
        var be = e
        while (be < text.length && text[be] == ' ') be++
        return Bounds(s, be)
    }
}

internal const val SENTENCE_ENDERS = ".!?"
