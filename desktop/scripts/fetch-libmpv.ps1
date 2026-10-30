# Fetches libmpv-2.dll (shinchiro/mpv-winbuild-cmake) into desktop/resources/windows.
# The DLL is ~120 MB, so it is gitignored rather than committed.
#
#   powershell -ExecutionPolicy Bypass -File desktop/scripts/fetch-libmpv.ps1
#   ... -Release 20260903 -Build mpv-dev-x86_64-20260903-git-69e63f425a   one build in particular
#
# Without -Release it takes the newest release's standard x86_64 dev build. shinchiro prunes old
# releases, so a build pinned here stopped existing and broke the Windows release; asking for the
# newest one each time can't go stale that way.

param(
    [string]$Release = "",
    [string]$Build = ""
)

$ErrorActionPreference = "Stop"

$dest = Join-Path $PSScriptRoot "..\resources\windows"
$tmp = Join-Path ([IO.Path]::GetTempPath()) "exhale-libmpv"
New-Item -ItemType Directory -Force $dest, $tmp | Out-Null

if ($Release -and $Build) {
    $url = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/$Release/$Build.7z"
} else {
    $headers = @{ 'User-Agent' = 'exhale-build'; 'Accept' = 'application/vnd.github+json' }
    # In GitHub Actions the workflow's token lifts the API's rate limit; locally none is needed.
    if ($env:GITHUB_TOKEN) { $headers['Authorization'] = "Bearer $env:GITHUB_TOKEN" }
    $latest = Invoke-RestMethod "https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest" -Headers $headers
    # The standard build (not the x86_64-v3 one, which needs a newer CPU).
    $asset = $latest.assets | Where-Object { $_.name -match '^mpv-dev-x86_64-\d+-git-[0-9a-f]+\.7z$' } | Select-Object -First 1
    if (-not $asset) { throw "No mpv-dev-x86_64 build in shinchiro's release $($latest.tag_name)" }
    $url = $asset.browser_download_url
    $Build = $asset.name -replace '\.7z$', ''
    Write-Host "Newest libmpv: $($latest.tag_name), $($asset.name)"
}

$archive = Join-Path $tmp "$Build.7z"
Invoke-WebRequest $url -OutFile $archive

# Windows' bundled bsdtar reads 7z, so no 7-Zip install is needed.
& "$env:SystemRoot\System32\tar.exe" -xf $archive -C $tmp libmpv-2.dll
Copy-Item (Join-Path $tmp "libmpv-2.dll") $dest -Force

Write-Host "libmpv-2.dll -> $((Resolve-Path $dest).Path)"
