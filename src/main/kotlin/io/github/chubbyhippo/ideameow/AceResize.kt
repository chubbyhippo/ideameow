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
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.awt.Container
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
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
        val center: () -> Point? = { Point(rect.x + rect.width / 2, rect.y + rect.height / 2) },
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
            Axis.HORIZONTAL -> "← →"
            Axis.VERTICAL -> "↓ ↑"
            Axis.BOTH -> "←→↓↑"
        }

    fun toolWindowAction(dir: Dir): String =
        when (dir) {
            Dir.LEFT -> "ResizeToolWindowLeft"
            Dir.RIGHT -> "ResizeToolWindowRight"
            Dir.DOWN -> "ResizeToolWindowDown"
            Dir.UP -> "ResizeToolWindowUp"
        }

    fun accepts(
        axis: Axis,
        dir: Dir,
    ): Boolean =
        when (axis) {
            Axis.HORIZONTAL -> dir == Dir.LEFT || dir == Dir.RIGHT
            Axis.VERTICAL -> dir == Dir.UP || dir == Dir.DOWN
            Axis.BOTH -> true
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

    fun holdArrow(
        st: MeowState,
        dir: Dir,
    ): Boolean {
        val session = st.aceResize?.takeIf { it.phase == Phase.HOLD } ?: return false
        val target = session.picked
        if (target != null && accepts(target.axis, dir)) {
            target.resize(dir)
            session.canvases.forEach { it.repaint() }
        }
        return true
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
    val target = session.picked
    if (dir == null || target == null) {
        AceResize.cancel(st)
        return
    }
    if (AceResize.accepts(target.axis, dir)) {
        target.resize(dir)
        session.canvases.forEach { it.repaint() }
    }
}

private fun paintLabels(session: AceResize.Session) {
    session.canvases.forEach { Overlay.detach(it) }
    session.canvases.clear()
    val node = session.node ?: return
    Avy
        .labels(node)
        .mapNotNull { (index, label) -> session.targets.getOrNull(index)?.let { it to label } }
        .groupBy { (target, _) -> target.layer }
        .forEach { (layer, entries) ->
            if (layer != null) {
                val badges = ResizeBadges(entries.map { (target, label) -> target.center to label })
                session.canvases += Overlay.layerCanvas(layer, badges)
            }
        }
}

private fun paintHold(session: AceResize.Session) {
    session.canvases.forEach { Overlay.detach(it) }
    session.canvases.clear()
    val target = session.picked ?: return
    val layer = target.layer ?: return
    val badges = ResizeBadges(listOf(target.center to AceResize.holdLabel(target.axis)))
    session.canvases += Overlay.layerCanvas(layer, badges)
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
    val first = splitter.firstComponent?.takeIf { splitter.isShowing } ?: return null
    val axis = if (splitter.isVertical) AceResize.Axis.VERTICAL else AceResize.Axis.HORIZONTAL
    val center = {
        if (splitter.isShowing && layer != null) {
            val spot =
                if (splitter.isVertical) {
                    Rectangle(0, first.y + first.height, splitter.width, 1)
                } else {
                    Rectangle(first.x + first.width, 0, 1, splitter.height)
                }
            val r = SwingUtilities.convertRectangle(splitter, spot, layer)
            Point(r.x + r.width / 2, r.y + r.height / 2)
        } else {
            null
        }
    }
    return resizeTarget(layer, axis, center) { dir ->
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
    val area = FileEditorManagerEx.getInstanceEx(project).splitters
    return manager.toolWindowIds.mapNotNull { id ->
        val toolWindow = manager.getToolWindow(id)?.takeIf { it.isVisible } ?: return@mapNotNull null
        val component = toolWindow.component
        if (!component.isShowing || SwingUtilities.getWindowAncestor(component) !== frame) return@mapNotNull null
        val anchor = toolWindow.anchor
        val axis =
            when (anchor) {
                ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT -> AceResize.Axis.HORIZONTAL
                ToolWindowAnchor.TOP, ToolWindowAnchor.BOTTOM -> AceResize.Axis.VERTICAL
                else -> AceResize.Axis.BOTH
            }
        resizeTarget(layer, axis, { toolWindowBoundary(area, component, anchor, layer) }) { dir ->
            Ide.actOn(component, AceResize.toolWindowAction(dir))
        }
    }
}

private fun toolWindowBoundary(
    area: JComponent,
    toolWindow: Component,
    anchor: ToolWindowAnchor,
    layer: JLayeredPane?,
): Point? {
    if (layer == null || !area.isShowing || !toolWindow.isShowing) return null
    val areaRect = SwingUtilities.convertRectangle(area, Rectangle(0, 0, area.width, area.height), layer)
    val tw = SwingUtilities.convertRectangle(toolWindow, Rectangle(0, 0, toolWindow.width, toolWindow.height), layer)
    return when (anchor) {
        ToolWindowAnchor.LEFT -> Point(areaRect.x, tw.y + tw.height / 2)
        ToolWindowAnchor.RIGHT -> Point(areaRect.x + areaRect.width, tw.y + tw.height / 2)
        ToolWindowAnchor.TOP -> Point(tw.x + tw.width / 2, areaRect.y)
        ToolWindowAnchor.BOTTOM -> Point(tw.x + tw.width / 2, areaRect.y + areaRect.height)
        else -> Point(tw.x + tw.width / 2, tw.y + tw.height / 2)
    }
}

private fun resizeTarget(
    layer: JLayeredPane?,
    axis: AceResize.Axis,
    center: () -> Point?,
    resize: (AceResize.Dir) -> Unit,
): AceResize.Target? =
    layer?.let { pane ->
        center()?.let { here ->
            val screen = Point(here)
            SwingUtilities.convertPointToScreen(screen, pane)
            AceResize.Target(
                Rectangle(here.x, here.y, 0, 0),
                axis,
                pane,
                Rectangle(screen.x, screen.y, 0, 0),
                center,
                resize,
            )
        }
    }

private class ResizeBadges(
    private val entries: List<Pair<() -> Point?, String>>,
) : JComponent() {
    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = false

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.font = g2.font.deriveFont(Font.BOLD)
        val metrics = g2.fontMetrics
        for ((center, label) in entries) {
            val p = center() ?: continue
            val width = metrics.stringWidth(label) + Overlay.LABEL_PADDING
            val height = metrics.height
            val x = p.x - width / 2
            val y = p.y - height / 2
            g2.color = Avy.LEAD_BG
            g2.fillRect(x, y, width, height)
            g2.color = Avy.LEAD_FG
            g2.drawString(label, x + 1, y + metrics.ascent)
        }
    }
}
