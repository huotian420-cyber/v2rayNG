param(
    [string]$OfficialRepo = "https://github.com/2dust/v2rayNG.git",
    [string]$UpstreamRemote = "upstream",
    [string]$UpstreamBranch = "master",
    [string]$TargetBranch = "master",
    [switch]$FetchOnly,
    [switch]$PushOrigin,
    [switch]$RunSignedBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args
    )

    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if (-not $repoRoot) {
    throw "Unable to resolve git repository root."
}

Set-Location -LiteralPath $repoRoot

$trackedChanges = (& git status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect git status."
}
if ($trackedChanges) {
    throw "Tracked changes detected. Commit or stash them before syncing upstream."
}

$remoteNames = (& git remote)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to enumerate git remotes."
}

if ($remoteNames -notcontains $UpstreamRemote) {
    Invoke-Git -Args @("remote", "add", $UpstreamRemote, $OfficialRepo)
    Write-Host "Added remote $UpstreamRemote -> $OfficialRepo"
    $remoteUrl = $OfficialRepo
} else {
    $remoteUrl = (& git remote get-url $UpstreamRemote).Trim()
}

if ($remoteUrl.Trim() -ne $OfficialRepo) {
    throw "Remote '$UpstreamRemote' points to '$($remoteUrl.Trim())', expected '$OfficialRepo'."
}

Invoke-Git -Args @("fetch", "--prune", "origin")
Invoke-Git -Args @("fetch", "--prune", $UpstreamRemote)

if ($FetchOnly) {
    Write-Host "Fetched origin and $UpstreamRemote successfully."
    exit 0
}

Invoke-Git -Args @("checkout", $TargetBranch)
Invoke-Git -Args @("merge", "--ff-only", "origin/$TargetBranch")

$beforeHead = (& git rev-parse HEAD).Trim()
Invoke-Git -Args @("merge", "--no-ff", "--no-edit", "$UpstreamRemote/$UpstreamBranch")
$afterHead = (& git rev-parse HEAD).Trim()

if ($beforeHead -eq $afterHead) {
    Write-Host "No upstream changes to merge."
} else {
    Write-Host "Merged $UpstreamRemote/$UpstreamBranch into $TargetBranch."
}

if ($PushOrigin) {
    Invoke-Git -Args @("push", "origin", $TargetBranch)
    Write-Host "Pushed $TargetBranch to origin."
}

if ($RunSignedBuild) {
    & gh workflow run build-fdroid-release.yml --ref $TargetBranch
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to trigger build-fdroid-release.yml."
    }
    Write-Host "Triggered build-fdroid-release.yml for $TargetBranch."
}
