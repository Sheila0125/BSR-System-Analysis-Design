param(
    [switch]$EnableDemoSeed
)

$ErrorActionPreference = 'Stop'

if (-not $EnableDemoSeed) {
    throw 'Demo seed is disabled by default. Re-run with -EnableDemoSeed for an explicitly configured disposable local database.'
}

$requiredVariables = @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD')
$missingVariables = @($requiredVariables | Where-Object {
    $value = [Environment]::GetEnvironmentVariable($_)
    [string]::IsNullOrWhiteSpace($value)
})
if ($missingVariables.Count -gt 0) {
    throw "Missing local demo database variables: $($missingVariables -join ', ')"
}

$databaseName = [Environment]::GetEnvironmentVariable('DB_NAME')
$normalizedDatabaseName = $databaseName.ToLowerInvariant()
if (-not ($normalizedDatabaseName.StartsWith('bsr_dev') -or $normalizedDatabaseName.StartsWith('bsr_demo'))) {
    throw 'DB_NAME must start with bsr_dev or bsr_demo; refusing to seed another database.'
}

$port = 0
if (-not [int]::TryParse([Environment]::GetEnvironmentVariable('DB_PORT'), [ref]$port) -or
    $port -lt 1 -or
    $port -gt 65535) {
    throw 'DB_PORT must be an integer from 1 to 65535.'
}

$mysql = Get-Command 'mysql' -CommandType Application -ErrorAction Stop
$root = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $root 'backend\src\test\resources\fixtures\core\v003-foundation-smoke.sql'
if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) {
    throw "Missing fictional demo fixture: $fixture"
}

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $mysql.Source
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.CreateNoWindow = $true
$startInfo.ArgumentList.Add('--protocol=TCP')
$startInfo.ArgumentList.Add("--host=$([Environment]::GetEnvironmentVariable('DB_HOST'))")
$startInfo.ArgumentList.Add("--port=$port")
$startInfo.ArgumentList.Add("--user=$([Environment]::GetEnvironmentVariable('DB_USERNAME'))")
$startInfo.ArgumentList.Add('--default-character-set=utf8mb4')
$startInfo.ArgumentList.Add($databaseName)
$startInfo.Environment['MYSQL_PWD'] = [Environment]::GetEnvironmentVariable('DB_PASSWORD')

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
try {
    if (-not $process.Start()) {
        throw 'Failed to start mysql client.'
    }
    $process.StandardInput.Write([System.IO.File]::ReadAllText($fixture))
    $process.StandardInput.Close()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        throw "Demo seed failed with mysql exit code $($process.ExitCode): $stderr"
    }
    if (-not [string]::IsNullOrWhiteSpace($stdout)) {
        Write-Output $stdout.Trim()
    }
}
finally {
    $process.Dispose()
}

Write-Output "Fictional demo fixture loaded into explicitly enabled local database '$databaseName'."
