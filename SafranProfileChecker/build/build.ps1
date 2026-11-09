param(
    [string]$RhapsodyHome = "C:\Program Files\IBM\Rhapsody\10.0.2"
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$rhapsodyJar = Join-Path $RhapsodyHome "Share\JavaAPI\Rhapsody.jar"
if (-not (Test-Path $rhapsodyJar)) {
    throw "Rhapsody.jar not found at $rhapsodyJar"
}
$target = Join-Path $root "target"
$classes = Join-Path $target "classes"
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
New-Item $classes -ItemType Directory | Out-Null
$sources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sourcesFile = Join-Path $target "sources.txt"
$sources | Set-Content $sourcesFile -Encoding UTF8
& javac --release 17 -encoding UTF-8 -cp $rhapsodyJar -d $classes "@$sourcesFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
& jar --create --file (Join-Path $target "SafranChecker.jar") -C $classes .
Copy-Item (Join-Path $root "safran-checkers.xml") $target
Copy-Item (Join-Path $root "SafranChecker.hep") $target
Write-Host "Build completed: $(Join-Path $target 'SafranChecker.jar')"
