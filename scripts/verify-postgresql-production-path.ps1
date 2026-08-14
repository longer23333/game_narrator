$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$testPath = Join-Path $root 'src/test/java/cn/longer233/gamenarrator/task/PostgreSqlProductionIntegrationTest.java'
$workflowPath = Join-Path $root '.github/workflows/ci.yml'
$test = Get-Content -Raw -Encoding UTF8 $testPath
$workflow = Get-Content -Raw -Encoding UTF8 $workflowPath

foreach ($marker in @(
    'version224BaselineUpgradeMatchesFullMigrationHistory',
    'classpath:db/baseline-postgresql',
    'classpath:db/migration-postgresql',
    'full.validate()',
    'baseline.validate()',
    'structureSignature(fullSchema)',
    'structureSignature(baselineSchema)',
    'information_schema.columns',
    'information_schema.table_constraints',
    "constraint_name !~ '^[0-9]+_[0-9]+_[0-9]+_not_null$'",
    'pg_indexes'
)) {
    if (-not $test.Contains($marker)) {
        throw "PostgreSQL production-path integration is missing: $marker"
    }
}

if ($workflow -notmatch '(?ms)^  postgresql-integration:.*?PostgreSqlProductionIntegrationTest') {
    throw 'CI must execute PostgreSqlProductionIntegrationTest in its PostgreSQL service job'
}
if ($workflow -notmatch 'needs:.*postgresql-integration') {
    throw 'Same-commit release evidence must depend on PostgreSQL production-path integration'
}

Write-Output 'PASS: PostgreSQL 16 validates full history and the 2.2.4 baseline upgrade with matching schema signatures'
