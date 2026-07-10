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

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/*
 * The IdeaVim-style rc reload: a small floating "Reload" button in the
 * top-right of the ~/.ideameowrc editor that lights up when the buffer's
 * PARSED config differs from what the engine has loaded — comment and
 * formatting edits don't count — and reloads in place. A port of IdeaVim's
 * ui/ReloadVimRc.kt (VimRcFileState + ReloadVimRc + ReloadFloatingToolbar;
 * JetBrains/ideavim, source-read 2026-07-10). SPC c M stays the keyboard
 * path; both funnel through [RcReload.perform].
 */

/** Snapshot of the last-LOADED rc, as a hash of the parsed config (IdeaVim
 *  hashes the parsed Script for the same reason: comment-only edits must not
 *  demand a reload), plus a Document modificationStamp fast path. */
internal object RcFileState {
    @Volatile
    private var state: Int? = null

    @Volatile
    private var modificationStamp = 0L
    private val listeners = mutableListOf<() -> Unit>()

    private fun hash(c: Rc.Config): Int =
        listOf(c.normal, c.motion, c.keypad, c.keypadDesc, c.whichKey, c.whichKeyDelayMs).hashCode()

    /** Called by [Rc.load] with whatever it just parsed. */
    fun saveParsed(c: Rc.Config) {
        state = hash(c)
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it() }
    }

    fun loaded(): Boolean = state != null

    /** Does DOCUMENT parse to the same config the engine is running? */
    fun equalTo(document: Document): Boolean {
        val stamp = document.modificationStamp
        if (stamp == modificationStamp) return true
        val same = hash(Rc.parse(document.charsSequence.toString().lines())) == state
        if (same) modificationStamp = stamp
        return same
    }

    fun whenSaved(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun resetForTest() {
        state = null
        modificationStamp = 0L
    }
}

/** The one reload routine, shared by SPC c M and the floating button. */
internal object RcReload {
    fun perform() {
        flushUnsavedRc()
        Rc.load()
        TreeMeow.refresh() // a focused tree picks the new mmap keys up now
        val c = Rc.config
        Rc.notify(
            "Reloaded ~/${Rc.FILE_NAME}: ${c.normal.size} normal map(s), " +
                "${c.motion.size} motion map(s), " +
                "${c.keypad.size} keypad map(s), ${c.keypadDesc.size} description(s)" +
                if (c.errors.isEmpty()) "" else ", ${c.errors.size} problem(s)",
            NotificationType.INFORMATION,
        )
    }

    /** The rc is usually edited right here in the IDE (SPC c m), and the
     *  platform flushes Documents to disk LAZILY — reloading straight from
     *  disk would re-read the stale file and look dead until a restart
     *  happens to save everything. IdeaVim's ReloadVimRc guards the same
     *  way: saveDocumentAsIs before re-executing (ui/ReloadVimRc.kt). */
    fun flushUnsavedRc() {
        val vf = LocalFileSystem.getInstance().findFileByIoFile(Rc.rcFile()) ?: return
        val fdm = FileDocumentManager.getInstance()
        val doc = fdm.getCachedDocument(vf) ?: return
        if (fdm.isDocumentUnsaved(doc)) fdm.saveDocumentAsIs(doc)
    }
}

/** The floating-toolbar action: only on the rc file's editor, icon and text
 *  flip between "no changes" and "reload" from the parse-hash comparison. */
internal class ReloadRcFloatingAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(PlatformDataKeys.EDITOR)
        val vf = e.getData(PlatformDataKeys.VIRTUAL_FILE)
        if (editor == null || vf == null || !FileUtil.filesEqual(File(vf.path), Rc.rcFile())) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val same = RcFileState.loaded() && RcFileState.equalTo(editor.document)
        e.presentation.icon = if (same) AllIcons.Actions.Checked else AllIcons.Actions.BuildLoadChanges
        e.presentation.text = if (same) "No Changes in ~/${Rc.FILE_NAME}" else "Reload ~/${Rc.FILE_NAME}"
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) = RcReload.perform()
}

/** plugin.xml binds the floating toolbar to this group. */
internal class ReloadRcFloatingActionGroup : DefaultActionGroup() {
    companion object {
        const val ID = "Ideameow.ReloadRcFloating.group"
    }
}

/** Shows the toolbar over the rc editor; re-shows whenever a load lands so
 *  the "no changes" state repaints (IdeaVim's ReloadFloatingToolbar). */
internal class RcReloadFloatingToolbar : AbstractFloatingToolbarProvider(ReloadRcFloatingActionGroup.ID) {
    override val autoHideable: Boolean = false

    override fun register(
        dataContext: DataContext,
        component: FloatingToolbarComponent,
        parentDisposable: Disposable,
    ) {
        super.register(dataContext, component, parentDisposable)
        val show = { component.scheduleShow() }
        RcFileState.whenSaved(show)
        Disposer.register(parentDisposable) { RcFileState.removeListener(show) }
    }
}
