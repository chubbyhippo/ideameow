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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

object Things {
    data class Bounds(
        val start: Int,
        val end: Int,
    )

    fun inner(
        editor: Editor,
        char: Char,
        offset: Int,
    ): Bounds? = compute(editor, char, offset, inner = true)

    fun bounds(
        editor: Editor,
        char: Char,
        offset: Int,
    ): Bounds? = compute(editor, char, offset, inner = false)

    private fun compute(
        editor: Editor,
        char: Char,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        val text = editor.document.charsSequence
        return when (char) {
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
            val char = text[i]
            if (char == close) {
                depth++
            } else if (char == open) {
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
            val char = text[j]
            if (char == open && j != start) {
                depth++
            } else if (char == close) {
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
        var i = 0
        while (i < text.length) {
            val quote = text[i]
            if (quote != '"' && quote != '\'' && quote != '`') {
                i++
                continue
            }
            val triple = i + 2 < text.length && text[i + 1] == quote && text[i + 2] == quote
            val quoteLen = if (triple) 3 else 1
            val open = i
            val closeEnd = stringEnd(text, i + quoteLen, quote, triple)
            if (closeEnd < 0) {
                i = open + quoteLen
                continue
            }
            if (offset in open until closeEnd) {
                return if (inner) Bounds(open + quoteLen, closeEnd - quoteLen) else Bounds(open, closeEnd)
            }
            i = closeEnd
        }
        return null
    }

    private fun stringEnd(
        text: CharSequence,
        contentStart: Int,
        quote: Char,
        triple: Boolean,
    ): Int {
        var j = contentStart
        while (j < text.length) {
            val char = text[j]
            if (!triple && char == '\n') return -1
            if (char == '\\') {
                j += 2
                continue
            }
            val closed = if (triple) j + 2 < text.length && text[j + 1] == quote && text[j + 2] == quote else true
            if (char == quote && closed) return j + if (triple) 3 else 1
            j++
        }
        return -1
    }

    fun isWordChar(char: Char) = Character.isLetterOrDigit(char)

    fun isSymbolChar(char: Char) = isWordChar(char) || char == '_' || char == '$'

    private fun symbol(
        text: CharSequence,
        offset: Int,
    ): Bounds? {
        var index = offset
        if (index >= text.length || !isSymbolChar(text[index])) {
            if (index > 0 && isSymbolChar(text[index - 1])) index-- else return null
        }
        val (start, end) = Words.spanAt(text, index, ::isSymbolChar)
        return Bounds(start, end)
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

        fun blank(line: Int): Boolean {
            val range = doc.charsSequence.subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
            return range.isBlank()
        }
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
        val visualLineNumber = editor.offsetToVisualPosition(offset).line
        val start = editor.visualPositionToOffset(VisualPosition(visualLineNumber, 0))
        var end = editor.visualPositionToOffset(VisualPosition(visualLineNumber + 1, 0))
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
        defunFromPsi(editor, offset)?.let { return it }
        val text = editor.document.charsSequence
        var bounds = pair(text, offset, '{', '}', false) ?: return null
        while (true) {
            val outer = pair(text, bounds.start, '{', '}', false) ?: break
            bounds = outer
        }
        return bounds
    }

    private fun defunFromPsi(
        editor: Editor,
        offset: Int,
    ): Bounds? {
        val project = editor.project ?: return null
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitDocument(editor.document)
        val file = psiDocumentManager.getPsiFile(editor.document) ?: return null
        val safeOffset = offset.coerceIn(0, (editor.document.textLength - 1).coerceAtLeast(0))
        var element = file.findElementAt(safeOffset)
        while (element != null && element !is PsiFile) {
            if (isFunctionElement(element)) return Bounds(element.textRange.startOffset, element.textRange.endOffset)
            element = element.parent
        }
        return null
    }

    private val FUNCTION_TYPE_MARKERS = listOf("METHOD", "FUNCTION", "LAMBDA")

    private fun isFunctionElement(element: PsiElement): Boolean {
        val typeName =
            element.node
                ?.elementType
                ?.toString()
                ?.uppercase() ?: ""
        return typeName.startsWith("FUN") || FUNCTION_TYPE_MARKERS.any { typeName.contains(it) }
    }

    private fun sentence(
        text: CharSequence,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        if (text.isEmpty()) return null

        fun blankLineBefore(pos: Int) = pos > 1 && text[pos - 1] == '\n' && text[pos - 2] == '\n'

        fun blankLineAt(pos: Int) = text[pos] == '\n' && pos + 1 < text.length && text[pos + 1] == '\n'

        var start = offset.coerceIn(0, text.length - 1)
        while (start > 0) {
            if (text[start - 1] in SENTENCE_ENDERS || blankLineBefore(start)) break
            start--
        }
        while (start < text.length && text[start].isWhitespace()) start++
        var end = offset.coerceIn(0, text.length)
        while (end < text.length && text[end] !in SENTENCE_ENDERS && !blankLineAt(end)) end++
        if (end < text.length && text[end] in SENTENCE_ENDERS) end++
        if (end <= start) return null
        if (inner) return Bounds(start, end)
        var extendedEnd = end
        while (extendedEnd < text.length && text[extendedEnd] == ' ') extendedEnd++
        return Bounds(start, extendedEnd)
    }
}

internal const val SENTENCE_ENDERS = ".!?"
