param(
  [Parameter(Mandatory=$true)][string]$Commit,
  [Parameter(Mandatory=$true)][string]$RunUrl,
  [Parameter(Mandatory=$true)][string]$RunId,
  [string]$Output = 'artifacts/ci-release-result.json'
)
$ErrorActionPreference='Stop'
if($Commit -notmatch '^[0-9a-f]{40}$'){throw 'Commit must be a full SHA-1 hash'}
if($RunUrl -notmatch '^https://github\.com/.+/actions/runs/\d+$'){throw 'RunUrl must identify a GitHub Actions run'}
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$path=[IO.Path]::GetFullPath((Join-Path $root $Output))
$artifactRoot=[IO.Path]::GetFullPath((Join-Path $root 'artifacts'))
if(-not $path.StartsWith($artifactRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'CI evidence output must stay under artifacts'}
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))|Out-Null
$document=[ordered]@{
 schemaVersion=1;status='passed';commit=$Commit.ToLowerInvariant();completedAt=[DateTimeOffset]::UtcNow.ToString('o');runId=$RunId;runUrl=$RunUrl
 gates=[ordered]@{migrationBaseline='passed';documentation='passed';frontend='passed';android='passed';androidEmulator='passed';backend='passed';performanceBaseline='passed'}
}
$json=$document|ConvertTo-Json -Depth 6
[IO.File]::WriteAllText($path,$json+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
Write-Host "CI release evidence created: $path"
