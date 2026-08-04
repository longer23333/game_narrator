param(
    [string]$Branch = "",
    [string[]]$Remotes = @(),
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$safeDirectoryArgument = "safe.directory=$($repositoryRoot.Replace('\\', '/'))"
if ([string]::IsNullOrWhiteSpace($Branch)) {
    $Branch = git -c $safeDirectoryArgument -C $repositoryRoot branch --show-current
}
if ([string]::IsNullOrWhiteSpace($Branch)) {
    throw "Cannot synchronize a detached HEAD. Pass -Branch explicitly."
}

if ($Remotes.Count -eq 0 -and -not [string]::IsNullOrWhiteSpace($env:GAME_NARRATOR_SYNC_REMOTES)) {
    $Remotes = @($env:GAME_NARRATOR_SYNC_REMOTES -split ',' | ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}
$requiredRemotes = if ($Remotes.Count -gt 0) { @($Remotes) } else { @("github", "gitee") }
$configuredRemotes = @(git -c $safeDirectoryArgument -C $repositoryRoot remote)
foreach ($remote in $requiredRemotes) {
    if ($configuredRemotes -notcontains $remote) {
        throw "Required remote '$remote' is not configured."
    }
}

foreach ($remote in $requiredRemotes) {
    if ($DryRun) {
        $remoteUrl = git -c $safeDirectoryArgument -C $repositoryRoot remote get-url --push $remote
        Write-Host "[dry-run] Would push HEAD to $remote ($remoteUrl), branch '$Branch'."
        continue
    }
    Write-Host "Pushing $Branch to $remote..."
    git -c $safeDirectoryArgument -C $repositoryRoot push $remote "HEAD:refs/heads/$Branch"
    if ($LASTEXITCODE -ne 0) {
        throw "Push to '$remote' failed; synchronization stopped."
    }
}

if ($DryRun) {
    Write-Host "Dry run complete; nothing was pushed."
} else {
    Write-Host "Synchronized '$Branch' to: $($requiredRemotes -join ', ')."
}
