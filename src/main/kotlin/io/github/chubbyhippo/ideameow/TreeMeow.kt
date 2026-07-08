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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JTree
import javax.swing.KeyStroke

/**
 * Meow for tool-window trees — MOTION state ported to the one surface
 * IntelliJ navigates without an editor. Like special buffers in Emacs, a
 * tree (project view, structure, TODO, find results, ...) answers to the
 * MOTION map: the `mmap` lines of the rc. Motion commands translate to the
 * tree's own arrow-key vocabulary, `<action>(...)` bindings dispatch with
 * the tree as context, and every key the map does NOT bind keeps its native
 * meaning — Enter still opens, unmapped letters still start speed search.
 *
 * Mechanism (ported from IdeaVim's NERDTree / NerdTreeEverywhere, read from
 * JetBrains/ideavim master 2026-07): one [DumbAwareAction] whose
 * CustomShortcutSet is exactly the mmap-bound keys, registered on whichever
 * JTree owns focus — a KeyboardFocusManager "focusOwner" listener registers
 * and unregisters as focus moves, so the shortcuts exist nowhere else. While
 * speed search is open, [PlatformDataKeys.SPEED_SEARCH_TEXT] is non-null and
 * update() disables the whole action: typing into the search always wins.
 */
object TreeMeow {
    /**
     * The meow motion commands with a native tree meaning — the four arrows.
     * Values are JTree's BasicTreeUI ActionMap keys, the exact handlers the
     * real arrow keys invoke (IdeaVim's NERDTree navigates through the same
     * map: it survives separator skipping, cycle scrolling, loading nodes).
     * Every other meow command needs a text buffer and is simply inert here.
     */
    private val SWING_MOTIONS =
        mapOf(
            "meow-next" to "selectNext",
            "meow-prev" to "selectPrevious",
            "meow-left" to "selectParent", // collapse, else go to the parent
            "meow-right" to "selectChild", // expand, else go to the first child
        )

    /** Every char the MOTION map binds (defaults + ~/.ideameowrc) — the
     *  tree shortcut set. Anything else never reaches the dispatcher; a key
     *  whose effective binding is `ignore` is excluded too, which is how a
     *  home rc returns a default key to the tree (native speed search). */
    fun boundChars(): Set<Char> =
        (Rc.defaults().motion.keys + Rc.cfg().motion.keys).filterTo(mutableSetOf()) { c ->
            (Rc.cfg().motion[c] ?: Rc.defaults().motion[c])?.command != "ignore"
        }

    /** Resolve one key against the MOTION map and run it on [tree] —
     *  the tree-surface analog of Engine.handleChar + runBinding, with the
     *  same layering (user maps unless inside a noremap replay, then the
     *  bundled defaults) and the same replay depth guard. */
    fun dispatch(
        tree: JTree,
        c: Char,
        noremap: Boolean = false,
        depth: Int = 0,
    ) {
        val b = (if (noremap) null else Rc.cfg().motion[c]) ?: Rc.defaults().motion[c] ?: return
        val command = b.command
        if (command != null) {
            SWING_MOTIONS[command]?.let { swing(tree, it) }
            return
        }
        val actionId = b.action
        if (actionId != null) {
            Ide.actOn(tree, actionId)
            return
        }
        val keys = b.keys ?: return
        if (depth >= 8) return
        for (k in keys) dispatch(tree, k, noremap || !b.recursive, depth + 1)
    }

    private fun swing(
        tree: JTree,
        name: String,
    ) {
        tree.actionMap
            .get(name)
            ?.actionPerformed(ActionEvent(tree, ActionEvent.ACTION_PERFORMED, name))
    }

    // -------------------------------------------------- focus-scoped wiring

    private val dispatcher =
        object : DumbAwareAction() {
            init {
                // settings-dialog trees and friends live in modal contexts
                templatePresentation.isEnabledInModalContext = true
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled =
                    e.getData(PlatformDataKeys.SPEED_SEARCH_TEXT) == null &&
                    e.getData(PlatformDataKeys.CONTEXT_COMPONENT) is JTree
            }

            override fun actionPerformed(e: AnActionEvent) {
                val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JTree ?: return
                val c = (e.inputEvent as? KeyEvent)?.keyChar ?: return
                if (c == KeyEvent.CHAR_UNDEFINED) return
                dispatch(tree, c)
            }
        }

    private val installed = AtomicBoolean()

    private val focusListener =
        PropertyChangeListener { evt ->
            (evt.oldValue as? JTree)?.let { dispatcher.unregisterCustomShortcutSet(it) }
            (evt.newValue as? JTree)?.let { register(it) }
        }

    /** Install the app-wide focus hook once; every JTree gaining focus gets
     *  the current mmap keys as component shortcuts, and loses them again
     *  with focus (registration is per-component, so nothing leaks). */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .addPropertyChangeListener("focusOwner", focusListener)
    }

    /** Undo [install] — the KeyboardFocusManager is JVM-global, so the hook
     *  must not outlive the plugin (dynamic unload would otherwise leak the
     *  plugin classloader through the captured dispatcher). */
    fun uninstall() {
        if (!installed.compareAndSet(true, false)) return
        val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        kfm.removePropertyChangeListener("focusOwner", focusListener)
        (kfm.focusOwner as? JTree)?.let { dispatcher.unregisterCustomShortcutSet(it) }
    }

    /** Re-read the mmap keys for the currently focused tree — called after
     *  SPC c M so a reloaded ~/.ideameowrc applies without refocusing. */
    fun refresh() {
        (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JTree)
            ?.let { register(it) }
    }

    private fun register(tree: JTree) {
        dispatcher.unregisterCustomShortcutSet(tree)
        val shortcuts =
            boundChars()
                .map { KeyboardShortcut(KeyStroke.getKeyStroke(it), null) }
        dispatcher.registerCustomShortcutSet(CustomShortcutSet(*shortcuts.toTypedArray()), tree)
    }
}

/** App-level lifetime anchor: created on first project open, disposed on
 *  dynamic plugin unload — which is what balances the JVM-global focus hook
 *  (light service, no plugin.xml entry). */
@Service(Service.Level.APP)
internal class TreeMeowLifecycle : Disposable {
    init {
        TreeMeow.install()
    }

    override fun dispose() = TreeMeow.uninstall()
}

/** Installs the tree-surface focus hook once any project opens. */
internal class TreeMeowStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().getService(TreeMeowLifecycle::class.java)
    }
}
