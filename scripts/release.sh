#!/usr/bin/env bash
#
# Pull the latest xs-check core and cut a plugin release.
#
#   ./scripts/release.sh            # bump xs-check to latest upstream, build, tag
#   ./scripts/release.sh 0.3.0      # also set the plugin version to 0.3.0 first
#
# Steps: update submodule -> (optionally bump version) -> build plugin -> commit
# bump -> tag. If the GitHub CLI (gh) is installed and an `origin` remote exists,
# it also pushes the tag and creates a GitHub release with the .zip attached.
#
# Requires: a Rust toolchain (cargo) and JDK 21 (or JAVA_HOME) on PATH.
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root

version_arg="${1:-}"

# --- 0. Make sure a JDK is available -----------------------------------------
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

# Re-apply the local fixes in patches/ to the xs-check submodule (idempotent).
apply_patches() {
  shopt -s nullglob
  for patch in patches/*.patch; do
    if git -C xs-check apply --reverse --check "../$patch" 2>/dev/null; then
      echo "    patch already applied: $patch"
    elif git -C xs-check apply --check "../$patch" 2>/dev/null; then
      git -C xs-check apply "../$patch"; echo "    applied patch: $patch"
    else
      echo "    WARNING: patch no longer applies (upstream may have fixed it): $patch" >&2
    fi
  done
  shopt -u nullglob
}

# --- 1. Update the xs-check submodule to the latest upstream commit -----------
echo "==> Updating xs-check submodule to latest upstream..."
git -C xs-check checkout -- . 2>/dev/null || true   # drop applied patches so update can fast-forward
git submodule update --init --remote xs-check
xs_ref="$(git -C xs-check describe --tags --always)"
echo "    xs-check is now at: $xs_ref"
apply_patches                                        # re-apply local fixes (see patches/)

# --- 2. Optionally set the plugin version ------------------------------------
if [[ -n "$version_arg" ]]; then
  echo "==> Setting plugin version to $version_arg"
  sed -i -E "s/^version = \".*\"/version = \"$version_arg\"/" build.gradle.kts
fi
version="$(sed -nE 's/^version = "(.*)"/\1/p' build.gradle.kts)"
tag="v$version"
echo "==> Plugin version: $version (tag $tag)"

# --- 3. Build the plugin (compiles the LSP from the submodule) ----------------
echo "==> Building plugin..."
./gradlew buildPlugin --console=plain
zip="build/distributions/xs-check-jb-${version}.zip"
[[ -f "$zip" ]] || { echo "ERROR: expected artifact not found: $zip" >&2; exit 1; }
echo "    Built: $zip"

# --- 4. Commit the bump (if anything changed) --------------------------------
if ! git diff --quiet -- xs-check build.gradle.kts; then
  git add xs-check build.gradle.kts
  git commit -m "Release $tag (xs-check $xs_ref)"
  echo "==> Committed release bump"
fi

# --- 5. Tag ------------------------------------------------------------------
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  echo "==> Tag $tag already exists, leaving it as-is"
else
  git tag -a "$tag" -m "$tag (xs-check $xs_ref)"
  echo "==> Tagged $tag"
fi

# --- 6. Publish to GitHub if possible ----------------------------------------
if command -v gh >/dev/null 2>&1 && git remote get-url origin >/dev/null 2>&1; then
  echo "==> Pushing and creating GitHub release $tag..."
  git push origin HEAD
  git push origin "$tag"
  gh release create "$tag" "$zip" \
    --title "$tag" \
    --notes "XS Check $tag (xs-check core: $xs_ref)"
  echo "==> Published GitHub release $tag"
else
  echo "==> Skipping GitHub publish (need both the 'gh' CLI and an 'origin' remote)."
  echo "    Release artifact is ready at: $zip"
  echo "    To publish: add an 'origin' remote, install gh (https://cli.github.com/),"
  echo "    then push '$tag' and upload the zip — or just re-run this script."
fi

echo "==> Done."
