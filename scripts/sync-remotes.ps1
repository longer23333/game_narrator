param(
    [string]$Branch = ""
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

$requiredRemotes = @("github", "gitee")
$configuredRemotes = @(git -c $safeDirectoryArgument -C $repositoryRoot remote)
foreach ($remote in $requiredRemotes) {
    if ($configuredRemotes -notcontains $remote) {
        throw "Required remote '$remote' is not configured."
    }
}

foreach ($remote in $requiredRemotes) {
    Write-Host "Pushing $Branch to $remote..."
    git -c $safeDirectoryArgument -C $repositoryRoot push $remote "HEAD:refs/heads/$Branch"
    if ($LASTEXITCODE -ne 0) {
        throw "Push to '$remote' failed; synchronization stopped."
    }
}

Write-Host "Synchronized '$Branch' to GitHub and Gitee."
