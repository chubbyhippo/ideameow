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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.awt.Container
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

object AceWindow {
    val commands: Map<String, MeowCommand> =
        mapOf(
            "ace-window" to MeowCommand { ed, st -> start(ed, st, swap = false) },
            "ace-swap-window" to MeowCommand { ed, st -> start(ed, st, swap = true) },
        )

    const val LABEL_THRESHOLD = 2

    enum class Plan { NONE, OTHER, LABELS }

    class Window(
        val rect: Rectangle,
        val editor: Editor?,
        val focusComponent: JComponent,
        val container: Component = focusComponent,
    )

    class Session(
        val swap: Boolean,
        val windows: List<Window>,
        val layer: JLayeredPane?,
        val current: Window?,
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

    internal fun otherWindow(
        windows: List<Window>,
        current: Window?,
    ): Window = windows.first { it !== current }

    private fun start(
        editor: Editor,
        st: MeowState,
        swap: Boolean,
    ) {
        val frame = SwingUtilities.getWindowAncestor(editor.component) ?: return
        val editors =
            (listOf(editor) + Windmove.visibleEditors(editor, frame)).mapNotNull { e ->
                Windmove.rectIn(frame, e.component)?.let { Window(it, e, e.contentComponent) }
            }
        val panels = if (swap) emptyList() else previewPanels(editor, frame) + toolWindowPanels(editor, frame, editors)
        val layer = (frame as? RootPaneContainer)?.rootPane?.layeredPane
        val windows = ordered((editors + panels).map { it to it.rect })
        begin(editor, st, swap, windows, layer, focusedWindow(windows))
    }

    private fun focusedWindow(windows: List<Window>): Window? {
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return null
        return windows.firstOrNull { SwingUtilities.isDescendingFrom(focus, it.editor?.component ?: it.container) }
    }

    internal fun begin(
        editor: Editor,
        st: MeowState,
        swap: Boolean,
        windows: List<Window>,
        layer: JLayeredPane? = null,
        current: Window? = null,
    ) {
        cancel(st)
        val cur = current ?: windows.firstOrNull { it.editor === editor }
        when (plan(windows.size)) {
            Plan.NONE -> return

            Plan.OTHER -> perform(editor, swap, otherWindow(windows, cur), cur)

            Plan.LABELS -> {
                val session = Session(swap, windows, layer, cur)
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
                val current = session.current
                cancel(st)
                if (target != null) perform(editor, swap, target, current)
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
        target: Window,
        current: Window?,
    ) {
        if (target === current) return
        val targetEditor = target.editor
        if (!swap) {
            IdeFocusManager.getInstance(editor.project).requestFocus(target.focusComponent, true)
        } else if (targetEditor == null) {
            Ide.hint(editor, "Cannot swap with this window")
        } else {
            swapWith(editor, targetEditor)
        }
    }

    private fun swapWith(
        editor: Editor,
        target: Editor,
    ) {
        val project = editor.project ?: return
        val fem = FileEditorManagerEx.getInstanceEx(project)
        val current = fem.currentWindow
        val targetWindow =
            fem.windows
                .firstOrNull { SwingUtilities.isDescendingFrom(target.component, it.tabbedPane.component) }
                ?.takeIf { it !== current }
        if (current == null || targetWindow == null || !Windmove.exchange(fem, current, targetWindow)) {
            Ide.hint(editor, "Cannot swap with this window")
        }
    }

    private fun paintLabels(session: Session) {
        clearVisuals(session)
        val node = session.node ?: return
        val badges = mutableListOf<Pair<Rectangle, String>>()
        for ((index, label) in Avy.labels(node)) {
            val target = session.windows.getOrNull(index) ?: continue
            val targetEditor = target.editor
            if (targetEditor != null) {
                val canvas = LeadCanvas(targetEditor, label)
                Overlay.attach(targetEditor, canvas)
                session.canvases.add(canvas)
            } else {
                badges.add(target.rect to label)
            }
        }
        val layer = session.layer
        if (badges.isEmpty() || layer == null) return
        val frame = SwingUtilities.getWindowAncestor(layer) ?: return
        session.canvases.add(
            Overlay.badge(
                layer,
                badges.map { (rect, label) -> SwingUtilities.convertRectangle(frame, rect, layer) to label },
            ),
        )
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

private fun previewPanels(
    editor: Editor,
    frame: java.awt.Window,
): List<AceWindow.Window> {
    val project = editor.project ?: return emptyList()
    return FileEditorManager
        .getInstance(project)
        .allEditors
        .filterIsInstance<TextEditorWithPreview>()
        .mapNotNull { composite ->
            val preview = composite.previewEditor
            if (SwingUtilities.getWindowAncestor(preview.component) !== frame) return@mapNotNull null
            Windmove.rectIn(frame, preview.component)?.let {
                AceWindow.Window(it, null, preview.preferredFocusedComponent ?: preview.component, preview.component)
            }
        }
}

private fun toolWindowPanels(
    editor: Editor,
    frame: java.awt.Window,
    editors: List<AceWindow.Window>,
): List<AceWindow.Window> {
    val project = editor.project ?: return emptyList()
    val manager = ToolWindowManager.getInstance(project)
    return manager.toolWindowIds.flatMap { id ->
        val toolWindow = manager.getToolWindow(id)?.takeIf { it.isVisible } ?: return@flatMap emptyList()
        val component = toolWindow.component
        if (SwingUtilities.getWindowAncestor(component) !== frame) return@flatMap emptyList()
        val paneWindows =
            panes(component).mapNotNull { pane ->
                Windmove.rectIn(frame, paneHost(pane))?.let { AceWindow.Window(it, null, pane, pane) }
            }
        if (paneWindows.isNotEmpty()) return@flatMap paneWindows
        if (editors.any { w -> w.editor != null && SwingUtilities.isDescendingFrom(w.editor.component, component) }) {
            return@flatMap emptyList()
        }
        val focusable = toolWindow.contentManagerIfCreated?.selectedContent?.preferredFocusableComponent
        Windmove
            .rectIn(frame, component)
            ?.let {
                listOf(AceWindow.Window(it, null, focusable ?: component, component))
            }.orEmpty()
    }
}

internal fun panes(root: Component): List<JComponent> {
    val out = mutableListOf<JComponent>()
    val queue = ArrayDeque<Component>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val c = queue.removeFirst()
        if (!c.isVisible) continue
        when {
            c is JTree || c is JTable || c is JList<*> -> out.add(c as JComponent)

            c is Container -> queue += c.components
        }
    }
    return out
}

internal fun paneHost(c: JComponent): Component = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, c) ?: c
