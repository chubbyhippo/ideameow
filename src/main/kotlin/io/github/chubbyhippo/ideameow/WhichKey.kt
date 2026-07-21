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
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Point
import javax.swing.Timer

object WhichKey {
    private var popup: JBPopup? = null
    private var timer: Timer? = null

    private var chainVisible = false

    private const val SEPARATOR = " → "
    private const val COLUMN_GAP = 28
    private const val PANEL_MARGIN = 28
    private const val BOTTOM_INSET = 8
    private const val LEFT_INSET = 4

    private val THINGS =
        listOf(
            "r" to "round ( )",
            "s" to "square [ ]",
            "c" to "curly { }",
            "g" to "string",
            "e" to "symbol",
            "w" to "window",
            "b" to "buffer",
            "p" to "paragraph",
            "l" to "line",
            "v" to "visual line",
            "d" to "defun",
            "." to "sentence",
        )

    fun scheduleKeypad(
        editor: Editor,
        buffer: String,
    ) = schedule(editor) { keypadRows(buffer) }

    fun scheduleThings(editor: Editor) = schedule(editor) { THINGS }

    fun keypadRows(buffer: String): List<Pair<String, String>> {
        val descs = Rc.keypadDescs()
        val rows = sortedMapOf<String, String>()
        for ((seq, b) in Rc.keypad()) {
            if (!seq.startsWith(buffer) || seq == buffer) continue
            val child = buffer + seq[buffer.length]
            val label =
                if (seq == child) {
                    descs[seq] ?: b.action ?: b.command ?: b.keys.orEmpty()
                } else {
                    descs[child] ?: "+more"
                }
            if (child !in rows || descs.containsKey(child)) rows[child] = label
        }
        return rows.map { (child, label) ->
            val key = child.last()
            (if (key == ' ') "SPC" else key.toString()) to label
        }
    }

    private fun schedule(
        editor: Editor,
        rowsProvider: () -> List<Pair<String, String>>,
    ) {
        hide()
        if (!Rc.whichKeyEnabled()) {
            chainVisible = false
            return
        }
        val delay = if (chainVisible) 0 else Rc.whichKeyDelayMs().coerceAtLeast(0)
        chainVisible = false
        timer =
            Timer(delay) {
                timer = null
                show(editor, rowsProvider())
            }.apply {
                isRepeats = false
                start()
            }
    }

    private fun show(
        editor: Editor,
        rows: List<Pair<String, String>>,
    ) {
        runCatching {
            if (rows.isEmpty() || editor.isDisposed) return
            val host = SpaceLeader.surfaceFor(editor) ?: editor.component
            val label =
                JBLabel(gridHtml(host, rows)).apply {
                    border = JBUI.Borders.empty(6, 10)
                }
            val p =
                JBPopupFactory
                    .getInstance()
                    .createComponentPopupBuilder(label, null)
                    .setRequestFocus(false)
                    .setFocusable(false)
                    .setCancelKeyEnabled(false)
                    .setCancelOnClickOutside(true)
                    .createPopup()
            popup = p
            val pref = label.preferredSize
            val y = (host.height - pref.height - JBUI.scale(BOTTOM_INSET)).coerceAtLeast(0)
            p.show(RelativePoint(host, Point(JBUI.scale(LEFT_INSET), y)))
        }
    }

    private fun gridHtml(
        host: Component,
        rows: List<Pair<String, String>>,
    ): String {
        val metrics = host.getFontMetrics(JBFont.label())
        val entryWidth = rows.maxOf { (k, d) -> metrics.stringWidth(k + SEPARATOR + d) } + JBUI.scale(COLUMN_GAP)
        val available = (host.width - JBUI.scale(PANEL_MARGIN)).coerceAtLeast(entryWidth)
        val cols = (available / entryWidth).coerceIn(1, rows.size)
        val perColumn = (rows.size + cols - 1) / cols
        return buildString {
            append("<html><table cellpadding='1' cellspacing='0'>")
            for (r in 0 until perColumn) {
                append("<tr>")
                for (c in 0 until cols) {
                    val i = c * perColumn + r
                    if (i < rows.size) {
                        val (k, d) = rows[i]
                        append("<td align='right'><b>").append(esc(k)).append("</b></td>")
                        append("<td>").append(SEPARATOR).append(esc(d)).append("</td>")
                        append("<td width='").append(JBUI.scale(18)).append("'></td>")
                    }
                }
                append("</tr>")
            }
            append("</table></html>")
        }
    }

    fun hide() {
        timer?.stop()
        timer = null
        popup?.let {
            chainVisible = it.isVisible
            runCatching { it.cancel() }
        }
        popup = null
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
