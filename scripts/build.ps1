# Windows convenience wrapper for scripts/build.sh.
#
#   .\scripts\build.ps1            # build with the dev version
#   .\scripts\build.ps1 1.2.3      # build with an explicit version
#
# Finds Git Bash and runs the real build script. No need to set JAVA_HOME --
# build.sh auto-detects a JetBrains JBR if one isn't already on PATH.
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

# Run build.sh from the repo root so its relative paths resolve.
Push-Location $repo
try {
    & $bash "scripts/build.sh" @args
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $code
