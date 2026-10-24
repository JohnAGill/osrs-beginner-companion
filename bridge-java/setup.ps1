$ErrorActionPreference = "Stop"
$libPath = Join-Path $PSScriptRoot "lib"
New-Item -ItemType Directory -Force -Path $libPath | Out-Null
$jarPath = Join-Path $libPath "gson-2.11.0.jar"
Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar" -OutFile $jarPath
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -ne "57928D6E5A6EDEB2ABD3770A8F95BA44DCE45F3B23B7A9DC2B309C581552A78B") {
    throw "Downloaded Gson checksum mismatch."
}
Write-Output "Gson 2.11.0 ready."
