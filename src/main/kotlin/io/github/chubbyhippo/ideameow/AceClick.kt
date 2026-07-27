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
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JScrollBar
import javax.swing.JSpinner
import javax.swing.JTree
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.text.JTextComponent

object AceClick {
    val commands: Map<String, MeowCommand> =
        mapOf(
            "ace-click" to MeowCommand { editor, state -> start(editor, state) },
        )

    class Target(
        val rect: Rectangle,
        val component: JComponent,
        val layer: JLayeredPane? = null,
        val screen: Rectangle = rect,
        val rightClick: () -> Unit = {},
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
        state: MeowState,
    ) {
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val base =
            focus?.let(SwingUtilities::getWindowAncestor)
                ?: SwingUtilities.getWindowAncestor(editor.component)
                ?: return
        begin(editor, state, (listOf(base) + menuWindows()).distinct().flatMap(::collect))
    }

    internal fun begin(
        editor: Editor,
        state: MeowState,
        targets: List<Target>,
    ) {
        cancel(state)
        if (targets.isEmpty()) {
            Ide.hint(editor, "No clickable components")
            return
        }
        val session = Session(AceWindow.ordered(targets.map { it to it.screen }))
        state.aceClick = session
        session.node = Avy.tree(session.targets.indices.toList())
        paintLabels(session)
    }

    internal fun labelOpenMenus(
        editor: Editor,
        state: MeowState,
        targets: List<Target> = openMenus().flatMap(::collect),
    ): Boolean {
        if (targets.isEmpty()) return false
        begin(editor, state, targets)
        openMenus().firstOrNull()?.let { SpaceLeader.routeTo(editor, state, it) }
        return true
    }

    private fun collect(root: Component): List<Target> {
        val window = root as? Window ?: SwingUtilities.getWindowAncestor(root)
        val layer = (window as? RootPaneContainer)?.rootPane?.layeredPane ?: return emptyList()
        val out = mutableListOf<Target>()
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!component.isVisible) continue
            if (component is Container) queue += component.components
            val rows = rowTargets(component, layer)
            if (rows != null) out.addAll(rows) else targetOf(component, layer)?.let { out.add(it) }
        }
        return out
    }

    private fun rowTargets(
        component: Component,
        layer: JLayeredPane,
    ): List<Target>? {
        if (component !is JComponent || !component.isShowing) return null
        return when (component) {
            is JTree -> treeRows(component, layer)
            is JList<*> -> listCells(component, layer)
            else -> null
        }
    }

    internal fun clicker(component: JComponent): (() -> Unit)? {
        if (!component.isEnabled) return null
        return when {
            component is ActionButton -> ({ clickActionButton(component) })
            component is JMenuItem -> ({ clickMenuItem(component) })
            component is AbstractButton && !wrappedButtonChild(component.parent) -> ({ component.doClick() })
            component is InplaceButton -> ({ component.doClick() })
            component is LinkLabel<*> -> ({ component.doClick() })
            component is HyperlinkLabel -> ({ component.doClick() })
            component is JComboBox<*> -> ({ component.showPopup() })
            component is TabLabel -> ({ mouseClick(component) })
            standaloneTextInput(component) -> ({ mouseClick(component) })
            else -> null
        }
    }

    fun key(
        editor: Editor,
        state: MeowState,
        char: Char,
    ) {
        val session = state.aceClick ?: return
        val node = session.node ?: return
        val lower = char.lowercaseChar()
        val secondary = char != lower
        when (val child = node.children.firstOrNull { it.first == lower }?.second) {
            is Avy.Leaf -> {
                val target = session.targets.getOrNull(child.offset)
                cancel(state)
                if (target != null) {
                    val action = if (secondary) target.rightClick else target.click
                    ApplicationManager.getApplication().invokeLater {
                        if (!editor.isDisposed && target.component.parent != null) {
                            if (target.component !is MenuElement) {
                                MenuSelectionManager.defaultManager().clearSelectedPath()
                            }
                            runCatching { action() }.onFailure { Ide.hint(editor, "Click failed") }
                            scheduleMenuLabels(editor, state)
                        }
                    }
                }
            }

            is Avy.Branch -> {
                session.node = child
                paintLabels(session)
            }

            null -> {
                Ide.hint(editor, "No such candidate: $char")
            }
        }
    }

    fun cancel(state: MeowState) {
        state.aceClick?.canvases?.forEach { Overlay.detach(it) }
        state.aceClick = null
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

private const val MENU_SETTLE_MS = 60
private const val MENU_SETTLE_TRIES = 5

private fun scheduleMenuLabels(
    editor: Editor,
    state: MeowState,
    triesLeft: Int = MENU_SETTLE_TRIES,
) {
    Timer(MENU_SETTLE_MS) {
        if (menuLabelsWanted(editor, state) && !AceClick.labelOpenMenus(editor, state) && triesLeft > 1) {
            scheduleMenuLabels(editor, state, triesLeft - 1)
        }
    }.apply {
        isRepeats = false
        start()
    }
}

private fun menuLabelsWanted(
    editor: Editor,
    state: MeowState,
) = !editor.isDisposed && state.aceClick == null

private fun openMenus(): List<JPopupMenu> =
    MenuSelectionManager
        .defaultManager()
        .selectedPath
        .filterIsInstance<JPopupMenu>()

private fun menuWindows(): List<Window> = openMenus().mapNotNull(SwingUtilities::getWindowAncestor)

private fun clickMenuItem(menuItem: JMenuItem) {
    if (menuItem is JMenu) {
        menuItem.doClick()
        return
    }
    MenuSelectionManager.defaultManager().clearSelectedPath()
    menuItem.doClick(0)
}

@Suppress("UnstableApiUsage")
private fun clickActionButton(button: ActionButton) {
    if (button.isShowing) {
        button.click()
        return
    }
    val stamped = mutableListOf<JComponent>()
    var ancestor: Component? = button
    while (ancestor is JComponent) {
        stamped.add(ancestor)
        ancestor.putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, true)
        if (ancestor is ActionToolbar) break
        ancestor = ancestor.parent
    }
    try {
        button.click()
    } finally {
        stamped.forEach { it.putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, null) }
    }
}

private fun wrappedButtonChild(parent: Container?): Boolean =
    parent is JComboBox<*> || parent is JSpinner || parent is JScrollBar

private fun standaloneTextInput(component: JComponent): Boolean =
    component is JTextComponent &&
        component.isEditable &&
        SwingUtilities.getAncestorOfClass(JComboBox::class.java, component) == null &&
        SwingUtilities.getAncestorOfClass(JSpinner::class.java, component) == null

private fun mouseClick(component: JComponent) {
    val time = System.currentTimeMillis()
    val ids =
        intArrayOf(
            MouseEvent.MOUSE_PRESSED,
            MouseEvent.MOUSE_RELEASED,
            MouseEvent.MOUSE_CLICKED,
        )
    for (id in ids) {
        component.dispatchEvent(
            MouseEvent(component, id, time, 0, component.width / 2, component.height / 2, 1, false, MouseEvent.BUTTON1),
        )
    }
}
