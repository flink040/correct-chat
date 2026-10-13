[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$targets = @('26.2', '26.1', '1.21.11')
$modVersion = (Select-String -Path (Join-Path $PSScriptRoot 'gradle.properties') -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
$wrapper = if ($IsWindows -or $env:OS -eq 'Windows_NT') {
    Join-Path $PSScriptRoot 'gradlew.bat'
} else {
    Join-Path $PSScriptRoot 'gradlew'
}
$staging = Join-Path ([System.IO.Path]::GetTempPath()) ("correct-chat-build-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $staging | Out-Null

try {
    foreach ($target in $targets) {
        Write-Host "Building Correct Chat for Minecraft $target"
        & $wrapper --no-daemon clean build "-Pminecraft_target=$target"
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for Minecraft $target"
        }
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "build/libs/correct-chat-mc$target-$modVersion.jar") -Destination $staging
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "build/libs/correct-chat-mc$target-$modVersion-sources.jar") -Destination $staging
    }

    $output = Join-Path $PSScriptRoot 'build/libs'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    Copy-Item -Path (Join-Path $staging '*.jar') -Destination $output -Force
} finally {
    Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host 'All versioned JARs are available in build\libs.'
