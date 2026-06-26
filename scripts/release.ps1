# Windows convenience wrapper for scripts/release.sh.
#
#   .\scripts\release.ps1            # bump xs-check to latest, build, tag
#   .\scripts\release.ps1 0.3.0      # also set the plugin version to 0.3.0 first
#
# Finds Git Bash and runs the real release script. No need to set JAVA_HOME --
# release.sh auto-detects a JetBrains JBR if one isn't already on PATH.
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot   # scripts\.. -> repo root

# Locate Git Bash.
$bash = $null
foreach ($p in @(
    "$env:ProgramFiles\Git\bin\bash.exe",
    "${env:ProgramFiles(x86)}\Git\bin\bash.exe",
    "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
)) {
    if ($p -and (Test-Path $p)) { $bash = $p; break }
}
if (-not $bash) {
    $cmd = Get-Command bash.exe -ErrorAction SilentlyContinue
    if ($cmd) { $bash = $cmd.Source }
}
if (-not $bash) {
    throw "Git Bash not found. Install Git for Windows: https://git-scm.com/download/win"
}

# Run release.sh from the repo root so its relative paths resolve.
Push-Location $repo
try {
    & $bash "scripts/release.sh" @args
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $code
