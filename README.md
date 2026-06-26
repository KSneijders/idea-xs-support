# XS Check — JetBrains plugin

Language support for AoE2:DE's **XS** scripting language in IntelliJ-based IDEs
(IntelliJ IDEA, PyCharm, and friends).

**Features**

- Error & warning diagnostics
- Code completion
- Hover documentation
- Syntax & semantic highlighting

Under the hood it's a thin wrapper around the
[`xs-check`](https://github.com/Divy1211/xs-check) language server, integrated via
[LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij).

## Install

1. Grab the plugin `.zip` — from a GitHub release, or build it yourself (see below).
2. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the `.zip`.
3. Restart the IDE, then open a `.xs` file.

Plugin options live under **Settings → Tools → XS Check** (extra prelude, include
directories, ignored warnings).

## Build from source

You need **JDK 21** and a **Rust toolchain** (`cargo`) — the language server is compiled
from source.

```bash
git clone --recurse-submodules <repo>   # the xs-check core is a git submodule
./gradlew runIde        # launch a sandbox IDE with the plugin loaded
./gradlew buildPlugin   # build the installable zip into build/distributions/
```

Gradle compiles the `xs-check` server and bundles it into the plugin automatically — no
manual steps.

## Releasing

Releases are automated. Push a version tag and GitHub Actions builds the Linux + Windows
server binaries, packages one cross-platform plugin zip, and publishes a GitHub release:

```bash
git tag v0.3.0
git push origin v0.3.0
```

The release is named after the tag (without the `v`) and notes which `xs-check` version it
bundles.

### Build locally

To build the zip yourself (e.g. to install from disk for testing):

```bash
./scripts/build.sh         # or  .\scripts\build.ps1  on Windows
./scripts/build.sh 1.2.3   # with an explicit version
```

It auto-detects a JDK (a JetBrains JBR if `JAVA_HOME`/`java` isn't set) and keeps the local
patch applied — otherwise it's just `./gradlew buildPlugin`.

## Updating the XS core

```bash
git submodule update --remote xs-check
git add xs-check && git commit -m "Bump xs-check"
```

The `patches/` folder carries one small fix applied on top of the core (a highlighting bug,
pending upstream). The local build script and CI apply it for you.

---

## Disclaimer

The code in this project is written by [Claude](https://claude.com/claude-code)
(Anthropic's Claude Code) and managed by [KSneijders](https://github.com/KSneijders).
