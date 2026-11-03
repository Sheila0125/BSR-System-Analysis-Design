param(
    [switch]$RequireLocalDb,
    [switch]$RequireTestDb
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Assert-CommandVersion {
    param(
        [string]$Command,
        [string[]]$Arguments,
        [string]$ExpectedPattern,
        [string]$Label
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Command
    $startInfo.Arguments = $Arguments -join ' '
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $output = (($process.StandardOutput.ReadToEnd() + "`n" + $process.StandardError.ReadToEnd()).Trim())
    $process.WaitForExit()

    if ($process.ExitCode -ne 0 -or $output -notmatch $ExpectedPattern) {
        throw "$Label version check failed. Expected pattern '$ExpectedPattern'."
    }
    Write-Output "PASS $Label"
}

function Assert-Variables {
    param(
        [string[]]$Names,
        [string]$Prefix
    )

    foreach ($name in $Names) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Missing required $Prefix variable: $name"
        }
    }
}

Assert-CommandVersion -Command 'java.exe' -Arguments @('-version') -ExpectedPattern '24\.0\.1' -Label 'Java 24.0.1'
Assert-CommandVersion -Command 'node.exe' -Arguments @('--version') -ExpectedPattern '^v24\.18\.0$' -Label 'Node 24.18.0'
Assert-CommandVersion -Command 'cmd.exe' -Arguments @('/d', '/c', 'npm.cmd', '--version') -ExpectedPattern '^11\.16\.0$' -Label 'npm 11.16.0'

$wrapper = Join-Path $root 'backend\.mvn\wrapper\maven-wrapper.properties'
$wrapperText = Get-Content -LiteralPath $wrapper -Raw
if ($wrapperText -notmatch 'apache-maven-3\.9\.16-bin\.zip' -or
    $wrapperText -notmatch 'distributionSha256Sum=') {
    throw 'Maven Wrapper is not pinned to verified Maven 3.9.16.'
}
Write-Output 'PASS Maven Wrapper 3.9.16 pin and checksum'

$miniPackage = Join-Path $root 'miniprogram\package.json'
if (Test-Path -LiteralPath $miniPackage) {
    $miniPackageJson = Get-Content -LiteralPath $miniPackage -Raw | ConvertFrom-Json
    if ($miniPackageJson.devDependencies.'miniprogram-api-typings' -ne '5.2.3') {
        throw 'miniprogram-api-typings must be pinned to verified version 5.2.3.'
    }
    Write-Output 'PASS mini program API typings 5.2.3 pin'
}

if ($RequireLocalDb) {
    Assert-Variables -Names @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD') -Prefix 'local database'
    if ($env:DB_PORT -notmatch '^\d{1,5}$' -or [int]$env:DB_PORT -lt 1 -or [int]$env:DB_PORT -gt 65535) {
        throw 'DB_PORT must be an explicit TCP port from 1 to 65535.'
    }
    if ($env:DB_NAME -notmatch '^bsr_dev(?:_[a-z0-9_]+)?$') {
        throw 'DB_NAME must identify a dedicated BSR development schema starting with bsr_dev.'
    }
    Write-Output 'PASS local database variable shape; no connection attempted'
}

if ($RequireTestDb) {
    Assert-Variables -Names @('TEST_DB_HOST', 'TEST_DB_PORT', 'TEST_DB_NAME', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD') -Prefix 'test database'
    if ($env:TEST_DB_PORT -notmatch '^\d{1,5}$' -or [int]$env:TEST_DB_PORT -lt 1 -or [int]$env:TEST_DB_PORT -gt 65535) {
        throw 'TEST_DB_PORT must be an explicit TCP port from 1 to 65535.'
    }
    if ($env:TEST_DB_NAME -notmatch '^bsr_test_[a-z0-9_]+$') {
        throw 'TEST_DB_NAME must be isolated and start with bsr_test_.'
    }
    if ($env:TEST_DB_NAME -eq $env:DB_NAME) {
        throw 'TEST_DB_NAME must not equal DB_NAME.'
    }
    Write-Output 'PASS test database isolation variable shape; no connection attempted'
}

Write-Output 'Preflight completed without changing any database or service.'
