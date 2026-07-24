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
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

internal object RcFileState {
    @Volatile
    private var state: Int? = null

    @Volatile
    private var modificationStamp = 0L
    private val listeners = mutableListOf<() -> Unit>()

    private fun hash(config: Rc.Config): Int =
        listOf(
            config.normal,
            config.motion,
            config.keypad,
            config.keypadDesc,
            config.repeat,
            config.chords,
            config.whichKey,
            config.whichKeyDelayMs,
            config.overlayColor,
            config.overlayTextColor,
            config.expandHintColor,
            config.grabColor,
        ).hashCode()

    fun saveParsed(config: Rc.Config) {
        state = hash(config)
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it() }
    }

    fun loaded(): Boolean = state != null

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

internal object RcReload {
    fun perform() {
        flushUnsavedRc()
        Rc.load()
        TreeMeow.refresh()
        val config = Rc.config
        Rc.notify(
            "Reloaded ~/${Rc.FILE_NAME}: ${config.normal.size} normal map(s), " +
                "${config.motion.size} motion map(s), " +
                "${config.keypad.size} keypad map(s), ${config.keypadDesc.size} description(s), " +
                "${config.repeat.size} repeat group(s)" +
                if (config.errors.isEmpty()) "" else ", ${config.errors.size} problem(s)",
            NotificationType.INFORMATION,
        )
    }

    fun flushUnsavedRc() {
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(Rc.rcFile()) ?: return
        val fileDocumentManager = FileDocumentManager.getInstance()
        val document = fileDocumentManager.getCachedDocument(virtualFile) ?: return
        if (fileDocumentManager.isDocumentUnsaved(document)) fileDocumentManager.saveDocumentAsIs(document)
    }
}

internal class ReloadRcFloatingAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(PlatformDataKeys.EDITOR)
        val virtualFile = event.getData(PlatformDataKeys.VIRTUAL_FILE)
        if (editor == null || virtualFile == null || File(virtualFile.path) != Rc.rcFile()) {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val same = RcFileState.loaded() && RcFileState.equalTo(editor.document)
        event.presentation.icon = if (same) AllIcons.Actions.Checked else AllIcons.Actions.BuildLoadChanges
        event.presentation.text = if (same) "No Changes in ~/${Rc.FILE_NAME}" else "Reload ~/${Rc.FILE_NAME}"
        event.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) = RcReload.perform()
}

internal class ReloadRcFloatingActionGroup : DefaultActionGroup() {
    companion object {
        const val ID = "Ideameow.ReloadRcFloating.group"
    }
}

internal class RcReloadFloatingToolbar : AbstractFloatingToolbarProvider(ReloadRcFloatingActionGroup.ID) {
    override val autoHideable: Boolean = false

    override fun register(
        dataContext: DataContext,
        component: FloatingToolbarComponent,
        parentDisposable: Disposable,
    ) {
        val showToolbar = { component.scheduleShow() }
        RcFileState.whenSaved(showToolbar)
        Disposer.register(parentDisposable) { RcFileState.removeListener(showToolbar) }
    }
}
