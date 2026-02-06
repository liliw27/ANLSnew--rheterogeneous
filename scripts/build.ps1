Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RootDir

$BuildDir = Join-Path $RootDir "build"
$ClassesDir = Join-Path $BuildDir "classes"
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

Write-Host "[build] Compiling Java sources into build/classes ..."

# Collect sources into a response file so command-line length is not an issue.
$SourcesFile = Join-Path $BuildDir "sources.txt"
Get-ChildItem -Path (Join-Path $RootDir "src") -Recurse -File -Filter "*.java" |
    ForEach-Object { $_.FullName } |
    Set-Content -Encoding UTF8 -Path $SourcesFile

# Use UTF-8 to keep Chinese comments and instance names safe.
javac -encoding UTF-8 -d $ClassesDir "@$SourcesFile"

Write-Host "[build] Done."


