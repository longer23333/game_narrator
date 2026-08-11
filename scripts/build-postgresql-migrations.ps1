$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot '..\src\main\resources\db\migration'
$target = Join-Path $PSScriptRoot '..\src\main\resources\db\migration-postgresql'
New-Item -ItemType Directory -Force -Path $target | Out-Null
Get-ChildItem $target -Filter 'V*.sql' | Remove-Item -Force

Get-ChildItem $source -Filter 'V*.sql' | ForEach-Object {
    $name = $_.Name
    if ($name -eq 'V2__backfill_legacy_project_history.sql') {
        $sql = '-- Clean PostgreSQL installations have no legacy H2 rows to backfill.'
    } else {
        $sql = Get-Content -Raw -Encoding UTF8 $_.FullName
        $sql = $sql -replace '\bCLOB\b', 'TEXT'
        $sql = $sql -replace 'ALTER COLUMN failure_message TEXT', 'ALTER COLUMN failure_message TYPE TEXT'
        $sql = $sql -replace 'ALTER COLUMN error_message TEXT', 'ALTER COLUMN error_message TYPE TEXT'
        $sql = $sql -replace 'ADD CONSTRAINT IF NOT EXISTS', 'ADD CONSTRAINT'
        if ($name -eq 'V1__database_v2_foundation.sql') {
            $sql = $sql -replace "MERGE INTO app_user \(id, username, display_name, password_hash, role, status, created_at, updated_at, last_login_at\)\s*KEY \(id\) VALUES \(([^;]+)\);", "INSERT INTO app_user (id, username, display_name, password_hash, role, status, created_at, updated_at, last_login_at) VALUES (`$1) ON CONFLICT (id) DO NOTHING;"
        }
    }
    Set-Content -Encoding UTF8 -NoNewline -Path (Join-Path $target $name) -Value $sql
}
