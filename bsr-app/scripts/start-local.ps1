$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'preflight.ps1') -RequireLocalDb
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& (Join-Path $root 'backend\mvnw.cmd') `
    -f (Join-Path $root 'backend\pom.xml') `
    spring-boot:run `
    '-Dspring-boot.run.profiles=local'
exit $LASTEXITCODE
