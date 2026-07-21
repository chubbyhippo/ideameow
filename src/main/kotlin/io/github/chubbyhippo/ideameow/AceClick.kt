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

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.tabs.impl.TabLabel
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JScrollBar
import javax.swing.JSpinner
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

object AceClick {
    val commands: Map<String, MeowCommand> =
        mapOf(
            "ace-click" to MeowCommand { ed, st -> start(ed, st) },
        )

    class Target(
        val rect: Rectangle,
        val component: JComponent,
        val click: () -> Unit,
    )

    class Session(
        val targets: List<Target>,
        val layer: JLayeredPane?,
    ) {
        var node: Avy.Branch? = null
        var canvas: JComponent? = null
    }

    private fun start(
        editor: Editor,
        st: MeowState,
    ) {
        val window = SwingUtilities.getWindowAncestor(editor.component) ?: return
        val layer = (window as? RootPaneContainer)?.rootPane?.layeredPane ?: return
        begin(editor, st, collect(window, layer), layer)
    }

    internal fun begin(
        editor: Editor,
        st: MeowState,
        targets: List<Target>,
        layer: JLayeredPane? = null,
    ) {
        cancel(st)
        if (targets.isEmpty()) {
            Ide.hint(editor, "No clickable components")
            return
        }
        val session = Session(AceWindow.ordered(targets.map { it to it.rect }), layer)
        st.aceClick = session
        session.node = Avy.tree(session.targets.indices.toList())
        paintLabels(session)
    }

    private fun collect(
        root: Component,
        layer: JLayeredPane,
    ): List<Target> {
        val out = mutableListOf<Target>()
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!c.isVisible) continue
            if (c is Container) queue += c.components
            targetOf(c, layer)?.let { out.add(it) }
        }
        return out
    }

    private fun targetOf(
        c: Component,
        layer: JLayeredPane,
    ): Target? {
        if (c !is JComponent || !c.isShowing) return null
        val click = clicker(c) ?: return null
        val visible = c.visibleRect
        if (visible.width <= 0 || visible.height <= 0) return null
        return Target(SwingUtilities.convertRectangle(c, visible, layer), c, click)
    }

    internal fun clicker(c: JComponent): (() -> Unit)? {
        if (!c.isEnabled) return null
        val parent = c.parent
        return when {
            c is ActionButton -> ({ clickActionButton(c) })

            c is AbstractButton && parent !is JComboBox<*> && parent !is JSpinner && parent !is JScrollBar ->
                ({ c.doClick() })

            c is InplaceButton -> ({ c.doClick() })

            c is LinkLabel<*> -> ({ c.doClick() })

            c is HyperlinkLabel -> ({ c.doClick() })

            c is JComboBox<*> -> ({ c.showPopup() })

            c is TabLabel -> ({ mouseClick(c) })

            standaloneTextInput(c) -> ({ mouseClick(c) })

            else -> null
        }
    }

    @Suppress("UnstableApiUsage")
    private fun clickActionButton(c: ActionButton) {
        if (c.isShowing) {
            c.click()
            return
        }
        val stamped = mutableListOf<JComponent>()
        var p: Component? = c
        while (p is JComponent) {
            stamped.add(p)
            p.putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, true)
            if (p is ActionToolbar) break
            p = p.parent
        }
        try {
            c.click()
        } finally {
            stamped.forEach { it.putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, null) }
        }
    }

    fun key(
        editor: Editor,
        st: MeowState,
        c: Char,
    ) {
        val session = st.aceClick ?: return
        val node = session.node ?: return
        when (val child = node.children.firstOrNull { it.first == c }?.second) {
            is Avy.Leaf -> {
                val target = session.targets.getOrNull(child.offset)
                cancel(st)
                if (target != null) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!editor.isDisposed && target.component.parent != null) {
                            runCatching { target.click() }.onFailure { Ide.hint(editor, "Click failed") }
                        }
                    }
                }
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
        st.aceClick?.let { Overlay.detach(it.canvas) }
        st.aceClick = null
    }

    private fun paintLabels(session: Session) {
        Overlay.detach(session.canvas)
        session.canvas = null
        val node = session.node ?: return
        val layer = session.layer ?: return
        val badges =
            Avy.labels(node).mapNotNull { (index, label) ->
                session.targets.getOrNull(index)?.let { it.rect to label }
            }
        session.canvas = Overlay.badge(layer, badges)
    }
}

private fun standaloneTextInput(c: JComponent): Boolean =
    c is JTextComponent &&
        c.isEditable &&
        SwingUtilities.getAncestorOfClass(JComboBox::class.java, c) == null &&
        SwingUtilities.getAncestorOfClass(JSpinner::class.java, c) == null

private fun mouseClick(c: JComponent) {
    val time = System.currentTimeMillis()
    val ids =
        intArrayOf(
            MouseEvent.MOUSE_PRESSED,
            MouseEvent.MOUSE_RELEASED,
            MouseEvent.MOUSE_CLICKED,
        )
    for (id in ids) {
        c.dispatchEvent(
            MouseEvent(c, id, time, 0, c.width / 2, c.height / 2, 1, false, MouseEvent.BUTTON1),
        )
    }
}
