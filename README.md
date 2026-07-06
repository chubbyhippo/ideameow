# ideameow — meow modal editing for IntelliJ

If you love [meow](https://github.com/meow-edit/meow) in Emacs and sigh every
time you open IntelliJ, this plugin is for you. It implements meow's suggested
**QWERTY layout** as a native modal editing engine — no IdeaVim underneath, no
vim emulation in the middle. Just meow: select first, then act.

(Do disable IdeaVim while this is enabled — both plugins intercept typing, and
they will fight.)

## What you get

The states you know from meow:

- **NORMAL** — keys are commands, block cursor. You start here.
- **INSERT** — keys type text. `i a c I A` get you in, `ESC` gets you out.
- **MOTION** — meow's reduced state for special contexts (`j`/`k`/`SPC` by
  default, rebindable with `mmap`). Read-only editors do *not* use it: like
  read-only buffers in Emacs they stay in NORMAL — every motion, selection,
  search and avy jump works, and the modify commands are simply inert
  (meow's `meow--allow-modify-p`). Nothing enters MOTION by default.
- **KEYPAD** — `SPC` as the leader, dispatching IDE actions Emacs-style
  (`SPC x f` = open file, `SPC w v` = split…). A which-key popup lists your
  options whenever you pause on a prefix.
- **BEACON** — meow's multi-edit, built on IntelliJ's native multiple carets:
  grab a region with `G`, select something inside it, and a caret lands on
  every similar range. Edit them all at once; `ESC` collapses.

The status bar always tells you which state you're in.

Meow runs in main file editors, in diff views (the editable side gets full
NORMAL editing; the read-only revision side gets the same full layout with
edits blocked — navigate, select, search, avy-jump), and in multi-line
writable dialog fields such as the VCS commit message box (like IdeaVim's
`ideavimsupport=dialog`). One-line fields and consoles keep native editing,
and `ESC` in a diff still closes it when there is nothing meow-related to
cancel.

And one idea borrowed straight from meow itself: **the plugin binds no keys in
code.** The entire keymap — the NORMAL/MOTION layout *and* the whole `SPC`
keypad table — lives in an `.ideameowrc` file bundled inside the plugin, and a
`~/.ideameowrc` in your home directory overrides it entry by entry. Rebind
anything; relayout everything.

## Build & install

```bash
cd ideameow
./setup.sh                  # build + install into every detected 2026.1+ IDE
                            # (Linux, macOS, and Windows IDEs from WSL) and
                            # install the default ~/.ideameowrc
./setup.sh --list           # just show which IDE dirs it would target
gradle buildPlugin          # or: gradle runIde  (sandbox IDE for a test drive)
```

Prefer clicking? *Settings → Plugins → ⚙ → Install Plugin from Disk…* and pick
`build/distributions/ideameow-0.1.0.zip`.

You'll need a JDK 21 toolchain (`mise.toml` pins it; `setup.sh` handles the
rest). The plugin targets IDE 2026.1 and anything newer.

## The layout

This is meow's suggested QWERTY layout (`KEYBINDING_QWERTY`), verified against
meow's source — not reconstructed from vim habits. The bundled `.ideameowrc`
spells it out as one `nmap <key> <meow-command>` line per key, so the file
doubles as the authoritative reference; what follows is the guided tour.

**Moving and selecting.** `h j k l` move (a char-selection survives movement,
any other selection is cancelled), and `H J K L` extend a char selection.
`w`/`W` mark the word/symbol at point — and push it to the search ring, which
is why `n` finds the next occurrence right afterwards. `e`/`E` and `b`/`B` go
to the next/previous word or symbol, and after a `w` they *extend* the
selection instead of replacing it (meow's `(expand . word)` rule). `x` selects
the line — repeat it or press digits to take more lines. `Q`/`X` go to a line,
`f`/`t` find/till a character, `o`/`O` select the enclosing block / to its
end, `m` selects the join region, and `,` `.` `[` `]` select inner/bounds/
begin/end of a *thing* (`r` round, `s` square, `c` curly, `g` string, `e`
symbol, `w` window, `b` buffer, `p` paragraph, `l` line, `v` visual line, `d`
defun, `.` sentence). `;` reverses the selection, `z` pops back to the
previous one, `v` visits a regexp, `n` continues the search (backward when the
selection is reversed). Digits expand the selection by N units — little
painted hints show you where each digit lands (`0` = 10) — or act as a count
when nothing is selected. `-` is the negative argument.

**Editing.** `i`/`a` insert at the selection's start/end, `I`/`A` open a line
above/below, `c` change, `s` kill (cut), `d`/`D` delete forward/backward, `y`
save (copy), `p` yank (paste), `r` replace the selection with the clipboard,
`u` undo, `'` repeats the last command — counts and all, so `'` after `2fa`
finds the second `a` again. `g` cancels, `q` closes the tab, `ESC` always
brings you back to NORMAL.

**Grab and beacon.** `G` grabs the selection (you'll see it highlighted).
While a grab is active, any selection you make inside it — `w`, `x`, `f`… —
drops a caret on every similar range: change them all, then `ESC`. `R`
swap-grab exchanges the selection and grab texts; `Y` sync-grab re-stashes.

**Keypad.** `SPC b/x/c/m/w …` mirror the Emacs/meow keypad of the companion
`.ideavimrc`/`init.el` (GotoFile, SaveAll, splits, font size…). `SPC 1-9` is a
digit argument, `SPC ?` opens the cheatsheet, `SPC /` describes a key, and
`SPC c m` / `SPC c M` edit / reload your config.

## ~/.ideameowrc — configuring everything

ideameow reads an `.ideavimrc`-style file from your home directory:
`~/.ideameowrc` on Linux/macOS, `C:\Users\<you>\.ideameowrc` on Windows.

**Getting started is two steps:**

1. Press `SPC c m` in the IDE — it creates and opens the file for you. (The
   bundled defaults stay underneath, so an empty file changes nothing and a
   one-line file changes exactly one thing. Or copy the repo's `.ideameowrc`
   over it and edit anything.)
2. Edit, then reload with `SPC c M`. A balloon tells you how many mappings
   loaded — and lists any parse problems with their line numbers.

**Syntax reference**

| Line | Meaning |
|---|---|
| `" text` or `# text` | comment (also at the end of a line: `nmap S <action>(X) " jump`) |
| `nmap <key> <meow-command>` | bind a NORMAL key to a named meow command, e.g. `nmap n meow-mark-word` — this is how you remap the layout itself |
| `nmap <key> <action>(ActionId)` | NORMAL key runs an IDE action |
| `nmap <key> <keys>` | NORMAL key replays a meow key sequence, e.g. `nmap Z ,b` |
| `nnoremap` / `noremap` | like `nmap`/`map`, but the replayed keys resolve through the bundled defaults, ignoring your other mappings |
| `mmap` / `mnoremap` | the same three target forms, for MOTION mode (unused by default — read-only editors stay in NORMAL) |
| `map <leader><seq> <action>(Id)` | keypad entry: `SPC` + sequence runs the action (yours override the bundled defaults) |
| `map <leader><seq> <keys>` | keypad entry replaying meow keys after the keypad closes |
| `desc <leader><seq> <text>` | which-key label for an entry (exact seq) or a group (prefix) |
| `let g:WhichKeyDesc_x = "<leader>x text"` | same as `desc` — paste `.ideavimrc` lines unchanged |
| `set timeoutlen=300` | which-key popup delay in milliseconds (the bundled default sets 300) |
| `set which-key` / `set nowhich-key` | popup on/off (default on) |

Key notation: plain printable characters, plus `<Space>` and `<lt>`. Find an
action's id via *Tools → Internal Actions* or IdeaVim's `:actionlist` —
they're the same ids `.ideavimrc` uses in `<action>(...)`.

**Relayouting (Dvorak, Colemak, …).** The layout section of the bundled
`.ideameowrc` IS the default keymap — an `nmap`/`mmap` line per key, exactly
like a `meow-normal-define-key` block in Emacs. The command names are meow's
own (`meow-next-word`, `meow-kill`, …) plus `repeat` and `ignore`, so that
section doubles as the full command reference. A right-hand side that names a
known command binds it; `ignore` disables a key; a misspelled `meow-*` name is
reported as an error; anything else is replayed as keys. A key you don't
mention keeps its bundled binding.

**A few semantics worth knowing:**

- Mapped keys work with `'` (repeat), and key-replay mappings are
  recursion-guarded — a self-referencing map stops at depth 8 with a hint
  instead of freezing your IDE.
- `repeat` is itself a bindable command, so even `'` can be reassigned.
- Reserved: keypad `0-9` (digit argument), `?` (cheatsheet), `/` (describe
  key); `SPC` is always the keypad key. Only printable keys reach the modal
  engine — `<CR>`, `<Esc>`, and modifier chords belong on the IDE keymap.
- Unknown `set` options and `let` lines are ignored, so pasting your whole
  `.ideavimrc` won't error; only the lines ideameow understands take effect.

**which-key.** Pause on any pending prefix — a keypad `SPC` sequence, or the
`,` `.` `[` `]` thing table — and after `timeoutlen` ms a panel appears along
the bottom of the editor listing the continuations in columns, exactly like
Emacs' which-key. It never takes focus and never interrupts: just keep typing
the sequence; `ESC` cancels through the editor as usual, and deeper prefixes
in the same chain refresh the panel instantly. Terminal entries show their
`desc` (falling back to the action id), groups show the group's `desc`
(falling back to `+more`).

**What the bundled default gives you.** The full meow QWERTY layout, the
complete keypad table, and a 1:1 port of the companion `.ideavimrc` leader
scheme: the IntelliJ groups (`SPC .` settings, `SPC a` tool windows,
`SPC d/e/f/g/h/i/j/k/l/n/o/p/q/r/s/t/u/v` …) with which-key labels, `S`/`Q`
as the avy jumps from `init.el` (a native port of avy — no plugin needed:
type chars, pause, hit a label; `Q` labels visible lines and digits switch to
a line-number prompt), split resizing on
`=` `_` `+`, and `SPC ]`/`SPC [` for next/prev change, diff, and error. The
file's footer lists what deliberately *isn't* ported, with reasons. Two
divergences to know about: `-` keeps meow's negative-argument (this engine has
real negative counts, so it doesn't need vim's split-resize workaround), and
since a later line for the same key wins, `Q` ends up on the avy line jump —
put `nmap Q meow-goto-line` in your home rc if you want meow's own binding
back (`X` has it regardless).

## Known deviations from meow

All deliberate, none accidental:

- `U` (meow-undo-in-selection) falls back to plain undo — IntelliJ's undo
  stack cannot be scoped to a region.
- Beacon uses native multiple carets instead of kmacro recording.
- The avy jumps (`S`/`Q`, `SPC c j`) are a native port of avy 0.5.0's
  goto-char-timer and goto-line: same keys (`a s d f g h j k l`), same
  label tree, same timeout flow — scoped to the current editor's visible
  area instead of all windows, with no DEL/RET editing during input (the
  0.25 s pause ends it).
- Block/string/defun "things" use a text scan (same-line strings skipped) plus
  a PSI heuristic for defun — close to, but not literally, Emacs' syntax-ppss.
- The kill-ring is the system clipboard (`meow-use-clipboard` behavior);
  `kill-line` does not append consecutive kills.
- Read-only editors (file viewers, the diff revision side) stay in NORMAL
  with modifications gated like meow's `meow--allow-modify-p`: kill / change /
  backspace / replace are silently inert, delete / yank / open / swap-grab
  answer "Buffer is read-only". `i`/`a` still switch to INSERT (as in Emacs)
  but typing is refused by the platform. MOTION exists for `mmap` setups but
  nothing attaches to it by default; IDE tool windows keep their own keys
  (the commit message box is the exception — it gets full meow editing).

## Hacking on it

The code keeps one rule from meow: commands are data. Every command registers
under its meow name, and keys only ever resolve through rc bindings.

| Where | What |
|---|---|
| `Engine.kt` | the dispatcher: key → binding → command; repeat (`'`) and rc-replay bookkeeping; the `COMMANDS` registry |
| `Motions.kt` | movement and the selections it creates: hjkl, words, lines, find/till |
| `Selections.kt` | the selection primitive (meow's expand/select model), reverse/cancel/pop, digit expand |
| `Search.kt` | meow-search / meow-visit and the shared regexp ring |
| `Structures.kt` | the char-thing table dispatch, blocks, join |
| `Grab.kt` | grab / swap / sync and the beacon (multi-caret) reaction |
| `Edits.kt` | everything that mutates text: insert/change/delete/kill/yank/… |
| `Things.kt` | what a "thing" is: pairs, strings, paragraphs, defuns… |
| `Rc.kt` / `RcParser.kt` | the two rc layers (bundled defaults + `~/.ideameowrc`) and the line syntax |
| `Keypad.kt` / `WhichKey.kt` / `ExpandHints.kt` | the SPC leader, the popup, the digit hints |
| `MeowTypedHandler` / `MeowEscapeHandler` / `MeowEditorFactoryListener` | the three platform hooks: raw typing, escape, editor attach |

Behavior is pinned by the BDD specs in `src/test` (given/whenKeys/then…) —
every assertion there was cross-checked against meow's source, so treat a red
spec as "you changed meow's semantics", not "update the test". Run them with
`gradle test` (platform fixtures; the first run downloads the IDE, and on WSL
`/mnt/c` expect several minutes).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) for the full text.
