Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RootDir

# Always build first (simple and reproducible).
& (Join-Path $PSScriptRoot "build.ps1")

Write-Host "[run] Running src/test/testSA ..."

$ClassesDir = (Resolve-Path (Join-Path $RootDir "build" "classes")).Path

# Forward all arguments to testSA (supports both positional and named args).
java -cp $ClassesDir test.testSA @args


