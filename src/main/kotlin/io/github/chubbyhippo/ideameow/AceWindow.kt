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
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.wm.IdeFocusManager
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities

object AceWindow {
    val commands: Map<String, MeowCommand> =
        mapOf(
            "ace-window" to MeowCommand { ed, st -> start(ed, st, swap = false) },
            "ace-swap-window" to MeowCommand { ed, st -> start(ed, st, swap = true) },
        )

    const val LABEL_THRESHOLD = 2

    enum class Plan { NONE, OTHER, LABELS }

    class Session(
        val swap: Boolean,
        val windows: List<Editor>,
    ) {
        var node: Avy.Branch? = null
        val canvases = mutableListOf<JComponent>()
    }

    fun plan(windowCount: Int): Plan =
        when {
            windowCount <= 1 -> Plan.NONE
            windowCount <= LABEL_THRESHOLD -> Plan.OTHER
            else -> Plan.LABELS
        }

    fun <T> ordered(candidates: List<Pair<T, Rectangle>>): List<T> =
        candidates.sortedWith(compareBy({ it.second.x }, { it.second.y })).map { it.first }

    private fun start(
        editor: Editor,
        st: MeowState,
        swap: Boolean,
    ) {
        cancel(st)
        val frame = SwingUtilities.getWindowAncestor(editor.component) ?: return
        val windows =
            ordered(
                (listOf(editor) + Windmove.visibleEditors(editor, frame)).mapNotNull { e ->
                    Windmove.rectIn(frame, e.component)?.let { e to it }
                },
            )
        when (plan(windows.size)) {
            Plan.NONE -> return

            Plan.OTHER -> perform(editor, swap, windows.first { it !== editor })

            Plan.LABELS -> {
                val session = Session(swap, windows)
                st.aceWindow = session
                session.node = Avy.tree(windows.indices.toList())
                paintLabels(session)
            }
        }
    }

    fun key(
        editor: Editor,
        st: MeowState,
        c: Char,
    ) {
        val session = st.aceWindow ?: return
        val node = session.node ?: return
        when (val child = node.children.firstOrNull { it.first == c }?.second) {
            is Avy.Leaf -> {
                val target = session.windows.getOrNull(child.offset)
                val swap = session.swap
                cancel(st)
                if (target != null) perform(editor, swap, target)
            }

            is Avy.Branch -> {
                session.node = child
                paintLabels(session)
            }

            null -> {
                Ide.hint(editor, "No such candidate: $c")
            }
        }
    }

    fun cancel(st: MeowState) {
        st.aceWindow?.let { clearVisuals(it) }
        st.aceWindow = null
    }

    private fun perform(
        editor: Editor,
        swap: Boolean,
        target: Editor,
    ) {
        if (target === editor) return
        if (swap) {
            swapWith(editor, target)
        } else {
            IdeFocusManager.getInstance(editor.project).requestFocus(target.contentComponent, true)
        }
    }

    private fun swapWith(
        editor: Editor,
        target: Editor,
    ) {
        val project = editor.project ?: return
        val fem = FileEditorManagerEx.getInstanceEx(project)
        val current = fem.currentWindow
        val targetWindow = editorWindowOf(fem, target)?.takeIf { it !== current }
        if (current == null || targetWindow == null || !Windmove.exchange(fem, current, targetWindow)) {
            Ide.hint(editor, "Cannot swap with this window")
        }
    }

    private fun editorWindowOf(
        fem: FileEditorManagerEx,
        target: Editor,
    ): EditorWindow? =
        fem.windows.firstOrNull {
            SwingUtilities.isDescendingFrom(target.component, it.tabbedPane.component)
        }

    private fun paintLabels(session: Session) {
        clearVisuals(session)
        val node = session.node ?: return
        for ((index, label) in Avy.labels(node)) {
            val target = session.windows.getOrNull(index) ?: continue
            val canvas = LeadCanvas(target, label)
            Overlay.attach(target, canvas)
            session.canvases.add(canvas)
        }
    }

    private fun clearVisuals(session: Session) {
        session.canvases.forEach { Overlay.detach(it) }
        session.canvases.clear()
    }

    private class LeadCanvas(
        editor: Editor,
        private val label: String,
    ) : Overlay.Canvas(editor) {
        override fun paintLabels(
            g2: Graphics2D,
            metrics: FontMetrics,
        ) {
            val area = editor.scrollingModel.visibleArea
            val width = metrics.stringWidth(label) + Overlay.LABEL_PADDING
            g2.color = Avy.LEAD_BG
            g2.fillRect(area.x, area.y, width, editor.lineHeight)
            g2.color = Avy.LEAD_FG
            g2.drawString(label, area.x + 1, area.y + editor.ascent)
        }
    }
}
