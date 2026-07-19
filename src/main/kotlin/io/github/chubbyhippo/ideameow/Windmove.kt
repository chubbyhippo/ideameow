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

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.IdeFocusManager
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.SwingUtilities

internal object Windmove {
    enum class Dir(
        val emacs: String,
        val horizontal: Boolean,
    ) {
        LEFT("left", true),
        RIGHT("right", true),
        UP("up", false),
        DOWN("down", false),
    }

    fun noWindowMessage(dir: Dir): String = "No window ${dir.emacs} from selected window"

    fun reference(
        dir: Dir,
        current: Rectangle,
        caret: Point?,
    ): Int =
        if (dir.horizontal) {
            if (caret != null && current.contains(caret)) caret.y else current.y + 1
        } else {
            if (caret != null && current.contains(caret)) caret.x else current.x + 1
        }

    fun <T> pick(
        dir: Dir,
        current: Rectangle,
        posn: Int,
        frame: Dimension,
        candidates: List<Pair<T, Rectangle>>,
    ): T? {
        val hor = dir.horizontal
        val first = if (hor) current.x else current.y
        val last = first + if (hor) current.width else current.height
        var bestEdge =
            when (dir) {
                Dir.DOWN -> frame.height
                Dir.RIGHT -> frame.width
                else -> -1
            }
        var bestEdge2 = bestEdge
        var bestDiff2 = if (hor) frame.height else frame.width
        var best: T? = null
        var best2: T? = null
        for ((w, r) in candidates) {
            val lead = if (hor) r.x else r.y
            val size = if (hor) r.width else r.height
            val bandLead = if (hor) r.y else r.x
            val bandSize = if (hor) r.height else r.width
            if (bandLead <= posn && posn < bandLead + bandSize) {
                val inDir =
                    when (dir) {
                        Dir.LEFT, Dir.UP -> lead in (bestEdge + 1)..first
                        Dir.RIGHT -> lead in last..<bestEdge
                        Dir.DOWN -> lead in first..<bestEdge
                    }
                if (inDir) {
                    bestEdge = lead
                    best = w
                }
            } else {
                val strictlyInDir =
                    when (dir) {
                        Dir.LEFT, Dir.UP -> lead + size <= first
                        Dir.RIGHT, Dir.DOWN -> last <= lead
                    }
                if (!strictlyInDir) continue
                val diff2 =
                    if (bandLead > posn) bandLead - posn else posn - bandLead - bandSize
                val better =
                    diff2 < bestDiff2 ||
                        (
                            diff2 == bestDiff2 &&
                                when (dir) {
                                    Dir.LEFT, Dir.UP -> lead > bestEdge2
                                    Dir.RIGHT, Dir.DOWN -> lead < bestEdge2
                                }
                        )
                if (better) {
                    bestEdge2 = lead
                    bestDiff2 = diff2
                    best2 = w
                }
            }
        }
        return best ?: best2
    }

    fun move(
        editor: Editor,
        dir: Dir,
    ) {
        val frame = SwingUtilities.getWindowAncestor(editor.component) ?: return
        val current = rectIn(frame, editor.component) ?: return
        val candidates =
            visibleEditors(editor, frame).mapNotNull { other ->
                rectIn(frame, other.component)?.let { other to it }
            }
        val posn = reference(dir, current, caretPoint(editor, frame))
        val target = pick(dir, current, posn, frame.size, candidates)
        if (target == null) {
            Ide.hint(editor, noWindowMessage(dir))
            return
        }
        IdeFocusManager.getInstance(editor.project).requestFocus(target.contentComponent, true)
    }

    fun swap(
        editor: Editor,
        dir: Dir,
    ) {
        val project = editor.project ?: return
        val frame = SwingUtilities.getWindowAncestor(editor.component) ?: return
        val fem = FileEditorManagerEx.getInstanceEx(project)
        val current = fem.currentWindow ?: return
        val currentRect = rectIn(frame, current) ?: return
        val candidates =
            fem.windows
                .filter { it !== current }
                .mapNotNull { w -> rectIn(frame, w)?.let { w to it } }
        val posn = reference(dir, currentRect, caretPoint(editor, frame))
        val target = pick(dir, currentRect, posn, frame.size, candidates)
        if (target == null || !exchange(fem, current, target)) {
            Ide.hint(editor, noWindowMessage(dir))
        }
    }

    @Suppress("UnstableApiUsage")
    fun exchange(
        fem: FileEditorManagerEx,
        current: EditorWindow,
        target: EditorWindow,
    ): Boolean {
        val mine = current.selectedComposite?.file ?: return false
        val theirs = target.selectedComposite?.file ?: return false
        if (mine != theirs) {
            val options = FileEditorOpenOptions(requestFocus = false)
            fem.openFile(mine, target, options)
            fem.openFile(theirs, current, options)
            current.closeFile(mine)
            target.closeFile(theirs)
        }
        target.setAsCurrentWindow(true)
        return true
    }

    private fun rectIn(
        frame: java.awt.Window,
        window: EditorWindow,
    ): Rectangle? {
        if (window.selectedComposite == null) return null
        return rectIn(frame, window.tabbedPane.component)
    }

    fun rectIn(
        frame: java.awt.Window,
        c: java.awt.Component,
    ): Rectangle? {
        if (!c.isShowing || c.width <= 0 || c.height <= 0) return null
        return SwingUtilities.convertRectangle(c.parent, c.bounds, frame)
    }

    fun visibleEditors(
        editor: Editor,
        frame: java.awt.Window,
    ) = EditorFactory.getInstance().allEditors.filter { other ->
        other !== editor &&
            (other as? EditorEx)?.isOneLineMode != true &&
            other.component.isShowing &&
            SwingUtilities.getWindowAncestor(other.component) === frame &&
            !SwingUtilities.isDescendingFrom(other.component, editor.component) &&
            !SwingUtilities.isDescendingFrom(editor.component, other.component)
    }

    private fun caretPoint(
        editor: Editor,
        frame: java.awt.Window,
    ): Point? {
        val xy = editor.visualPositionToXY(editor.caretModel.visualPosition)
        if (!editor.scrollingModel.visibleArea.contains(xy)) return null
        return SwingUtilities.convertPoint(editor.contentComponent, xy, frame)
    }
}

internal sealed class WindmoveAction(
    private val dir: Windmove.Dir,
) : DumbAwareAction() {
    init {
        isEnabledInModalContext = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        Windmove.move(editor, dir)
    }
}

internal class WindmoveLeftAction : WindmoveAction(Windmove.Dir.LEFT)

internal class WindmoveRightAction : WindmoveAction(Windmove.Dir.RIGHT)

internal class WindmoveUpAction : WindmoveAction(Windmove.Dir.UP)

internal class WindmoveDownAction : WindmoveAction(Windmove.Dir.DOWN)

internal sealed class WindmoveSwapAction(
    private val dir: Windmove.Dir,
) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        Windmove.swap(editor, dir)
    }
}

internal class WindmoveSwapLeftAction : WindmoveSwapAction(Windmove.Dir.LEFT)

internal class WindmoveSwapRightAction : WindmoveSwapAction(Windmove.Dir.RIGHT)

internal class WindmoveSwapUpAction : WindmoveSwapAction(Windmove.Dir.UP)

internal class WindmoveSwapDownAction : WindmoveSwapAction(Windmove.Dir.DOWN)

internal class WindmovePromoter : ActionPromoter {
    override fun promote(
        actions: List<AnAction>,
        context: DataContext,
    ): List<AnAction> = actions.sortedByDescending { it is WindmoveAction }
}
