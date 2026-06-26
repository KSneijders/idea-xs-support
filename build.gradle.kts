plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.xscheck"
// Releases set the version from the pushed tag (-PpluginVersion=…); the fallback is only
// a placeholder for local dev builds (runIde / buildPlugin) where the version is irrelevant.
version = providers.gradleProperty("pluginVersion").getOrElse("0.0.0-dev")

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        // Check https://plugins.jetbrains.com/plugin/23257-lsp4ij for the latest version
        plugin("com.redhat.devtools.lsp4ij:0.7.0")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "XS Check"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}

// ---------------------------------------------------------------------------
// xs-check-lsp ("port" step)
//
// Builds the LSP server from the `xs-check` git submodule with cargo and bundles
// the host-platform binary into the plugin's resources. This is wired into
// `processResources`, so `./gradlew runIde` / `buildPlugin` "just work" -- no
// manual binary copying. Updating the core is `git submodule update --remote`.
// Requires a Rust toolchain (cargo) on PATH.
// ---------------------------------------------------------------------------

val lspDir = layout.projectDirectory.dir("xs-check")

// Platform/extension naming must match XsServerFactory's resource lookup.
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val isArm = osArch.contains("aarch64") || osArch.contains("arm")
val lspPlatform = when {
    osName.contains("win") -> "win32-x64"
    osName.contains("mac") -> if (isArm) "darwin-arm64" else "darwin-x64"
    else                   -> if (isArm) "linux-arm64" else "linux-x64"
}
val lspExt = if (osName.contains("win")) ".exe" else ""
val lspBinaryName = "xs-check-lsp$lspExt"

val lspBuiltBinary = lspDir.dir("target/release").file(lspBinaryName)
val generatedResources = layout.buildDirectory.dir("generated-resources")

val buildLsp by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles xs-check-lsp from the xs-check submodule via cargo."

    workingDir = lspDir.asFile
    commandLine("cargo", "build", "--release", "--bin", "xs-check-lsp")

    doFirst {
        if (!lspDir.file("Cargo.toml").asFile.exists()) {
            throw GradleException(
                "The xs-check submodule is not checked out. Run:\n" +
                    "  git submodule update --init"
            )
        }
    }

    // Up-to-date checks: rebuild only when the Rust sources change.
    inputs.dir(lspDir.dir("xsc-core/src"))
    inputs.file(lspDir.file("xsc-core/prelude.xs"))
    inputs.dir(lspDir.dir("xsc-lsp/src"))
    inputs.file(lspDir.file("xsc-lsp/build.rs"))
    inputs.file(lspDir.file("Cargo.toml"))
    inputs.file(lspDir.file("Cargo.lock"))
    outputs.file(lspBuiltBinary)
}

val bundleLsp by tasks.registering(Copy::class) {
    group = "build"
    description = "Bundles the host xs-check-lsp binary into plugin resources."

    dependsOn(buildLsp)
    from(lspBuiltBinary)
    into(generatedResources.map { it.dir("binaries/$lspPlatform") })
}

sourceSets {
    main {
        resources {
            srcDir(generatedResources)
        }
    }
}

// CI builds the server for every platform on separate runners and drops the binaries
// straight into src/main/resources/binaries/<platform>/, then packages with
// -PskipLspBuild so this local cargo build is skipped (and doesn't clash with them).
if (!providers.gradleProperty("skipLspBuild").isPresent) {
    tasks.named("processResources") {
        dependsOn(bundleLsp)
    }
}
