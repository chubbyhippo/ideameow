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
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JScrollBar
import javax.swing.JSpinner
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
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
        val layer: JLayeredPane? = null,
        val screen: Rectangle = rect,
        val click: () -> Unit,
    )

    class Session(
        val targets: List<Target>,
    ) {
        var node: Avy.Branch? = null
        val canvases = mutableListOf<JComponent>()
    }

    private fun start(
        editor: Editor,
        st: MeowState,
    ) {
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val base =
            focus?.let(SwingUtilities::getWindowAncestor)
                ?: SwingUtilities.getWindowAncestor(editor.component)
                ?: return
        begin(editor, st, (listOf(base) + menuWindows()).distinct().flatMap(::collect))
    }

    private fun menuWindows(): List<Window> =
        MenuSelectionManager
            .defaultManager()
            .selectedPath
            .filterIsInstance<JPopupMenu>()
            .mapNotNull(SwingUtilities::getWindowAncestor)

    internal fun begin(
        editor: Editor,
        st: MeowState,
        targets: List<Target>,
    ) {
        cancel(st)
        if (targets.isEmpty()) {
            Ide.hint(editor, "No clickable components")
            return
        }
        val session = Session(AceWindow.ordered(targets.map { it to it.screen }))
        st.aceClick = session
        session.node = Avy.tree(session.targets.indices.toList())
        paintLabels(session)
    }

    private fun collect(root: Window): List<Target> {
        val layer = (root as? RootPaneContainer)?.rootPane?.layeredPane ?: return emptyList()
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
        val screen = Rectangle(visible)
        val corner = screen.location
        SwingUtilities.convertPointToScreen(corner, c)
        screen.location = corner
        return Target(SwingUtilities.convertRectangle(c, visible, layer), c, layer, screen, click)
    }

    internal fun clicker(c: JComponent): (() -> Unit)? {
        if (!c.isEnabled) return null
        return when {
            c is ActionButton -> ({ clickActionButton(c) })

            c is JMenuItem -> ({ clickMenuItem(c) })

            c is AbstractButton && !wrappedButtonChild(c.parent) -> ({ c.doClick() })

            c is InplaceButton -> ({ c.doClick() })

            c is LinkLabel<*> -> ({ c.doClick() })

            c is HyperlinkLabel -> ({ c.doClick() })

            c is JComboBox<*> -> ({ c.showPopup() })

            c is TabLabel -> ({ mouseClick(c) })

            standaloneTextInput(c) -> ({ mouseClick(c) })

            else -> null
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
                            if (target.component !is MenuElement) {
                                MenuSelectionManager.defaultManager().clearSelectedPath()
                            }
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
        st.aceClick?.canvases?.forEach { Overlay.detach(it) }
        st.aceClick = null
    }

    private fun paintLabels(session: Session) {
        session.canvases.forEach { Overlay.detach(it) }
        session.canvases.clear()
        val node = session.node ?: return
        Avy
            .labels(node)
            .mapNotNull { (index, label) -> session.targets.getOrNull(index)?.let { it to label } }
            .groupBy { (target, _) -> target.layer }
            .forEach { (layer, badges) ->
                if (layer != null) {
                    session.canvases += Overlay.badge(layer, badges.map { (target, label) -> target.rect to label })
                }
            }
    }
}

private fun clickMenuItem(c: JMenuItem) {
    if (c is JMenu) {
        c.doClick()
        return
    }
    MenuSelectionManager.defaultManager().clearSelectedPath()
    c.doClick(0)
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

private fun wrappedButtonChild(p: Container?): Boolean = p is JComboBox<*> || p is JSpinner || p is JScrollBar

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
