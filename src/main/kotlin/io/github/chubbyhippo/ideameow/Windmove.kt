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
        position: Int,
        frame: Dimension,
        candidates: List<Pair<T, Rectangle>>,
    ): T? {
        val inBand = candidates.filter { rectInBand(dir, it.second, position) }
        val outBand = candidates.filterNot { rectInBand(dir, it.second, position) }
        return pickAligned(dir, current, frame, inBand) ?: pickByBand(dir, current, position, frame, outBand)
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
        val position = reference(dir, current, caretPoint(editor, frame))
        val target = pick(dir, current, position, frame.size, candidates)
        if (target == null) {
            Ide.hint(editor, noWindowMessage(dir))
        } else {
            IdeFocusManager.getInstance(editor.project).requestFocus(target.contentComponent, true)
        }
    }

    fun swap(
        editor: Editor,
        dir: Dir,
    ) {
        val project = editor.project
        val frame = SwingUtilities.getWindowAncestor(editor.component)
        if (project == null || frame == null) return
        val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
        val current = fileEditorManager.currentWindow
        val currentRect = current?.let { rectIn(frame, it) }
        if (current == null || currentRect == null) return
        val candidates =
            fileEditorManager.windows
                .filter { it !== current }
                .mapNotNull { window -> rectIn(frame, window)?.let { window to it } }
        val position = reference(dir, currentRect, caretPoint(editor, frame))
        val target = pick(dir, currentRect, position, frame.size, candidates)
        if (target == null || !exchange(fileEditorManager, current, target)) {
            Ide.hint(editor, noWindowMessage(dir))
        }
    }

    @Suppress("UnstableApiUsage")
    fun exchange(
        fileEditorManager: FileEditorManagerEx,
        current: EditorWindow,
        target: EditorWindow,
    ): Boolean {
        val currentFile = current.selectedComposite?.file
        val targetFile = target.selectedComposite?.file
        if (currentFile == null || targetFile == null) return false
        if (currentFile != targetFile) {
            val options = FileEditorOpenOptions(requestFocus = false)
            fileEditorManager.openFile(currentFile, target, options)
            fileEditorManager.openFile(targetFile, current, options)
            current.closeFile(currentFile)
            target.closeFile(targetFile)
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
        component: java.awt.Component,
    ): Rectangle? {
        if (!component.isShowing || component.width <= 0 || component.height <= 0) return null
        return SwingUtilities.convertRectangle(component.parent, component.bounds, frame)
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

private fun initialEdge(
    dir: Windmove.Dir,
    frame: Dimension,
): Int =
    when (dir) {
        Windmove.Dir.DOWN -> frame.height
        Windmove.Dir.RIGHT -> frame.width
        else -> -1
    }

private fun rectInBand(
    dir: Windmove.Dir,
    rect: Rectangle,
    position: Int,
): Boolean {
    val bandLead = if (dir.horizontal) rect.y else rect.x
    val bandSize = if (dir.horizontal) rect.height else rect.width
    return bandLead <= position && position < bandLead + bandSize
}

private fun <T> pickAligned(
    dir: Windmove.Dir,
    current: Rectangle,
    frame: Dimension,
    candidates: List<Pair<T, Rectangle>>,
): T? {
    val horizontal = dir.horizontal
    val first = if (horizontal) current.x else current.y
    val last = first + if (horizontal) current.width else current.height
    var bestEdge = initialEdge(dir, frame)
    var best: T? = null
    for ((window, rect) in candidates) {
        val lead = if (horizontal) rect.x else rect.y
        if (alignedInDir(dir, lead, first, last, bestEdge)) {
            bestEdge = lead
            best = window
        }
    }
    return best
}

private fun <T> pickByBand(
    dir: Windmove.Dir,
    current: Rectangle,
    position: Int,
    frame: Dimension,
    candidates: List<Pair<T, Rectangle>>,
): T? {
    val horizontal = dir.horizontal
    val first = if (horizontal) current.x else current.y
    val last = first + if (horizontal) current.width else current.height
    var bestBandEdge = initialEdge(dir, frame)
    var bestBandDiff = if (horizontal) frame.height else frame.width
    var bestBand: T? = null
    for ((window, rect) in candidates) {
        val lead = if (horizontal) rect.x else rect.y
        val size = if (horizontal) rect.width else rect.height
        val bandLead = if (horizontal) rect.y else rect.x
        val bandSize = if (horizontal) rect.height else rect.width
        if (!strictlyBeyond(dir, lead, size, first, last)) continue
        val bandDiff = if (bandLead > position) bandLead - position else position - bandLead - bandSize
        if (bandCloser(dir, lead, bandDiff, bestBandDiff, bestBandEdge)) {
            bestBandEdge = lead
            bestBandDiff = bandDiff
            bestBand = window
        }
    }
    return bestBand
}

private fun alignedInDir(
    dir: Windmove.Dir,
    lead: Int,
    first: Int,
    last: Int,
    edge: Int,
): Boolean =
    when (dir) {
        Windmove.Dir.LEFT, Windmove.Dir.UP -> lead in (edge + 1)..first
        Windmove.Dir.RIGHT -> lead in last..<edge
        Windmove.Dir.DOWN -> lead in first..<edge
    }

private fun strictlyBeyond(
    dir: Windmove.Dir,
    lead: Int,
    size: Int,
    first: Int,
    last: Int,
): Boolean =
    when (dir) {
        Windmove.Dir.LEFT, Windmove.Dir.UP -> lead + size <= first
        Windmove.Dir.RIGHT, Windmove.Dir.DOWN -> last <= lead
    }

private fun bandCloser(
    dir: Windmove.Dir,
    lead: Int,
    bandDiff: Int,
    bestBandDiff: Int,
    bestBandEdge: Int,
): Boolean =
    bandDiff < bestBandDiff ||
        (
            bandDiff == bestBandDiff &&
                when (dir) {
                    Windmove.Dir.LEFT, Windmove.Dir.UP -> lead > bestBandEdge
                    Windmove.Dir.RIGHT, Windmove.Dir.DOWN -> lead < bestBandEdge
                }
        )

internal sealed class WindmoveAction(
    private val dir: Windmove.Dir,
) : DumbAwareAction() {
    init {
        isEnabledInModalContext = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
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

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
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
