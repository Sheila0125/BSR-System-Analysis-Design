param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$InventoryPath = 'docs/acceptance/T003-evidence-inventory.md'
)

$ErrorActionPreference = 'Stop'
$failures = [System.Collections.Generic.List[string]]::new()
$sourceCache = @{}

# This is a static location gate, not a test runner or a PASS-results ledger.
# The input is the T003 Markdown inventory, whose links are relative to itself.
try {
    $root = [IO.Path]::GetFullPath($RepositoryRoot)
    if (-not [IO.Path]::IsPathRooted($InventoryPath)) {
        $InventoryPath = Join-Path $root $InventoryPath
    }
    $inventory = Get-Item -LiteralPath $InventoryPath -ErrorAction Stop
    $rows = @(Get-Content -LiteralPath $inventory.FullName -Encoding UTF8 |
        Where-Object { $_ -match '^\|.*\|\s*$' } |
        ForEach-Object {
            [pscustomobject]@{ Cells = @($_.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() }) }
        })
} catch {
    Write-Output "TRACE_INPUT_ERROR $($_.Exception.Message)"
    exit 1
}

function Get-TestLinks([string]$Cell, [string]$Label, [switch]$RequireLine) {
    $links = @([regex]::Matches($Cell, '\[[^\]]+\]\((?<target>[^)]+)\)'))
    if ($links.Count -eq 0) {
        $failures.Add("TRACE_NO_TEST $Label")
    }
    foreach ($link in $links) {
        if ($link.Groups['target'].Value -notmatch '^(?<path>[^#]+)(?:#L(?<line>[1-9][0-9]*))?$') {
            $failures.Add("TRACE_INVALID_LINK $Label")
            continue
        }
        $relativeLink = $Matches.path
        $lineNumber = if ($Matches.ContainsKey('line')) { [int]$Matches.line } else { 0 }
        try {
            $path = [IO.Path]::GetFullPath((Join-Path $inventory.DirectoryName $relativeLink))
            $rootPrefix = $root.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
            if (-not $path.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                $failures.Add("TRACE_INVALID_TEST_PATH $Label $relativeLink")
                continue
            }
            $relative = $path.Substring($rootPrefix.Length).Replace('\', '/')
        } catch {
            $failures.Add("TRACE_INVALID_TEST_PATH $Label $relativeLink")
            continue
        }
        $command = $null
        if ($relative -cmatch '^backend/src/test/java/.+(Test|IT)\.java$') {
            $command = '.\backend\mvnw.cmd -f backend\pom.xml -B -Pit verify'
        } elseif ($relative -cmatch '^frontend/tests/.+\.(test|spec)\.tsx?$') {
            $runner = if ($relative.StartsWith('frontend/tests/e2e/')) { 'test:e2e' } else { 'test:unit' }
            $command = "npm.cmd --prefix frontend run $runner"
        } elseif ($relative -cmatch '^miniprogram/tests/.+\.(test|spec)\.tsx?$') {
            $command = 'npm.cmd --prefix miniprogram run test:unit'
        } else {
            $failures.Add("TRACE_INVALID_TEST_PATH $Label $relative")
            continue
        }
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            $failures.Add("TRACE_MISSING_FILE $Label $relative")
            continue
        }
        if ($RequireLine -and $lineNumber -eq 0) {
            $failures.Add("TRACE_MISSING_TEST_LINE $Label $relative")
            continue
        }
        [pscustomobject]@{ Path = $path; Relative = $relative; Line = $lineNumber; Command = $command }
    }
}

function Test-AcName($Link, [string]$Ac) {
    if (-not $sourceCache.ContainsKey($Link.Path)) {
        $source = Get-Content -LiteralPath $Link.Path -Raw -Encoding UTF8
        # Preserve line numbers when removing comments. Only literal declarations
        # are supported; this does not prove discovery, assertions or execution.
        $source = [regex]::Replace($source, '(?s)/\*.*?\*/', {
            param($match)
            [regex]::Replace($match.Value, '[^\r\n]', ' ')
        })
        $sourceCache[$Link.Path] = @($source -split '\r?\n')
    }
    $lines = $sourceCache[$Link.Path]
    if ($Link.Line -gt $lines.Count) { return $false }
    $line = $lines[$Link.Line - 1]
    $name = $null
    if ($Link.Relative.EndsWith('.java')) {
        if ($line -match '^\s*(?:@Test\s+)?@DisplayName\(\s*"(?<name>[^"\r\n]+)"\s*\)') {
            $name = $Matches.name
        }
    } else {
        if ($line -match '^\s*(?:test|it|describe)\(\s*[''"](?<name>[^''"\r\n]+)[''"]\s*,') {
            $name = $Matches.name
        }
    }
    return ($null -ne $name -and $name -cmatch ('(?<![A-Za-z0-9_-])' + [regex]::Escape($Ac) + '(?![A-Za-z0-9_-])'))
}

$expectedIds = @(0..16 | ForEach-Object { 'AC-UC{0:D2}' -f $_ }) +
    @(1..40 | ForEach-Object { 'AC-BR{0:D2}' -f $_ }) +
    @(0..6 | ForEach-Object { 'AC-WX{0:D2}' -f $_ })
$acRows = @($rows | Where-Object { $_.Cells[0] -cmatch '^AC-' })
foreach ($ac in $expectedIds) {
    $found = @($acRows | Where-Object { $_.Cells[0] -ceq $ac })
    if ($found.Count -eq 0) { $failures.Add("TRACE_MISSING_ID $ac") }
    if ($found.Count -gt 1) { $failures.Add("TRACE_DUPLICATE_ID $ac") }
}
foreach ($row in $acRows) {
    $ac = $row.Cells[0]
    if ($ac -cnotin $expectedIds) { $failures.Add("TRACE_UNKNOWN_ID $ac") }
    if ($row.Cells.Count -ne 4) {
        $failures.Add("TRACE_INVALID_ROW $ac")
        continue
    }
    $commands = @($row.Cells[2].Replace('\\', '\').Split([char]0xFF1B) | ForEach-Object { $_.Trim() })
    foreach ($link in @(Get-TestLinks $row.Cells[1] $ac -RequireLine)) {
        if (-not (Test-AcName $link $ac)) {
            $failures.Add("TRACE_WRONG_TEST_NAME $ac $($link.Relative)#L$($link.Line)")
        }
        if ($link.Command -cnotin $commands) {
            $failures.Add("TRACE_WRONG_COMMAND $ac $($link.Relative)")
        }
    }
}

$expectedTables = @(
    'account', 'role_grant', 'customer', 'employee', 'repair_contractor', 'store',
    'rental_request', 'rental_request_item', 'rental_contract', 'rental_item',
    'rental_change', 'rental_charge', 'equipment', 'equipment_inspection',
    'repair_task', 'repair_labor', 'repair_rate', 'repair_service_charge',
    'equipment_transfer', 'equipment_transfer_item', 'employee_shift',
    'rental_change_item', 'payment_transaction', 'audit_event', 'rental_request_supplement'
)
$entityRows = @($rows | Where-Object { $_.Cells.Count -eq 6 -and $_.Cells[1] -match '^[0-9]+$' })
foreach ($table in $expectedTables) {
    $found = @($entityRows | Where-Object { $_.Cells[3] -ceq $table })
    if ($found.Count -eq 0) { $failures.Add("TRACE_MISSING_TABLE $table") }
    if ($found.Count -gt 1) { $failures.Add("TRACE_DUPLICATE_TABLE $table") }
}
foreach ($row in $entityRows) {
    if ($row.Cells[3] -cnotin $expectedTables) { $failures.Add("TRACE_UNKNOWN_TABLE $($row.Cells[3])") }
    $null = @(Get-TestLinks $row.Cells[4] "TABLE:$($row.Cells[3])")
}

$expectedModules = @('identity', 'customerrequest', 'rental', 'equipment', 'repair', 'storetransfer', 'scheduling', 'audit')
foreach ($module in $expectedModules) {
    $found = @($rows | Where-Object { $_.Cells.Count -eq 4 -and $_.Cells[0] -ceq $module })
    if ($found.Count -eq 0) { $failures.Add("TRACE_MISSING_MODULE $module") }
    if ($found.Count -gt 1) { $failures.Add("TRACE_DUPLICATE_MODULE $module") }
    foreach ($row in $found) { $null = @(Get-TestLinks $row.Cells[2] "MODULE:$module") }
}

$areaRows = @($rows | Where-Object { $_.Cells.Count -eq 4 -and $_.Cells[0] -match '^[0-9]{2}$' })
foreach ($number in 1..25) {
    $id = '{0:D2}' -f $number
    $found = @($areaRows | Where-Object { $_.Cells[0] -ceq $id })
    if ($found.Count -eq 0) { $failures.Add("TRACE_MISSING_AREA $id") }
    if ($found.Count -gt 1) { $failures.Add("TRACE_DUPLICATE_AREA $id") }
}
foreach ($row in $areaRows) {
    if ([int]$row.Cells[0] -lt 1 -or [int]$row.Cells[0] -gt 25) { $failures.Add("TRACE_UNKNOWN_AREA $($row.Cells[0])") }
    $null = @(Get-TestLinks $row.Cells[2] "AREA:$($row.Cells[0])")
}

Write-Output 'TEST_EXECUTION_NOT_CHECKED: static traceability does not establish test PASS or external acceptance.'
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Output $_ }
    Write-Output "TRACE_FAILED errors=$($failures.Count)"
    exit 1
}
Write-Output 'TRACE_OK ids=64 tables=25 modules=8 areas=25'
exit 0
