param(
    [string]$RepoRoot,
    [int]$AndroidApi = 21,
    [switch]$RunGoModTidy,
    [switch]$SkipGomobileInit,
    [switch]$SkipToolBootstrap
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Join-PathParts {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Base,
        [Parameter(Mandatory = $true)]
        [string[]]$ChildParts
    )

    $path = $Base
    foreach ($part in $ChildParts) {
        $path = Join-Path $path $part
    }
    return $path
}

function Resolve-RepositoryRoot {
    param(
        [string]$ExplicitRepoRoot,
        [string]$ScriptRoot
    )

    if ($ExplicitRepoRoot) {
        return (Resolve-Path -LiteralPath $ExplicitRepoRoot).Path
    }

    try {
        $gitRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
        if ($LASTEXITCODE -eq 0 -and $gitRoot) {
            return $gitRoot
        }
    } catch {
    }

    return (Split-Path -Parent $ScriptRoot)
}

function Resolve-ToolPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [string[]]$Candidates = @()
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    return $null
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory
    )

    Push-Location -LiteralPath $WorkingDirectory
    try {
        & $FilePath @ArgumentList
        if ($LASTEXITCODE -ne 0) {
            throw "'$FilePath $($ArgumentList -join ' ')' failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath $env:JAVA_HOME)) {
        return (Resolve-Path -LiteralPath $env:JAVA_HOME).Path
    }

    $javacPath = Resolve-ToolPath -Name "javac"
    if ($javacPath) {
        return (Split-Path -Parent (Split-Path -Parent $javacPath))
    }

    $jdkRoots = @(
        $(if ($env:ProgramFiles) { Join-Path $env:ProgramFiles "Eclipse Adoptium" }),
        $(if ($env:ProgramFiles) { Join-Path $env:ProgramFiles "Java" })
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    foreach ($jdkRoot in $jdkRoots) {
        $candidate = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Where-Object { Test-Path -LiteralPath (Join-PathParts $_.FullName @("bin", "javac.exe")) } |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }

    throw "Unable to find javac. Set JAVA_HOME or install a JDK first."
}

function Resolve-AndroidSdkRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $repoParent = Split-Path -Parent $RepositoryRoot
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        $(if ($env:LOCALAPPDATA) { Join-PathParts $env:LOCALAPPDATA @("Android", "Sdk") }),
        (Join-Path $HOME "android-sdk"),
        (Join-PathParts $HOME @("Android", "Sdk")),
        $(if ($repoParent) { Join-Path $repoParent "android-sdk" })
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Unable to find Android SDK. Set ANDROID_SDK_ROOT or ANDROID_HOME."
}

function Resolve-AndroidNdkRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SdkRoot,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $envCandidates = @($env:ANDROID_NDK_HOME, $env:NDK_HOME) | Where-Object { $_ }
    foreach ($candidate in $envCandidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $versionedNdkRoot = Join-Path $SdkRoot "ndk"
    if (Test-Path -LiteralPath $versionedNdkRoot) {
        $latestNdk = Get-ChildItem -LiteralPath $versionedNdkRoot -Directory |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($latestNdk) {
            return $latestNdk.FullName
        }
    }

    $ndkBundle = Join-Path $SdkRoot "ndk-bundle"
    if (Test-Path -LiteralPath $ndkBundle) {
        return (Resolve-Path -LiteralPath $ndkBundle).Path
    }

    $repoParent = Split-Path -Parent $RepositoryRoot
    if ($repoParent) {
        $siblingNdk = Get-ChildItem -LiteralPath $repoParent -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "android-ndk-*" } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($siblingNdk) {
            return $siblingNdk.FullName
        }
    }

    throw "Unable to find Android NDK. Set ANDROID_NDK_HOME/NDK_HOME or install an NDK under the Android SDK directory."
}

function Ensure-AndroidLibAssets {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AndroidLibRoot
    )

    $assetsDir = Join-Path $AndroidLibRoot "assets"
    New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null

    $assetDownloads = @(
        @{
            Name = "geoip.dat"
            Url = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
        },
        @{
            Name = "geosite.dat"
            Url = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
        },
        @{
            Name = "geoip-only-cn-private.dat"
            Url = "https://raw.githubusercontent.com/Loyalsoldier/geoip/release/geoip-only-cn-private.dat"
        }
    )

    foreach ($asset in $assetDownloads) {
        $target = Join-Path $assetsDir $asset.Name
        if (Test-Path -LiteralPath $target) {
            continue
        }

        Write-Host "Downloading $($asset.Name)..."
        Invoke-WebRequest -Uri $asset.Url -OutFile $target
        if (-not (Test-Path -LiteralPath $target)) {
            throw "Failed to download $($asset.Name) to '$target'."
        }
    }
}

function Ensure-GoMobileTools {
    param(
        [Parameter(Mandatory = $true)]
        [string]$GoExe,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [switch]$NoBootstrap
    )

    $gopath = (& $GoExe env GOPATH).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $gopath) {
        throw "Unable to resolve GOPATH from go env GOPATH."
    }

    $goBinDir = Join-Path $gopath "bin"
    $gomobileCandidates = @(
        (Join-Path $goBinDir "gomobile.exe"),
        (Join-Path $goBinDir "gomobile")
    )
    $gobindCandidates = @(
        (Join-Path $goBinDir "gobind.exe"),
        (Join-Path $goBinDir "gobind")
    )

    $gomobile = Resolve-ToolPath -Name "gomobile" -Candidates $gomobileCandidates
    $gobind = Resolve-ToolPath -Name "gobind" -Candidates $gobindCandidates

    if ((-not $gomobile -or -not $gobind) -and $NoBootstrap) {
        throw "gomobile/gobind not found. Install them first or rerun without -SkipToolBootstrap."
    }

    if (-not $gomobile) {
        Write-Host "Installing gomobile..."
        Invoke-Checked -FilePath $GoExe -ArgumentList @("install", "golang.org/x/mobile/cmd/gomobile@latest") -WorkingDirectory $RepositoryRoot
        $gomobile = Resolve-ToolPath -Name "gomobile" -Candidates $gomobileCandidates
    }

    if (-not $gobind) {
        Write-Host "Installing gobind..."
        Invoke-Checked -FilePath $GoExe -ArgumentList @("install", "golang.org/x/mobile/cmd/gobind@latest") -WorkingDirectory $RepositoryRoot
        $gobind = Resolve-ToolPath -Name "gobind" -Candidates $gobindCandidates
    }

    if (-not $gomobile -or -not $gobind) {
        throw "Unable to resolve gomobile/gobind after bootstrap."
    }

    return @{
        GOPATH = $gopath
        GoMobile = $gomobile
        GoBind = $gobind
    }
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-RepositoryRoot -ExplicitRepoRoot $RepoRoot -ScriptRoot $scriptRoot
$androidLibRoot = Join-Path $RepoRoot "AndroidLibXrayLite"
$appLibsDir = Join-PathParts $RepoRoot @("V2rayNG", "app", "libs")
$artifactDir = Join-PathParts $RepoRoot @("artifacts", "libv2ray")
$artifactAar = Join-Path $artifactDir "libv2ray.aar"
$artifactSourcesJar = Join-Path $artifactDir "libv2ray-sources.jar"
$targetAar = Join-Path $appLibsDir "libv2ray.aar"
$targetSourcesJar = Join-Path $appLibsDir "libv2ray-sources.jar"

if (-not (Test-Path -LiteralPath $androidLibRoot)) {
    throw "AndroidLibXrayLite submodule not found at '$androidLibRoot'."
}

Ensure-AndroidLibAssets -AndroidLibRoot $androidLibRoot

$goExe = Resolve-ToolPath -Name "go"
if (-not $goExe) {
    throw "Unable to find Go. Install Go and make sure 'go' is available in PATH."
}

$gomobileTools = Ensure-GoMobileTools -GoExe $goExe -RepositoryRoot $RepoRoot -NoBootstrap:$SkipToolBootstrap
$javaHome = Resolve-JavaHome
$sdkRoot = Resolve-AndroidSdkRoot -RepositoryRoot $RepoRoot
$ndkRoot = Resolve-AndroidNdkRoot -SdkRoot $sdkRoot -RepositoryRoot $RepoRoot

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_NDK_HOME = $ndkRoot
$env:NDK_HOME = $ndkRoot
$pathParts = @(
    (Join-Path $gomobileTools.GOPATH "bin"),
    (Join-Path $javaHome "bin"),
    (Join-Path $sdkRoot "platform-tools"),
    $env:PATH
) | Where-Object { $_ }
$env:PATH = [string]::Join([System.IO.Path]::PathSeparator, $pathParts)

New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
New-Item -ItemType Directory -Path $appLibsDir -Force | Out-Null

foreach ($path in @($artifactAar, $artifactSourcesJar, $targetSourcesJar)) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force
    }
}

Write-Host "Repo root      : $RepoRoot"
Write-Host "JAVA_HOME      : $javaHome"
Write-Host "ANDROID SDK    : $sdkRoot"
Write-Host "ANDROID NDK    : $ndkRoot"
Write-Host "gomobile       : $($gomobileTools.GoMobile)"
Write-Host "Output AAR     : $artifactAar"
Write-Host "App libs target: $targetAar"

if ($RunGoModTidy) {
    Write-Host "Running go mod tidy..."
    Invoke-Checked -FilePath $goExe -ArgumentList @("mod", "tidy", "-v") -WorkingDirectory $androidLibRoot
}

if (-not $SkipGomobileInit) {
    Write-Host "Running gomobile init..."
    Invoke-Checked -FilePath $gomobileTools.GoMobile -ArgumentList @("init", "-v") -WorkingDirectory $RepoRoot
}

Write-Host "Building libv2ray.aar..."
Invoke-Checked -FilePath $gomobileTools.GoMobile -ArgumentList @(
    "bind",
    "-v",
    "-androidapi",
    $AndroidApi.ToString(),
    "-ldflags=-s -w",
    "-o",
    $artifactAar,
    "./"
) -WorkingDirectory $androidLibRoot

if (-not (Test-Path -LiteralPath $artifactAar)) {
    throw "gomobile completed without producing '$artifactAar'."
}

Copy-Item -LiteralPath $artifactAar -Destination $targetAar -Force

Write-Host "libv2ray.aar copied to $targetAar"
if (Test-Path -LiteralPath $artifactSourcesJar) {
    Write-Host "Sources JAR available at $artifactSourcesJar"
}
