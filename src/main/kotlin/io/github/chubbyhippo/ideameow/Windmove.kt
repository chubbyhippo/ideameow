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

/**
 * windmove for the IDE — a native port of Emacs' windmove-left/right/up/down
 * (windmove.el and window.el's `window-in-direction`, Emacs 30.2, read AND
 * batch-probed 2026-07): select the window in a direction, as seen from the
 * caret. The "windows" here are every visible text editor in the frame —
 * editor splits AND both sides of a side-by-side diff (which IdeaVim's C-w
 * navigation never reaches: it walks EditorWindow splitters only), consoles,
 * the commit box. Like Emacs windows, they are just rectangles.
 *
 * The pick is window-in-direction's two-tier scan, ported verbatim:
 * tier 1 — windows whose span covers the caret's row (for left/right;
 * column for up/down): the nearest facing edge wins; tier 2 — windows fully
 * in the direction but outside the caret band: smallest distance to the
 * band, ties broken toward the nearer edge. The caret is the reference
 * exactly like `window-point` (with three stacked windows on the left,
 * S-left enters the one at the caret's height); a caret scrolled off-screen
 * falls back to edge+1 (window.el's nil posn-at-point path). No wrap-around
 * (`windmove-wrap-around` defaults to nil) and no window in the direction
 * reports meow-style what Emacs signals: "No window left from selected
 * window" (message batch-verified).
 *
 * The default shortcuts are `(windmove-default-keybindings)`'s: plain
 * Shift+arrows, registered on the $default keymap in plugin.xml — modifier
 * chords never reach the modal engine, so unlike everything else these are
 * NOT rc lines (rebind them in Settings > Keymap). They deliberately shadow
 * the editor's shift-selection, the exact tradeoff the Emacs binding makes;
 * [WindmovePromoter] is what wins the conflict. SPC w h/j/k/l dispatch the
 * same actions from the rc, mirroring init.el's C-c w map.
 */
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

    /** windmove-do-window-select's user-error, verbatim. */
    fun noWindowMessage(dir: Dir): String = "No window ${dir.emacs} from selected window"

    /**
     * window.el's reference coordinate: the caret's frame row (left/right)
     * or column (up/down) — `posn-at-point` — falling back to the window
     * edge + 1 when the caret is not visible (the `(or ... 1)` path).
     * [caret] is null or a frame-relative point checked against [current].
     */
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

    /**
     * `window-in-direction` over rectangles in frame coordinates, verbatim
     * (window.el, Emacs 30.2) — including its asymmetries: tier-1 candidates
     * toward LEFT/UP compare their near edge against the current window's
     * NEAR edge (`w-left <= first`), toward RIGHT against the FAR edge
     * (`w-left >= last`), toward DOWN against the near edge again
     * (`w-top >= first`). [candidates] must not contain the current window;
     * [posn] comes from [reference]. Returns null when Emacs would.
     */
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
            val lead = if (hor) r.x else r.y // w-left / w-top
            val size = if (hor) r.width else r.height
            val bandLead = if (hor) r.y else r.x // the caret-band axis
            val bandSize = if (hor) r.height else r.width
            if (bandLead <= posn && posn < bandLead + bandSize) {
                // W is in the direction and covers POSN: nearest edge wins
                val inDir =
                    when (dir) {
                        Dir.LEFT, Dir.UP -> lead <= first && lead > bestEdge
                        Dir.RIGHT -> lead >= last && lead < bestEdge
                        Dir.DOWN -> lead >= first && lead < bestEdge
                    }
                if (inDir) {
                    bestEdge = lead
                    best = w
                }
            } else {
                // W is in the direction but does not cover POSN
                val strictlyInDir =
                    when (dir) {
                        Dir.LEFT, Dir.UP -> lead + size <= first
                        Dir.RIGHT, Dir.DOWN -> last <= lead
                    }
                if (!strictlyInDir) continue
                val diff2 = // window--in-direction-2: distance from posn to the band
                    if (bandLead > posn) bandLead - posn else posn - bandLead - bandSize
                val better =
                    diff2 < bestDiff2 || (
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

    // ------------------------------------------------------ the IDE windows

    /** Select the window in [dir] from [editor]'s caret, Emacs-style; hints
     *  the windmove user-error when there is none. */
    fun move(
        editor: Editor,
        dir: Dir,
    ) {
        val frame = SwingUtilities.getWindowAncestor(editor.component) ?: return
        val current = rectIn(frame, editor) ?: return
        val candidates =
            visibleEditors(editor, frame).mapNotNull { other ->
                rectIn(frame, other)?.let { other to it }
            }
        val posn = reference(dir, current, caretPoint(editor, frame))
        val target = pick(dir, current, posn, frame.size, candidates)
        if (target == null) {
            Ide.hint(editor, noWindowMessage(dir))
            return
        }
        IdeFocusManager.getInstance(editor.project).requestFocus(target.contentComponent, true)
    }

    /** windmove-swap-states-left/down/up/right (windmove.el, Emacs 30.2 —
     *  source-read AND batch-probed): the current window's buffer and the
     *  FOCUS both travel to the window in [dir]; the displaced buffer lands
     *  in the origin window (window-swap-states carries the selected flag
     *  with the state). The swappable windows here are the editor splits —
     *  the only surface whose content can be exchanged; diff panes and
     *  consoles are fixed in place. The pick and the no-window user-error
     *  are exactly [move]'s (windmove-swap-states-in-direction reuses both). */
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
        val mine = current.selectedComposite?.file
        val theirs = target?.selectedComposite?.file
        if (target == null || mine == null || theirs == null) {
            Ide.hint(editor, noWindowMessage(dir))
            return
        }
        if (mine != theirs) {
            // add each file to its new window BEFORE closing it in the old
            // one — a single-tab window would otherwise collapse mid-swap
            val options = FileEditorOpenOptions(requestFocus = false)
            fem.openFile(mine, target, options)
            fem.openFile(theirs, current, options)
            current.closeFile(mine)
            target.closeFile(theirs)
        }
        target.setAsCurrentWindow(true)
    }

    /** An editor split as a windmove window: the tab container's rect in
     *  frame coordinates (IdeaVim's getSplitRectangle uses the same
     *  component), skipping empty windows. */
    private fun rectIn(
        frame: java.awt.Window,
        window: EditorWindow,
    ): Rectangle? {
        if (window.selectedComposite == null) return null
        return componentRect(frame, window.tabbedPane.component)
    }

    /** The component's rect in frame coordinates when actually visible. */
    private fun componentRect(
        frame: java.awt.Window,
        c: java.awt.Component,
    ): Rectangle? {
        if (!c.isShowing || c.width <= 0 || c.height <= 0) return null
        return SwingUtilities.convertRectangle(c.parent, c.bounds, frame)
    }

    /** Every other visible text editor in the same frame — splits, diff
     *  sides, consoles, the commit box. One-liners (rename fields, search
     *  bars) are not windows, and neither editor may nest inside the other
     *  (embedded fragment editors in rendered docs; Emacs windows can't
     *  nest, so windmove must never enter one). */
    private fun visibleEditors(
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

    /** The editor's outer component (gutter included, like an Emacs window
     *  with its fringes) in frame coordinates. NOT contentComponent — that
     *  is the unbounded canvas inside the scroll pane. */
    private fun rectIn(
        frame: java.awt.Window,
        editor: Editor,
    ): Rectangle? = componentRect(frame, editor.component)

    /** The caret in frame coordinates when scrolled into view, else null
     *  (reference() then applies window.el's edge+1 fallback). */
    private fun caretPoint(
        editor: Editor,
        frame: java.awt.Window,
    ): Point? {
        val xy = editor.visualPositionToXY(editor.caretModel.visualPosition)
        if (!editor.scrollingModel.visibleArea.contains(xy)) return null
        return SwingUtilities.convertPoint(editor.contentComponent, xy, frame)
    }
}

/** The four windmove commands; plugin.xml gives them the Shift+arrow
 *  $default shortcuts. Enabled only on editors: a focused tree keeps its
 *  native shift-selection. Modal contexts included — diffs open in dialogs
 *  (commit, ...) and windmove between their panes must work there. */
internal sealed class WindmoveAction(
    private val dir: Windmove.Dir,
) : DumbAwareAction() {
    init {
        templatePresentation.isEnabledInModalContext = true
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

/** The four windmove-swap-states commands — SPC w H/J/K/L only, like
 *  init.el's C-c w map (windmove-swap-states-default-keybindings is never
 *  called there, so no chords here either). */
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

/** Shift+arrows already mean "extend selection" in editors
 *  (EditorLeftWithSelection and friends): promoting windmove first is what
 *  makes the shortcut win, exactly the tradeoff
 *  `(windmove-default-keybindings)` makes in Emacs, where shift-select
 *  loses too. The actions are editor-gated, so nothing else changes. */
internal class WindmovePromoter : ActionPromoter {
    override fun promote(
        actions: MutableList<out AnAction>,
        context: DataContext,
    ): MutableList<AnAction> = actions.sortedByDescending { it is WindmoveAction }.toMutableList()
}
