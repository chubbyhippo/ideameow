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
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

/**
 * Which editors get meow state: main file editors and diff editors always —
 * in NORMAL even when read-only, like Emacs read-only buffers (the modify
 * commands gate themselves; see ModesKeypadSpec); dialog and tool-window
 * fields (EditorKind.UNTYPED, e.g. the commit message box) only when
 * multi-line and writable — mirroring IdeaVim's `ideavimsupport=dialog`.
 *
 * EditorTextField switches on one-line mode only AFTER the editor is created,
 * so the specs set it post-creation exactly like production does, and the
 * listener's deferred decision is flushed with the EDT queue.
 */
class AttachSpec : BasePlatformTestCase() {
    private val factory get() = EditorFactory.getInstance()
    private val created = mutableListOf<Editor>()

    override fun tearDown() {
        try {
            created.forEach { if (!it.isDisposed) factory.releaseEditor(it) }
            created.clear()
        } finally {
            super.tearDown()
        }
    }

    // ------------------------------------------------------------------ DSL

    private fun givenEditor(
        kind: EditorKind,
        oneLine: Boolean = false,
        viewer: Boolean = false,
        writable: Boolean = true,
    ): Editor {
        val doc = factory.createDocument("feat: teach meow the commit box\n\nlonger body")
        doc.setReadOnly(!writable)
        val editor =
            if (viewer) {
                factory.createViewer(doc, project, kind)
            } else {
                factory.createEditor(doc, project, kind)
            }
        (editor as EditorEx).isOneLineMode = oneLine // EditorTextField does this after creation
        created += editor
        return editor
    }

    private fun whenTheFactoryListenerRuns(editor: Editor) {
        editor.putUserData(Meow.KEY, null) // isolate from the plugin-registered listener
        MeowEditorFactoryListener().editorCreated(EditorFactoryEvent(factory, editor))
        UIUtil.dispatchAllInvocationEvents() // flush the deferred UNTYPED decision
    }

    private fun thenMeowIsAttached(
        editor: Editor,
        mode: MeowMode,
    ) {
        val st = Meow.state(editor)
        assertNotNull("expected meow state on the editor", st)
        assertEquals("meow mode", mode, st!!.mode)
        assertTrue("block cursor in a modal state", editor.settings.isBlockCursor)
    }

    private fun thenMeowStaysAway(editor: Editor) = assertNull("expected no meow state on the editor", Meow.state(editor))

    // ---------------------------------------------------------------- specs

    fun `test given a multi-line writable dialog editor like the commit message box then meow attaches in NORMAL`() {
        val editor = givenEditor(EditorKind.UNTYPED)
        whenTheFactoryListenerRuns(editor)
        thenMeowIsAttached(editor, MeowMode.NORMAL)
    }

    fun `test given a commit-message-like editor then normal-mode keys are commands not text`() {
        val editor = givenEditor(EditorKind.UNTYPED)
        whenTheFactoryListenerRuns(editor)
        val before = editor.document.text
        assertTrue("NORMAL mode consumes the key", Engine.handleChar(editor, 'j', null))
        assertEquals("nothing was inserted", before, editor.document.text)
    }

    fun `test given a one-line dialog field then meow stays away`() {
        val editor = givenEditor(EditorKind.UNTYPED, oneLine = true)
        whenTheFactoryListenerRuns(editor)
        thenMeowStaysAway(editor)
    }

    fun `test given a read-only dialog viewer then meow stays away`() {
        val editor = givenEditor(EditorKind.UNTYPED, viewer = true, writable = false)
        whenTheFactoryListenerRuns(editor)
        thenMeowStaysAway(editor)
    }

    fun `test given a console editor then meow stays away`() {
        val editor = givenEditor(EditorKind.CONSOLE)
        whenTheFactoryListenerRuns(editor)
        thenMeowStaysAway(editor)
    }

    fun `test given a writable diff editor then meow attaches in NORMAL`() {
        val editor = givenEditor(EditorKind.DIFF)
        whenTheFactoryListenerRuns(editor)
        thenMeowIsAttached(editor, MeowMode.NORMAL)
    }

    fun `test given a diff revision side (a read-only viewer) then meow attaches in NORMAL`() {
        // like an Emacs read-only buffer: full layout, gated modifications
        val editor = givenEditor(EditorKind.DIFF, viewer = true, writable = false)
        whenTheFactoryListenerRuns(editor)
        thenMeowIsAttached(editor, MeowMode.NORMAL)
    }

    fun `test given a main file editor then meow attaches in NORMAL`() {
        val editor = givenEditor(EditorKind.MAIN_EDITOR)
        whenTheFactoryListenerRuns(editor)
        thenMeowIsAttached(editor, MeowMode.NORMAL)
    }

    fun `test given a read-only main file editor then meow attaches in NORMAL like Emacs read-only buffers`() {
        val editor = givenEditor(EditorKind.MAIN_EDITOR, writable = false)
        whenTheFactoryListenerRuns(editor)
        thenMeowIsAttached(editor, MeowMode.NORMAL)
    }
}
