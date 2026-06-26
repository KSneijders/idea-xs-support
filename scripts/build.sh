#!/usr/bin/env bash
#
# Build the plugin zip locally (for testing or installing from disk).
#
#   ./scripts/build.sh           # build with the dev version
#   ./scripts/build.sh 1.2.3     # build with an explicit version
#
# Releases are cut by pushing a `vX.Y.Z` tag -- GitHub Actions then builds every
# platform and publishes the release. This script is only for local builds.
#
# Requires a Rust toolchain (cargo). Uses JAVA_HOME/java if set, otherwise
# auto-detects a JetBrains JBR.
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root

version_arg="${1:-}"

# --- Make sure a JDK is available --------------------------------------------
# Prefer JAVA_HOME / java on PATH; otherwise fall back to a JetBrains JBR
# (so you don't have to set JAVA_HOME by hand on a typical dev machine).
find_jbr() {
  local d
  shopt -s nullglob
  local candidates=(
    "/c/Program Files/JetBrains/IntelliJ IDEA"*/jbr
    "/c/Program Files/JetBrains/"*/jbr
    "$HOME/AppData/Local/JetBrains/Toolbox/apps/"*/*/jbr
    "$HOME/AppData/Local/Programs/"*/jbr
  )
  shopt -u nullglob
  for d in "${candidates[@]}"; do
    if [[ -x "$d/bin/java" || -e "$d/bin/java.exe" ]]; then
      echo "$d"; return 0
    fi
  done
  return 1
}

ensure_java() {
  if [[ -n "${JAVA_HOME:-}" && ( -x "$JAVA_HOME/bin/java" || -e "$JAVA_HOME/bin/java.exe" ) ]]; then
    return
  fi
  if command -v java >/dev/null 2>&1; then
    return
  fi
  local jbr
  jbr="$(find_jbr || true)"
  if [[ -n "$jbr" ]]; then
    export JAVA_HOME="$jbr"
    echo "==> Auto-detected JDK (JetBrains JBR): $JAVA_HOME"
  else
    echo "ERROR: No JDK found. Install JDK 21 or set JAVA_HOME." >&2
    exit 1
  fi
}
ensure_java

# --- Keep the local xs-check patches applied (idempotent) --------------------
apply_patches() {
  shopt -s nullglob
  for patch in patches/*.patch; do
    if git -C xs-check apply --reverse --check "../$patch" 2>/dev/null; then
      : # already applied
    elif git -C xs-check apply --check "../$patch" 2>/dev/null; then
      git -C xs-check apply "../$patch"; echo "==> Applied patch: $patch"
    else
      echo "==> WARNING: patch no longer applies (upstream may have fixed it): $patch" >&2
    fi
  done
  shopt -u nullglob
}
apply_patches

# --- Build -------------------------------------------------------------------
echo "==> Building plugin..."
if [[ -n "$version_arg" ]]; then
  ./gradlew buildPlugin --console=plain -PpluginVersion="$version_arg"
else
  ./gradlew buildPlugin --console=plain
fi

zip="$(ls -t build/distributions/*.zip 2>/dev/null | head -1 || true)"
[[ -n "$zip" ]] || { echo "ERROR: no plugin zip produced in build/distributions/" >&2; exit 1; }
echo "==> Built: $zip"
