# ideameow

[meow](https://github.com/meow-edit/meow)-style modal editing for IntelliJ —
meow's suggested **QWERTY layout** as a native modal engine, with no IdeaVim
and no vim emulation underneath. Select first, then act.

Disable IdeaVim while this is enabled — both plugins intercept typing.

## States

| State | What |
|---|---|
| **NORMAL** | keys are commands, block cursor; you start here |
| **INSERT** | keys type text — `i a c I A` enter, `ESC` leaves |
| **MOTION** | meow's reduced state, rebindable with `mmap`; what answers to it is tool-window trees |
| **KEYPAD** | `SPC` as the leader, dispatching IDE actions Emacs-style (`SPC x f` open file, `SPC w v` split); which-key lists the options on a pause |
| **BEACON** | meow's multi-edit on IntelliJ's native multiple carets — grab with `G`, select inside it, a caret lands on every similar range; `ESC` collapses |

The status bar always shows the current state.

## Where meow attaches

| Surface | Behavior |
|---|---|
| Main file editors | full NORMAL editing |
| Diff views | editable side full NORMAL; read-only revision side full layout with edits blocked |
| Multi-line writable dialog fields (VCS commit message) | full meow editing — IdeaVim's `ideavimsupport=dialog` |
| One-line fields, consoles | native editing |
| Read-only editors | stay in NORMAL, not MOTION — motions, selection, search and avy work; modify commands are inert (meow's `meow--allow-modify-p`) |
| `ESC` in a diff | still closes it when there is nothing meow-related to cancel |

### Tool-window trees

The project view, structure, TODO, find results and every other tree answer to
the MOTION map.

| Key | Does |
|---|---|
| `j` / `k` | move the selection |
| `h` | collapse, or go to the parent |
| `l` | expand, or enter |
| `q` | hide the tool window |
| `Enter`, any unmapped letter | native — speed search still starts |

| rc line | Effect |
|---|---|
| `mmap o <action>(EditSource)` | add your own tree key |
| `mmap r <action>(SynchronizeCurrentFile)` | same |
| `mmap q ignore` | give `q` back to the tree, so it types into speed search |

Meow commands other than the four motions have no tree meaning and are inert
there.

### Double-ESC leaves any tool window

| Press | In | Result |
|---|---|---|
| `ESC` | most tool windows | the platform's own escape returns you to the editor |
| `ESC` ×2 within 500 ms | the same tool window | focus jumps back to the editor |
| the first of the two | terminal, AI-chat windows | still reaches the terminal untouched |

## Windows

`(windmove-default-keybindings)` from `init.el`, ported natively.

| Key | Does |
|---|---|
| `Shift+←→↑↓` | select the editor window in that direction, measured from the caret like windmove |
| `SPC w h/j/k/l` | the same four actions, mirroring init.el's `C-c w` window map |
| `SPC w H/J/K/L` | `windmove-swap-states` — push your file and the focus into the neighbouring split, bringing its file back (editor splits only; a diff pane cannot be swapped) |

| Fact | Value |
|---|---|
| What counts as a window | every visible editor — so the same keys cross the two sides of a diff, enter consoles, and reach the commit message box |
| Wrap-around | none |
| At the edge | "No window left from selected window", as in Emacs |
| Where the chords live | the IDE keymap — modifier chords never reach the modal engine; rebind under *Settings → Keymap → Windmove* |
| Tradeoff | they shadow shift-selection in editors, exactly as the Emacs binding does; trees and dialogs keep native shift-selection |

### Ace-window

| Key | Does |
|---|---|
| `SPC w w`, `SPC x o` | ace-window: three or more windows each get a home-row label (`a s d f g h j k l`, avy's style, multi-char past nine) at the top-left and the next key jumps there; exactly two hops straight across, like `other-window`; `Esc` cancels |
| `SPC w W` | `ace-swap-window` — pick a label and it exchanges files with yours, focus following the swap (editor splits only; a diff pane hints instead) |

ace-window's extra dispatch keys (`x`, `m`, `c`…) are not ported — the keypad's
`w` group already covers splitting, deleting and swapping.

## Emacs chords

| Behavior | Value |
|---|---|
| Bound to | the real Emacs point motions, not meow commands |
| With no selection | the chord moves the caret |
| With one active | it extends it, anchored exactly like meow's own `H J K L` expand — `w` then `Ctrl+f Ctrl+f` grows the marked word one character at a time |
| `;` (reverse) | flips which end subsequent chords grow from |

| Chord | Command |
|---|---|
| `Ctrl+f` / `Ctrl+b` | `forward/backward-char` |
| `Ctrl+n` / `Ctrl+p` | `next/previous-line` |
| `Ctrl+a` / `Ctrl+e` | `move-beginning/end-of-line` |
| `Alt+f` / `Alt+b` | `forward/backward-word` |
| `Alt+a` / `Alt+e` | `backward/forward-sentence` |
| `Alt+Shift+,` / `Alt+Shift+.` | `beginning/end-of-buffer` (`M-<` / `M->`) — a count lands N/10 of the way in, snapping to the next line start |
| `Alt+Shift+[` / `Alt+Shift+]` | `backward/forward-paragraph` (`M-{` / `M-}`) — blank-line-delimited; forward lands on the separator line, backward on the paragraph start with one adjacent empty line joining it |
| `Alt+u` / `Alt+l` / `Alt+c` | `upcase/downcase/capitalize-word` — from the caret through the word's end; `-` then the chord reaches back without moving the caret |
| `Alt+d` | `kill-word` into the clipboard; a negative count kills backward |

| Chord | Why not bound |
|---|---|
| `Alt+n` / `Alt+p` | stock Emacs has no default binding either — only the unrelated `M-g n` / `M-g p` prefix |
| `Ctrl+v`, `Ctrl+o`, `Ctrl+l`, `Alt+Backspace`, `Alt+q` … | the IDE default matters more |
| `Alt+/` | needs no port — the IDE's own `Alt+/` is HippieCompletion, named after the hippie-expand it implements |

| Fact | Value |
|---|---|
| Where they live | the IDE keymap — rebind under *Settings → Keymap* |
| Active in | NORMAL; they yield to the IDE's own chords in INSERT |
| Displacement | the four `Alt+letter` chords and the `Alt+Shift+[` / `]` pair are unbound in the IDE default keymap; `Alt+Shift+,` / `.` shadow font-size zoom in NORMAL only, which keeps `Ctrl+wheel`, `SPC w` and INSERT |

## No keys in code

| Layer | What |
|---|---|
| Bundled `.ideameowrc` | the entire keymap — the NORMAL/MOTION layout *and* the whole `SPC` keypad table |
| `~/.ideameowrc` | overrides it entry by entry |

## Build & install

```bash
cd ideameow
./setup.sh                  # build + install into every detected 2026.1+ IDE
                            # (Linux, macOS, and Windows IDEs from WSL) and
                            # install the default ~/.ideameowrc
./setup.sh --list           # just show which IDE dirs it would target
gradle buildPlugin          # or: gradle runIde  (sandbox IDE for a test drive)
```

| Item | Value |
|---|---|
| By hand | *Settings → Plugins → ⚙ → Install Plugin from Disk…*, pick `build/distributions/ideameow-0.1.0.zip` |
| Toolchain | JDK 21, pinned in `mise.toml` |
| Target | IDE 2026.1 and newer |

## The layout

| Item | Value |
|---|---|
| Layout | meow's suggested QWERTY layout (`KEYBINDING_QWERTY`), verified against meow's source |
| Authoritative reference | the bundled `.ideameowrc` — one `nmap <key> <meow-command>` line per key |

### Moving and selecting

| Key | Does |
|---|---|
| `h j k l` | move — a char-selection survives, any other selection is cancelled |
| `H J K L` | extend a char selection |
| `w` / `W` | mark the word / symbol at point, and push it to the search ring, so `n` finds the next occurrence |
| `e` / `E`, `b` / `B` | next / previous word or symbol; after a `w` they extend rather than replace (meow's `(expand . word)` rule) |
| `x` | select the line — repeat or press digits to take more |
| `Q` / `X` | go to a line |
| `f` / `t` | find / till a character |
| `o` / `O` | select the enclosing block / to its end |
| `m` | select the join region |
| `,` `.` `[` `]` | inner / bounds / begin / end of a *thing* |
| `;` | reverse the selection |
| `z` | pop back to the previous selection |
| `v` | visit a regexp |
| `n` | continue the search — backward when the selection is reversed |
| `1`-`9`, `0` | expand by N units (`0` = 10), painted hints showing where each digit lands; a count when nothing is selected |
| `-` | negative argument |

| Thing | Char |
|---|---|
| round / square / curly / tag | `r` (or `(` / `)`) / `s` (or `[` / `]`) / `c` (or `{` / `}`) / `t` |
| string / slash / question | `g` (or `'` / `"`) / `/` / `?` |
| symbol / window / buffer | `e` / `w` / `b` |
| paragraph / line / visual line | `p` / `l` / `v` |
| defun / sentence | `d` / `.` |

### Editing

| Key | Does |
|---|---|
| `i` / `a` | insert at the selection's start / end |
| `I` / `A` | open a line above / below |
| `c` | change |
| `s` | kill (cut) |
| `d` / `D` | delete forward / backward |
| `y` | save (copy) |
| `p` | yank (paste) |
| `r` | replace the selection with the clipboard |
| `u` | undo |
| `'` | repeat the last command, counts and all — `'` after `2fa` finds the second `a` again |
| `g` | cancel |
| `q` | close the tab |
| `ESC` | back to NORMAL |

### Grab and beacon

| Key | Does |
|---|---|
| `G` | grab the selection (highlighted) |
| any selection inside a grab | drops a caret on every similar range — change them all, then `ESC` |
| `R` | swap-grab: exchange the selection and grab texts |
| `Y` | sync-grab: re-stash |

### Keypad

| Sequence | Does |
|---|---|
| `SPC x/c/m/w …` | the Emacs/meow keypad of the companion `.ideavimrc`/`init.el` — GotoFile, SaveAll, splits, font size… |
| `SPC b` | bookmarks — `m` set, `0-9` numbered slots, `j` jump, `b` recent files |
| `SPC 1-9` | digit argument |
| `SPC ?` | the cheatsheet |
| `SPC /` | describe a key |
| `SPC c m` / `SPC c M` | edit / reload your config |

## ~/.ideameowrc

| Item | Value |
|---|---|
| Path | `~/.ideameowrc` on Linux/macOS, `C:\Users\<you>\.ideameowrc` on Windows |
| Format | `.ideavimrc`-style |
| Precedence | the bundled defaults stay underneath; overrides apply entry by entry, so deleting a line falls back to the default |
| Disable a key | bind it to `ignore` |

| Step | Do |
|---|---|
| 1 | `SPC c m` — the first press creates `~/.ideameowrc` as a full copy of the bundled defaults and opens it |
| 2 | Edit, then `SPC c M`, or the floating **Reload** button in the rc editor's top-right |

| Reload detail | Value |
|---|---|
| When the button appears | whenever the file's content differs from the loaded config — comparison is on the parsed config, IdeaVim-style, so comment and formatting edits do not count |
| Unsaved edits | flushed for you |
| Feedback | a balloon with the mapping count, and any parse problems with their line numbers |

### Syntax reference

| Line | Meaning |
|---|---|
| `" text` or `# text` | comment (also at the end of a line: `nmap S <action>(X) " jump`) |
| `nmap <key> <meow-command>` | bind a NORMAL key to a named meow command, e.g. `nmap n meow-mark-word` — this is how you remap the layout itself |
| `nmap <key> <action>(ActionId)` | NORMAL key runs an IDE action |
| `nmap <key> <keys>` | NORMAL key replays a meow key sequence, e.g. `nmap Z ,b` |
| `nnoremap` / `noremap` | like `nmap`/`map`, but the replayed keys resolve through the bundled defaults, ignoring your other mappings |
| `mmap` / `mnoremap` | the same three target forms, for MOTION mode — the keymap of tool-window trees (read-only editors stay in NORMAL) |
| `map <leader><seq> <action>(Id)` | keypad entry: `SPC` + sequence runs the action (yours override the bundled defaults) |
| `map <leader><seq> <keys>` | keypad entry replaying meow keys after the keypad closes |
| `desc <leader><seq> <text>` | which-key label for an entry (exact seq) or a group (prefix) |
| `let g:WhichKeyDesc_x = "<leader>x text"` | same as `desc` — paste `.ideavimrc` lines unchanged |
| `set timeoutlen=300` | which-key popup delay in milliseconds (the bundled default sets 300) |
| `set which-key` / `set nowhich-key` | popup on/off (default on) |
| `set overlay-color=#2ECC71` | background of the avy / ace-window / ace-click jump labels (`#RRGGBB`, applied to both light and dark themes) |
| `set overlay-text-color=#ffffff` | the jump-label text color |
| `set expand-hint-color=#d05c0a` | the `0`–`9` expand-hint color (theme-split by default) |
| `set grab-color=#c0f0cd` | the grab / beacon highlight color (theme-split by default) |

| Item | Value |
|---|---|
| Key notation | plain printable characters, plus `<Space>` and `<lt>` |
| Finding an action id | `SPC i d` toggles action-id tracking — ideameow's port of IdeaVim's *Track Action IDs*: every action you perform pops a balloon with its id and a *Copy Action Id* button; `SPC i d` again (or the balloon's *Stop Tracking*) turns it off. Same ids `.ideavimrc` uses in `<action>(...)` |

### Relayouting (Dvorak, Colemak, …)

The layout section of the bundled `.ideameowrc` IS the default keymap — an
`nmap`/`mmap` line per key, like a `meow-normal-define-key` block in Emacs.

| Right-hand side | Effect |
|---|---|
| a known command name | binds it — meow's own names (`meow-next-word`, `meow-kill`, …) plus `repeat` and `ignore` |
| `ignore` | disables the key |
| a misspelled `meow-*` name | reported as an error |
| anything else | replayed as keys |
| a key you do not mention | keeps its bundled binding |

### Semantics worth knowing

| Fact | Value |
|---|---|
| Repeat | mapped keys work with `'`; key-replay mappings are recursion-guarded — a self-referencing map stops at depth 8 with a hint |
| `repeat` | itself a bindable command, so even `'` can be reassigned |
| Reserved | keypad `0-9` (digit argument), `?` (cheatsheet), `/` (describe key); `SPC` is always the keypad key |
| Reach | only printable keys reach the modal engine — `<CR>`, `<Esc>` and modifier chords belong on the IDE keymap |
| Unknown `set` / `let` lines | ignored, so pasting a whole `.ideavimrc` will not error |

### which-key

| Fact | Value |
|---|---|
| Trigger | pause on any pending prefix — a keypad `SPC` sequence, or the `,` `.` `[` `]` thing table — for `timeoutlen` ms |
| Appearance | a panel along the bottom of the editor listing the continuations in columns, like Emacs' which-key |
| Focus | never takes it, never interrupts — keep typing the sequence |
| Deeper prefixes | refresh the panel instantly |
| Terminal entries | show their `desc`, falling back to the action id |
| Groups | show the group's `desc`, falling back to `+more` |

### What the bundled default gives you

| Item | Value |
|---|---|
| Layout | the full meow QWERTY layout and the complete keypad table |
| Leader scheme | a 1:1 port of the companion `.ideavimrc` — `SPC ;` settings, `SPC a` tool windows, `SPC d/e/f/g/h/i/j/k/l/n/o/p/q/r/s/t/u/v` …, with which-key labels |
| `S` / `Q` | the avy jumps from `init.el` — a native port, no plugin needed: type chars, pause, hit a label; `Q` labels visible lines and digits switch to a line-number prompt |
| Windmove | `SPC w h/j/k/l`, plus `Shift+arrows` on the IDE keymap |
| Split resizing | `=` `_` `+` |
| `SPC .` / `SPC ,` | next/prev change, diff, and error |
| The rc footer | lists what deliberately is not ported, with reasons |

| Divergence | Detail |
|---|---|
| `-` | keeps meow's negative argument — this engine has real negative counts, so it does not need vim's split-resize workaround |
| `Q` | a later line for the same key wins, so `Q` ends up on the avy line jump; `nmap Q meow-goto-line` in your home rc restores meow's binding (`X` has it regardless) |

## Known deviations from meow

All deliberate, none accidental.

| Deviation | Detail |
|---|---|
| `U` (meow-undo-in-selection) | falls back to plain undo — IntelliJ's undo stack cannot be scoped to a region |
| Beacon | native multiple carets instead of kmacro recording |
| The avy jumps (`S` / `Q`, `SPC c j`) | a native port of avy 0.5.0's goto-char-timer and goto-line — same keys, same label tree, same timeout flow, but scoped to the current editor's visible area and with no DEL/RET editing during input (the 0.25 s pause ends it) |
| Block/string/defun "things" | a text scan (same-line strings skipped) plus a PSI heuristic for defun — close to, but not literally, Emacs' syntax-ppss |
| The kill-ring | the system clipboard (`meow-use-clipboard` behavior); `kill-line` does not append consecutive kills |
| Read-only editors | stay in NORMAL with modification gated like `meow--allow-modify-p` — kill / change / backspace / replace silently inert; delete / yank / open / swap-grab answer "Buffer is read-only"; `i`/`a` still enter INSERT but typing is refused by the platform |
| MOTION | no *editor* attaches to it by default; tool-window trees answer to the `mmap` map, and the commit message box gets full meow editing |

## Hacking on it

Commands are data: every command registers under its meow name, and keys only
ever resolve through rc bindings.

| Where | What |
|---|---|
| `Engine.kt` | the dispatcher: key → binding → command; repeat (`'`) and rc-replay bookkeeping; the `COMMANDS` registry |
| `Motions.kt` | movement and the selections it creates: hjkl, words, lines, find/till, plus the Ctrl/Alt Emacs motion chords (IDE-keymap actions, region-expanding) |
| `Selections.kt` | the selection primitive (meow's expand/select model), reverse/cancel/pop, digit expand |
| `Search.kt` | meow-search / meow-visit and the shared regexp ring |
| `Structures.kt` | the char-thing table dispatch, blocks, join |
| `Grab.kt` | grab / swap / sync and the beacon (multi-caret) reaction |
| `Edits.kt` | everything that mutates text: insert/change/delete/kill/yank/… |
| `Things.kt` | what a "thing" is: pairs, strings, paragraphs, defuns… |
| `Rc.kt` / `RcParser.kt` | the two rc layers (bundled defaults + `~/.ideameowrc`) and the line syntax |
| `Keypad.kt` / `WhichKey.kt` / `ExpandHints.kt` | the SPC leader, the popup, the digit hints |
| `MeowTypedHandler` / `MeowEscapeHandler` / `MeowEditorFactoryListener` | the three platform hooks: raw typing, escape, editor attach |

| Item | Value |
|---|---|
| Specs | `src/test`, given/whenKeys/then…, every assertion cross-checked against meow's source |
| A red spec means | "you changed meow's semantics", not "update the test" |
| Run | `gradle test` — platform fixtures; the first run downloads the IDE, and on WSL `/mnt/c` expect several minutes |

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) for the full text.
