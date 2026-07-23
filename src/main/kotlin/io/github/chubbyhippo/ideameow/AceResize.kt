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
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

object AceResize {
    const val STEP = 0.05f
    const val MIN_PROPORTION = 0.05f

    val commands: Map<String, MeowCommand> =
        mapOf(
            "ace-resize" to MeowCommand { ed, st -> start(ed, st) },
        )

    enum class Dir { LEFT, RIGHT, DOWN, UP }

    enum class Axis { HORIZONTAL, VERTICAL, BOTH }

    enum class Phase { PICK, HOLD }

    class Target(
        val rect: Rectangle,
        val axis: Axis = Axis.BOTH,
        val layer: JLayeredPane? = null,
        val screen: Rectangle = rect,
        val resize: (Dir) -> Unit,
    )

    class Session(
        val targets: List<Target>,
    ) {
        var node: Avy.Branch? = null
        var phase = Phase.PICK
        var picked: Target? = null
        val canvases = mutableListOf<JComponent>()
    }

    fun dirOf(c: Char): Dir? =
        when (c) {
            'h' -> Dir.LEFT
            'l' -> Dir.RIGHT
            'j' -> Dir.DOWN
            'k' -> Dir.UP
            else -> null
        }

    fun nudge(
        vertical: Boolean,
        dir: Dir,
        proportion: Float,
        step: Float,
    ): Float? {
        val delta =
            when {
                vertical && dir == Dir.UP -> -step
                vertical && dir == Dir.DOWN -> step
                !vertical && dir == Dir.LEFT -> -step
                !vertical && dir == Dir.RIGHT -> step
                else -> return null
            }
        return (proportion + delta).coerceIn(MIN_PROPORTION, 1f - MIN_PROPORTION)
    }

    fun holdLabel(axis: Axis): String =
        when (axis) {
            Axis.HORIZONTAL -> "h l"
            Axis.VERTICAL -> "j k"
            Axis.BOTH -> "hjkl"
        }

    fun toolWindowAction(dir: Dir): String =
        when (dir) {
            Dir.LEFT -> "ResizeToolWindowLeft"
            Dir.RIGHT -> "ResizeToolWindowRight"
            Dir.DOWN -> "ResizeToolWindowDown"
            Dir.UP -> "ResizeToolWindowUp"
        }

    private fun start(
        editor: Editor,
        st: MeowState,
    ) {
        val frame = SwingUtilities.getWindowAncestor(editor.component) ?: return
        val layer = (frame as? RootPaneContainer)?.rootPane?.layeredPane
        begin(editor, st, splitterTargets(frame, layer) + toolWindowTargets(editor, frame, layer))
    }

    internal fun begin(
        editor: Editor,
        st: MeowState,
        targets: List<Target>,
    ) {
        cancel(st)
        if (targets.isEmpty()) {
            Ide.hint(editor, "No resizable dividers")
            return
        }
        val session = Session(AceWindow.ordered(targets.map { it to it.screen }))
        st.aceResize = session
        session.node = Avy.tree(session.targets.indices.toList())
        paintLabels(session)
    }

    fun key(
        editor: Editor,
        st: MeowState,
        c: Char,
    ) {
        val session = st.aceResize ?: return
        if (session.phase == Phase.HOLD) hold(st, session, c) else pick(editor, st, session, c)
    }

    fun cancel(st: MeowState) {
        st.aceResize?.canvases?.forEach { Overlay.detach(it) }
        st.aceResize = null
    }
}

private fun pick(
    editor: Editor,
    st: MeowState,
    session: AceResize.Session,
    c: Char,
) {
    val node = session.node ?: return
    when (val child = node.children.firstOrNull { it.first == c }?.second) {
        is Avy.Leaf -> enterHold(editor, st, session, child.offset)

        is Avy.Branch -> {
            session.node = child
            paintLabels(session)
        }

        null -> Ide.hint(editor, "No such candidate: $c")
    }
}

private fun enterHold(
    editor: Editor,
    st: MeowState,
    session: AceResize.Session,
    offset: Int,
) {
    val target = session.targets.getOrNull(offset)
    if (target == null) {
        AceResize.cancel(st)
        return
    }
    session.phase = AceResize.Phase.HOLD
    session.picked = target
    session.node = null
    paintHold(session)
    Ide.hint(editor, "Resize ${AceResize.holdLabel(target.axis)} · ESC exits")
}

private fun hold(
    st: MeowState,
    session: AceResize.Session,
    c: Char,
) {
    val dir = AceResize.dirOf(c)
    if (dir == null) {
        AceResize.cancel(st)
        return
    }
    session.picked?.resize?.invoke(dir)
}

private fun paintLabels(session: AceResize.Session) {
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

private fun paintHold(session: AceResize.Session) {
    session.canvases.forEach { Overlay.detach(it) }
    session.canvases.clear()
    val target = session.picked ?: return
    val layer = target.layer ?: return
    session.canvases += Overlay.badge(layer, listOf(target.rect to AceResize.holdLabel(target.axis)))
}

private fun splitterTargets(
    frame: Window,
    layer: JLayeredPane?,
): List<AceResize.Target> {
    val out = mutableListOf<AceResize.Target>()
    val queue = ArrayDeque<Component>()
    queue.add(frame)
    while (queue.isNotEmpty()) {
        val c = queue.removeFirst()
        if (!c.isVisible) continue
        if (c is Splitter && c.firstComponent != null && c.secondComponent != null) {
            splitterTarget(c, layer)?.let { out.add(it) }
        }
        if (c is Container) queue += c.components
    }
    return out
}

private fun splitterTarget(
    splitter: Splitter,
    layer: JLayeredPane?,
): AceResize.Target? {
    val divider = splitter.divider
    if (!divider.isShowing || divider.width <= 0 || divider.height <= 0) return null
    val axis = if (splitter.isVertical) AceResize.Axis.VERTICAL else AceResize.Axis.HORIZONTAL
    val spot = Rectangle(divider.width / 2, divider.height / 2, 1, 1)
    return resizeTarget(divider, spot, layer, axis) { dir ->
        AceResize.nudge(splitter.isVertical, dir, splitter.proportion, AceResize.STEP)?.let { splitter.proportion = it }
    }
}

private fun toolWindowTargets(
    editor: Editor,
    frame: Window,
    layer: JLayeredPane?,
): List<AceResize.Target> {
    val project = editor.project ?: return emptyList()
    val manager = ToolWindowManager.getInstance(project)
    return manager.toolWindowIds.mapNotNull { id ->
        val toolWindow = manager.getToolWindow(id)?.takeIf { it.isVisible } ?: return@mapNotNull null
        val component = toolWindow.component
        if (!component.isShowing || SwingUtilities.getWindowAncestor(component) !== frame) return@mapNotNull null
        val edge = innerEdge(component.width, component.height, toolWindow.anchor)
        resizeTarget(component, edge, layer, AceResize.Axis.BOTH) { dir ->
            Ide.actOn(component, AceResize.toolWindowAction(dir))
        }
    }
}

private fun innerEdge(
    width: Int,
    height: Int,
    anchor: ToolWindowAnchor,
): Rectangle =
    when (anchor) {
        ToolWindowAnchor.LEFT -> Rectangle(width - 1, height / 2, 1, 1)
        ToolWindowAnchor.RIGHT -> Rectangle(0, height / 2, 1, 1)
        ToolWindowAnchor.TOP -> Rectangle(width / 2, height - 1, 1, 1)
        ToolWindowAnchor.BOTTOM -> Rectangle(width / 2, 0, 1, 1)
        else -> Rectangle(width / 2, height / 2, 1, 1)
    }

private fun resizeTarget(
    source: Component,
    rectInSource: Rectangle,
    layer: JLayeredPane?,
    axis: AceResize.Axis,
    resize: (AceResize.Dir) -> Unit,
): AceResize.Target? {
    if (layer == null || !source.isShowing) return null
    val screen = Rectangle(rectInSource)
    val corner = screen.location
    SwingUtilities.convertPointToScreen(corner, source)
    screen.location = corner
    return AceResize.Target(SwingUtilities.convertRectangle(source, rectInSource, layer), axis, layer, screen, resize)
}
