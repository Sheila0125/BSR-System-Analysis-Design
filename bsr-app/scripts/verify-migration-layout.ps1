$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$migrationRoot = Join-Path $root 'backend\src\main\resources\db\migration'
$failures = [System.Collections.Generic.List[string]]::new()

if (-not (Test-Path -LiteralPath $migrationRoot -PathType Container)) {
    Write-Error "Missing migration directory: $migrationRoot"
    exit 1
}

$migrationFiles = @(Get-ChildItem -LiteralPath $migrationRoot -Filter '*.sql' -File | Sort-Object Name)
$versionedFiles = [System.Collections.Generic.List[object]]::new()

foreach ($file in $migrationFiles) {
    if ($file.Name -notmatch '^V(?<version>[0-9]{3})__[a-z0-9_]+\.sql$') {
        $failures.Add("Invalid Flyway migration name: $($file.Name)")
        continue
    }

    $versionedFiles.Add([pscustomobject]@{
        Version = [int]$Matches.version
        File = $file
        Sql = Get-Content -LiteralPath $file.FullName -Raw
    })
}

$duplicates = @($versionedFiles | Group-Object Version | Where-Object Count -gt 1)
foreach ($duplicate in $duplicates) {
    $failures.Add("Duplicate migration version V$('{0:D3}' -f [int]$duplicate.Name)")
}

$versions = @($versionedFiles.Version | Sort-Object)
if ($versions.Count -gt 0) {
    $expectedVersions = @(1..$versions[-1])
    if (($versions -join ',') -ne ($expectedVersions -join ',')) {
        $failures.Add("Migration versions must be continuous from V001: found $($versions -join ', ')")
    }
}

$requiredNames = @(
    'V001__create_identity_and_organization_tables.sql',
    'V002__create_core_business_tables.sql',
    'V003__add_constraints_and_business_indexes.sql',
    'V004__add_rental_request_supplement_history.sql'
)
foreach ($requiredName in $requiredNames) {
    if ($requiredName -notin $migrationFiles.Name) {
        $failures.Add("Missing required migration: $requiredName")
    }
}

$expectedTables = @(
    'account',
    'audit_event',
    'customer',
    'employee',
    'employee_shift',
    'equipment',
    'equipment_inspection',
    'equipment_transfer',
    'equipment_transfer_item',
    'payment_transaction',
    'rental_change',
    'rental_change_item',
    'rental_charge',
    'rental_contract',
    'rental_item',
    'rental_request',
    'rental_request_item',
    'rental_request_supplement',
    'repair_contractor',
    'repair_labor',
    'repair_rate',
    'repair_service_charge',
    'repair_task',
    'role_grant',
    'store'
) | Sort-Object

$createdTables = [System.Collections.Generic.List[string]]::new()
$legacyOnlyTables = @(
    'app_user',
    'rental_line',
    'settlement',
    'contractor',
    'contractor_rate',
    'repair_order',
    'work_shift'
)

foreach ($migration in $versionedFiles) {
    foreach ($match in [regex]::Matches($migration.Sql, '(?im)^\s*CREATE\s+TABLE\s+[\x60]?([a-z_]+)[\x60]?\s*\(')) {
        $createdTables.Add($match.Groups[1].Value)
    }

    foreach ($legacyTable in $legacyOnlyTables) {
        $pattern = "(?im)^\s*CREATE\s+TABLE\s+[\x60]?$([regex]::Escape($legacyTable))[\x60]?\s*\("
        if ($migration.Sql -match $pattern) {
            $failures.Add("Forbidden old 11-table artifact in $($migration.File.Name): $legacyTable")
        }
    }
}

$duplicateTables = @($createdTables | Group-Object | Where-Object Count -gt 1)
foreach ($duplicateTable in $duplicateTables) {
    $failures.Add("Business table created more than once: $($duplicateTable.Name)")
}

$actualTables = @($createdTables | Sort-Object -Unique)
if (($actualTables -join ',') -ne ($expectedTables -join ',')) {
    $missing = @($expectedTables | Where-Object { $_ -notin $actualTables })
    $extra = @($actualTables | Where-Object { $_ -notin $expectedTables })
    $failures.Add("Business table set mismatch. Missing: $($missing -join ', '); extra: $($extra -join ', ')")
}

$v001 = $versionedFiles | Where-Object Version -eq 1 | Select-Object -First 1
$v002 = $versionedFiles | Where-Object Version -eq 2 | Select-Object -First 1
$v003 = $versionedFiles | Where-Object Version -eq 3 | Select-Object -First 1
$v004 = $versionedFiles | Where-Object Version -eq 4 | Select-Object -First 1

if ($null -ne $v001) {
    $v001TableCount = ([regex]::Matches($v001.Sql, '(?im)^\s*CREATE\s+TABLE\s+')).Count
    if ($v001TableCount -ne 6) {
        $failures.Add("V001 must create 6 identity/organization tables; found $v001TableCount")
    }
}

if ($null -ne $v002) {
    $v002TableCount = ([regex]::Matches($v002.Sql, '(?im)^\s*CREATE\s+TABLE\s+')).Count
    if ($v002TableCount -ne 18) {
        $failures.Add("V002 must create 18 core business tables; found $v002TableCount")
    }
}

if ($null -ne $v003) {
    $v003TableCount = ([regex]::Matches($v003.Sql, '(?im)^\s*CREATE\s+TABLE\s+')).Count
    if ($v003TableCount -ne 0) {
        $failures.Add("V003 must not create additional business tables; found $v003TableCount")
    }

    $uniqueCount = ([regex]::Matches($v003.Sql, '(?im)^\s*ADD\s+CONSTRAINT\s+uk_[a-z0-9_]+\s+(?:UNIQUE|\r?\n\s*UNIQUE)')).Count
    $checkCount = ([regex]::Matches($v003.Sql, '(?im)^\s*ADD\s+CONSTRAINT\s+ck_[a-z0-9_]+')).Count
    if ($uniqueCount -ne 23) {
        $failures.Add("V003 must add 23 UNIQUE constraints; found $uniqueCount")
    }
    if ($checkCount -ne 61) {
        $failures.Add("V003 must add 61 CHECK constraints; found $checkCount")
    }
}

if ($null -ne $v004) {
    $v004Tables = @([regex]::Matches($v004.Sql, '(?im)^\s*CREATE\s+TABLE\s+([a-z_]+)\s*\(') |
        ForEach-Object { $_.Groups[1].Value })
    if (($v004Tables -join ',') -ne 'rental_request_supplement') {
        $failures.Add("V004 must create only rental_request_supplement; found $($v004Tables -join ', ')")
    }
    $v004ForeignKeys = ([regex]::Matches($v004.Sql, '(?im)^\s*FOREIGN\s+KEY\s*\(')).Count
    $v004Restrict = ([regex]::Matches($v004.Sql, '(?im)^\s*ON\s+DELETE\s+RESTRICT\s+ON\s+UPDATE\s+RESTRICT')).Count
    $v004Unique = ([regex]::Matches($v004.Sql, '(?im)^\s*CONSTRAINT\s+uk_[a-z0-9_]+\s+UNIQUE')).Count
    $v004Checks = ([regex]::Matches($v004.Sql, '(?im)^\s*CONSTRAINT\s+ck_[a-z0-9_]+\s+CHECK')).Count
    if ($v004ForeignKeys -ne 2 -or $v004Restrict -ne 2 -or $v004Unique -ne 1 -or $v004Checks -ne 3) {
        $failures.Add("V004 needs 2 RESTRICT FKs, 1 UNIQUE, 3 CHECKs: FK=$v004ForeignKeys RESTRICT=$v004Restrict UNIQUE=$v004Unique CHECK=$v004Checks")
    }
}

$foundationSql = (($versionedFiles | Where-Object Version -le 3).Sql) -join "`n"
$foreignKeyCount = ([regex]::Matches($foundationSql, '(?im)^\s*FOREIGN\s+KEY\s*\(')).Count
$restrictCount = ([regex]::Matches($foundationSql, '(?im)^\s*ON\s+DELETE\s+RESTRICT\s+ON\s+UPDATE\s+RESTRICT')).Count
if ($foreignKeyCount -ne 55) {
    $failures.Add("V001-V003 must define 55 foreign keys; found $foreignKeyCount")
}
if ($restrictCount -ne $foreignKeyCount) {
    $failures.Add("Every foreign key must explicitly use ON DELETE/UPDATE RESTRICT: FK=$foreignKeyCount, RESTRICT=$restrictCount")
}

if ($foundationSql -match '(?i)ON\s+DELETE\s+CASCADE') {
    $failures.Add('Cascade delete is forbidden for business history.')
}
if ($foundationSql -match '(?i)GENERATED\s+ALWAYS') {
    $failures.Add('Generated columns are forbidden for the employee shift uniqueness rule.')
}
if ($foundationSql -match '(?is)UNIQUE\s*\(\s*employee_id\s*,\s*shift_date') {
    $failures.Add('Employee shift must not have an unconditional (employee_id, shift_date) UNIQUE constraint.')
}

$allSql = ($versionedFiles.Sql) -join "`n"
if ($allSql -match '(?i)ON\s+DELETE\s+CASCADE') {
    $failures.Add('Cascade delete is forbidden for business history.')
}
$schemaNames = [regex]::Matches($allSql, '(?im)^\s*(?:CONSTRAINT|INDEX|ADD\s+CONSTRAINT|ADD\s+INDEX)\s+([a-z0-9_]+)') |
    ForEach-Object { $_.Groups[1].Value }
$tooLong = @($schemaNames | Where-Object Length -gt 64)
foreach ($name in $tooLong) {
    $failures.Add("Schema identifier exceeds MySQL's 64-character limit: $name")
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Error -Message $failure -ErrorAction Continue
    }
    exit 1
}

Write-Output "Migration layout verification passed: $($migrationFiles.Count) files, 25 business tables (24 foundation + supplement history)."
Write-Output 'V001-V003 retain 55 FKs, 23 UNIQUE and 61 CHECK constraints; V004 adds 2 RESTRICT FKs, 1 UNIQUE and 3 CHECKs.'
