$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

$requiredFiles = @(
    'AGENTS.md',
    'docs\architecture\baseline.md',
    'docs\ownership.md',
    'docs\versions.md',
    'backend\pom.xml',
    'backend\src\main\java\com\bsr\BsrApplication.java',
    'frontend\package.json',
    'frontend\src\app\routing\featureRegistry.ts',
    'frontend\src\shared\http\apiClient.ts',
    'miniprogram\package.json',
    'miniprogram\project.config.json',
    'miniprogram\app.json',
    'docs\contracts\miniprogram.md'
)

foreach ($relative in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $relative) -PathType Leaf)) {
        $failures.Add("Missing required file: $relative")
    }
}

$migrationRoot = Join-Path $root 'backend\src\main\resources\db\migration'
if (Test-Path -LiteralPath $migrationRoot) {
    $migrationFiles = @(Get-ChildItem -LiteralPath $migrationRoot -File -Recurse -ErrorAction Stop)
    $legacyOnlyTables = @(
        'app_user',
        'rental_line',
        'settlement',
        'contractor',
        'contractor_rate',
        'repair_order',
        'work_shift'
    )

    foreach ($migrationFile in $migrationFiles) {
        $migrationText = Get-Content -LiteralPath $migrationFile.FullName -Raw
        foreach ($legacyTable in $legacyOnlyTables) {
            $legacyCreatePattern = "(?im)^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[\x60]?$([regex]::Escape($legacyTable))[\x60]?(?:\s|\()"
            if ($migrationText -match $legacyCreatePattern) {
                $relativeMigration = [System.IO.Path]::GetRelativePath($root, $migrationFile.FullName)
                $failures.Add("Forbidden old 11-table migration artifact found in ${relativeMigration}: $legacyTable")
            }
        }
    }
}

$javaRoot = Join-Path $root 'backend\src\main\java'
$javaFiles = @(Get-ChildItem -LiteralPath $javaRoot -Filter '*.java' -File -Recurse -ErrorAction Stop)
$javaText = ($javaFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
foreach ($forbidden in @('class Settlement', 'class RepairOrder', 'MAINTENANCE', 'RETIRED')) {
    if ($javaText -match [regex]::Escape($forbidden)) {
        $failures.Add("Forbidden old business artifact found in Java source: $forbidden")
    }
}

$dataSourceBeanCount = ([regex]::Matches($javaText, '(?s)@Bean.{0,300}?\bDataSource\b')).Count
if ($dataSourceBeanCount -gt 1) {
    $failures.Add("More than one DataSource bean declaration found: $dataSourceBeanCount")
}

$registry = Get-Content -LiteralPath (Join-Path $root 'frontend\src\app\routing\featureRegistry.ts') -Raw
if ($registry -notmatch "import\.meta\.glob.*features/\*/routes\.ts") {
    $failures.Add('Frontend root router does not discover module routes.')
}

$expectedMiniProgramPages = @(
    'pages/identity/login',
    'pages/customerrequest/home',
    'pages/customerrequest/profile',
    'pages/customerrequest/create',
    'pages/customerrequest/detail',
    'pages/rental/list',
    'pages/rental/detail',
    'pages/rental/confirm',
    'pages/rental/change',
    'pages/rental/invoice'
)
$miniManifest = Get-Content -LiteralPath (Join-Path $root 'miniprogram\app.json') -Raw | ConvertFrom-Json
if (($miniManifest.pages -join '|') -ne ($expectedMiniProgramPages -join '|')) {
    $failures.Add('Mini program root page registration must preserve the original nine pages and approved invoice page.')
}
foreach ($page in $expectedMiniProgramPages) {
    foreach ($extension in @('.ts', '.json', '.wxml', '.wxss')) {
        if (-not (Test-Path -LiteralPath (Join-Path $root "miniprogram\$page$extension") -PathType Leaf)) {
            $failures.Add("Missing mini program page companion file: $page$extension")
        }
    }
}
$miniProject = Get-Content -LiteralPath (Join-Path $root 'miniprogram\project.config.json') -Raw | ConvertFrom-Json
if ($null -ne $miniProject.appid -and -not [string]::IsNullOrWhiteSpace($miniProject.appid) -and
        $miniProject.appid -cnotmatch '^wx[0-9a-f]{16}$') {
    $failures.Add('Mini program AppID must be absent or use the valid owner-supplied WeChat AppID format.')
}
$t03MiniProgramPaths = @('miniprogram\shared\auth', 'miniprogram\shared\session-request')
$hasT03MiniProgramImplementation = $t03MiniProgramPaths | Where-Object {
    Test-Path -LiteralPath (Join-Path $root $_)
}
if ($hasT03MiniProgramImplementation.Count -gt 0 -and
        -not (Test-Path -LiteralPath (Join-Path $root 'docs\contracts\identity.md') -PathType Leaf)) {
    $failures.Add('T03-owned mini program authentication requires docs\contracts\identity.md.')
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Output 'Architecture layout verification passed.'
Write-Output 'No old 11-table business artifacts, second DataSource bean, or uncontracted T03 mini program auth implementation found.'
