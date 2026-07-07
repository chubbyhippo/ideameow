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

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/** Reload ~/.ideameowrc (keypad: SPC c M). */
class ReloadRcAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        Rc.load()
        val c = Rc.config
        Rc.notify(
            "Reloaded ~/${Rc.FILE_NAME}: ${c.normal.size} normal map(s), " +
                "${c.motion.size} motion map(s), " +
                "${c.keypad.size} keypad map(s), ${c.keypadDesc.size} description(s)" +
                if (c.errors.isEmpty()) "" else ", ${c.errors.size} problem(s)",
            NotificationType.INFORMATION
        )
    }
}

/** Open (creating if needed) ~/.ideameowrc in the editor (keypad: SPC c m). */
class EditRcAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val f = Rc.rcFile()
        seedIfMissing(f)
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f) ?: return
        OpenFileDescriptor(project, vf).navigate(true)
    }

    companion object {
        /** A first ~/.ideameowrc starts as a full copy of the bundled
         *  defaults — the complete layout and keypad table, ready to edit —
         *  never touching an existing file. */
        fun seedIfMissing(f: File) {
            if (f.exists()) return
            f.writeText(
                Rc.defaultsText()
                // a missing bundled rc is a plugin bug (Rc reports it);
                // leave a minimal self-describing file so SPC c m still works
                    ?: (
                        "\" ~/${Rc.FILE_NAME} — ideameow configuration\n" +
                            "\" the bundled defaults (full meow layout + keypad table) stay\n" +
                            "\" underneath — lines here override them entry by entry, e.g.:\n" +
                            "\" nmap Q meow-goto-line\n"
                        )
            )
        }
    }
}
