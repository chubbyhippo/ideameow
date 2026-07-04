#!/usr/bin/env bash
# setup.sh — build and install the ideameow plugin from source.
#
#   ./setup.sh                 build, install plugin + default ~/.ideameowrc
#   ./setup.sh --list          show detected IDE plugin directories and exit
#   ./setup.sh --build-only    just produce build/distributions/ideameow-*.zip
#   ./setup.sh --rc-only       only install the default .ideameowrc
#   ./setup.sh --skip-build    install the already-built zip
#   ./setup.sh --plugins-dir DIR   install into DIR instead of auto-detecting
#   ./setup.sh --force-rc      overwrite an existing .ideameowrc
#
# Detection covers Linux (~/.local/share/JetBrains/<Product><ver> — plugins
# live directly in that directory), macOS (~/Library/Application Support/
# JetBrains/<Product>/plugins) and WSL (/mnt/c/Users/<user>/AppData/Roaming/
# JetBrains/<Product>/plugins). Only IDEs >= 2026.1 are targeted, matching
# the plugin's since-build=261.
#
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

MIN_MAJOR=2026
MIN_MINOR=1

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$here"

do_build=1 do_plugin=1 do_rc=1 force_rc=0 list_only=0
explicit_dir=""

while (($#)); do
    case "$1" in
        --list)        list_only=1 ;;
        --build-only)  do_plugin=0 do_rc=0 ;;
        --rc-only)     do_build=0 do_plugin=0 ;;
        --skip-build)  do_build=0 ;;
        --force-rc)    force_rc=1 ;;
        --plugins-dir) shift; explicit_dir="${1:?--plugins-dir needs a path}" ;;
        -h|--help)     sed -n '2,17p' "$0"; exit 0 ;;
        *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
    shift
done

info()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33mwarn:\033[0m %s\n' "$*" >&2; }

# ---------------------------------------------------------------- detection

ver_ok() { # accepts a directory name like IntelliJIdea2026.1
    local v major minor
    v="$(grep -oE '20[0-9]{2}\.[0-9]+' <<<"$1" | head -1)" || return 1
    [[ -n "$v" ]] || return 1
    major="${v%%.*}" minor="${v#*.}" ; minor="${minor%%.*}"
    (( major > MIN_MAJOR || (major == MIN_MAJOR && minor >= MIN_MINOR) ))
}

detect_plugin_dirs() {
    local d base
    # Linux: plugins live directly under the product directory
    for d in "$HOME"/.local/share/JetBrains/*/; do
        [[ -d "$d" ]] || continue
        base="$(basename "$d")"
        ver_ok "$base" && printf '%s\n' "${d%/}"
    done
    # macOS
    for d in "$HOME/Library/Application Support/JetBrains"/*/; do
        [[ -d "$d" ]] || continue
        base="$(basename "$d")"
        ver_ok "$base" && printf '%s\n' "${d%/}/plugins"
    done
    # WSL -> Windows IDEs
    if grep -qi microsoft /proc/version 2>/dev/null; then
        for d in /mnt/c/Users/*/AppData/Roaming/JetBrains/*/; do
            [[ -d "$d" ]] || continue
            base="$(basename "$d")"
            ver_ok "$base" && printf '%s\n' "${d%/}/plugins"
        done
    fi
}

# Windows user profiles that own a detected IDE (for the Windows-side rc)
detect_windows_homes() {
    grep -qi microsoft /proc/version 2>/dev/null || return 0
    local d
    for d in /mnt/c/Users/*/AppData/Roaming/JetBrains/*/; do
        [[ -d "$d" ]] || continue
        ver_ok "$(basename "$d")" || continue
        printf '%s\n' "$(sed -E 's|(/mnt/c/Users/[^/]+)/.*|\1|' <<<"$d")"
    done | sort -u
}

targets=()
if [[ -n "$explicit_dir" ]]; then
    targets=("$explicit_dir")
else
    while IFS= read -r line; do targets+=("$line"); done < <(detect_plugin_dirs | sort -u)
fi

if ((list_only)); then
    info "detected plugin directories (>= ${MIN_MAJOR}.${MIN_MINOR}):"
    ((${#targets[@]})) && printf '  %s\n' "${targets[@]}" || echo "  (none found)"
    exit 0
fi

# ------------------------------------------------------------------- build

if ((do_build)); then
    info "building the plugin"
    if [[ -x ./gradlew ]]; then ./gradlew -q buildPlugin; else gradle -q buildPlugin; fi
fi

zip=""
if ((do_plugin)); then
    zip="$(ls -1 build/distributions/ideameow-*.zip 2>/dev/null | sort -V | tail -1 || true)"
    [[ -n "$zip" ]] || { echo "no build/distributions/ideameow-*.zip — run without --skip-build" >&2; exit 1; }
    info "installing $(basename "$zip")"
fi

# ----------------------------------------------------------------- install

installed=0
if ((do_plugin)); then
    if ((${#targets[@]} == 0)); then
        warn "no IDE plugin directory (>= ${MIN_MAJOR}.${MIN_MINOR}) detected."
        warn "install manually: Settings > Plugins > Install Plugin from Disk > $zip"
    fi
    for dir in "${targets[@]}"; do
        mkdir -p "$dir"
        rm -rf "$dir/ideameow"
        unzip -q -o "$zip" -d "$dir"
        info "installed into $dir"
        installed=$((installed + 1))
    done
fi

# --------------------------------------------------------------------- rc

install_rc() {
    local dest="$1"
    if [[ -f "$dest" && $force_rc -eq 0 ]]; then
        warn "$dest exists — kept (use --force-rc to overwrite)"
    else
        cp .ideameowrc "$dest"
        info "installed default rc: $dest"
    fi
}

if ((do_rc)); then
    install_rc "$HOME/.ideameowrc"
    while IFS= read -r winhome; do
        [[ -d "$winhome" ]] && install_rc "$winhome/.ideameowrc"
    done < <(detect_windows_homes)
fi

# ------------------------------------------------------------------- done

echo
info "done."
((installed)) && echo "  * restart the IDE(s) to load the plugin"
echo "  * disable IdeaVim if it is enabled — both intercept typing"
echo "  * in the IDE: SPC ? shows the cheatsheet, SPC c v edits ~/.ideameowrc"
