param(
    [string]$Fixture = "contracts/fixtures/missing.request.json",
    [string]$ServiceUrl = "http://127.0.0.1:8765"
)
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $PSScriptRoot "lib/gson-2.11.0.jar"
if (!(Test-Path -LiteralPath $jarPath)) {
    throw "Missing Gson. Run bridge-java/setup.ps1 first."
}
$expectedHash = "57928D6E5A6EDEB2ABD3770A8F95BA44DCE45F3B23B7A9DC2B309C581552A78B"
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -ne $expectedHash) {
    throw "Gson checksum mismatch."
}
$buildPath = Join-Path $PSScriptRoot "build"
New-Item -ItemType Directory -Force -Path $buildPath | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "src") -Filter *.java -Recurse | ForEach-Object { $_.FullName })
& javac --release 11 -cp $jarPath -d $buildPath @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (![IO.Path]::IsPathRooted($Fixture)) { $Fixture = Join-Path $projectRoot $Fixture }
& java -cp "$buildPath;$jarPath" CompanionCaller $Fixture $ServiceUrl
exit $LASTEXITCODE
