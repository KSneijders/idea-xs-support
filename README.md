# XS Check — JetBrains plugin

JetBrains/IntelliJ plugin that brings language support for AoE2:DE's **XS** scripting
language: diagnostics, completion, hover docs, and semantic highlighting. It is a thin
editor integration around the upstream [`xs-check`][xs-check] LSP server, driven through
[LSP4IJ].

The Rust core is vendored as a git submodule and built from source by Gradle.
Configuration is delivered the standard LSP way (`workspace/configuration`) rather than via
a patched server. The submodule is otherwise kept clean; the only local change is a single
bug-fix carried as a patch in `patches/` (see [Local patches](#local-patches)), pending
upstream.

[xs-check]: https://github.com/Divy1211/xs-check
[LSP4IJ]: https://plugins.jetbrains.com/plugin/23257-lsp4ij

## Layout

```
.
├── build.gradle.kts   # plugin build + the cargo "port" step (buildLsp/bundleLsp)
├── src/main/...        # the JetBrains plugin (Kotlin) + resources
├── scripts/           # release.sh / release.ps1
├── patches/           # local fixes applied onto the xs-check submodule
└── xs-check/          # git submodule -> Divy1211/xs-check (the Rust LSP core)
```

Key Kotlin pieces:

- `XsServerFactory` — registers the LSP server and extracts the bundled binary (cached by
  content hash, so a rebuilt server is always re-extracted).
- `XsLanguageClient` — answers the server's `workspace/configuration` request (section
  `xsc`) from the per-project settings. **This is how config reaches the server.**
- `XsLexer` / `XsSyntaxHighlighter` — lexer-based highlighting (keywords, types, functions,
  constants, doc `@tags`).
- `XsSemanticTokensColorsProvider` — colors the LSP `parameter` semantic token; the default
  XS colors ship in `src/main/resources/colors/`.
- `XsSettings` / `XsSettingsConfigurable` — the per-project settings UI (prelude path,
  include directories, ignored warnings).

## Prerequisites

- JDK 21
- A Rust toolchain (`cargo`) on `PATH` — the LSP server is compiled from source; upstream
  does not publish prebuilt binaries.

## First checkout

```bash
git clone --recurse-submodules <this-repo>
# or, if already cloned:
git submodule update --init
```

## Build & run

```bash
./gradlew runIde        # launches a sandbox IDE with the plugin
./gradlew buildPlugin   # produces build/distributions/*.zip
```

Either command automatically runs `cargo build --release` on the submodule and bundles the
binary for the **host** platform into the plugin — no manual binary copying.

Useful tasks:

| Task         | What it does                                                       |
|--------------|-------------------------------------------------------------------|
| `buildLsp`   | `cargo build --release` of `xs-check-lsp` in the submodule        |
| `bundleLsp`  | copies the built binary into `build/generated-resources/binaries` |

## Updating the XS core (low-maintenance path)

Because the submodule is never modified locally, updating is conflict-free:

```bash
git submodule update --remote xs-check   # pull the latest upstream xs-check
./gradlew runIde                          # rebuilds the LSP and runs
git add xs-check && git commit -m "Bump xs-check"
```

To pin a specific version instead:

```bash
cd xs-check && git checkout <tag-or-commit> && cd ..
git add xs-check && git commit -m "Pin xs-check to <ref>"
```

### Local patches

`patches/` holds local fixes applied on top of the upstream `xs-check` submodule. They are
kept here (rather than committed into the submodule) so the submodule itself stays clean.

- `0001-sort-semantic-tokens-by-position.patch` — sorts semantic tokens by source position
  before delta-encoding. Upstream emits them in AST order, which underflows the LSP delta
  encoding and breaks highlighting after the first variable declaration (e.g. parameter
  usages render uncolored). **Should be upstreamed**, then deleted here.

`scripts/release.sh` re-applies these patches automatically after syncing the submodule. To
apply them by hand (e.g. after `git submodule update`):

```bash
git -C xs-check apply ../patches/*.patch
```

## Releasing

Windows (PowerShell) — no setup needed, finds Git Bash and a JDK automatically:

```powershell
.\scripts\release.ps1           # bump xs-check to latest, build, tag
.\scripts\release.ps1 0.3.0     # also set the plugin version to 0.3.0 first
```

macOS / Linux / Git Bash:

```bash
./scripts/release.sh
./scripts/release.sh 0.3.0
```

The script updates the `xs-check` submodule to the latest upstream, builds the plugin,
commits the bump, and tags it. If the [GitHub CLI][gh] (`gh`) is installed and an `origin`
remote exists, it also pushes the tag and creates a GitHub release with the `.zip`
attached; otherwise it stops after building and tells you the artifact path
(`build/distributions/xs-check-jb-<version>.zip`).

Requires a Rust toolchain (`cargo`). It uses `JAVA_HOME`/`java` if set, otherwise
auto-detects a JetBrains JBR — so on a typical dev machine you don't need to configure a
JDK at all.

[gh]: https://cli.github.com/
