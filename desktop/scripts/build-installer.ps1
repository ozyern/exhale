# Builds Exhale-Setup-<version>.exe: Exhale's own installer (desktop/installer/ExhaleSetup.cs) with the
# app image packed inside it. Needs nothing beyond Windows itself — the C# compiler and WPF ship with
# .NET Framework 4.8 — plus the JBR SDK the Gradle packaging uses (see desktop/build.gradle.kts).
#
#   powershell -ExecutionPolicy Bypass -File desktop\scripts\build-installer.ps1
#   ... -SkipApp    reuse the app image already in desktop\build\compose\binaries\main\app
#   ... -Unsigned   don't sign anything, even when a signing setup is available
#
# In two stages, for signing that happens somewhere else (SignPath, in the release workflow):
#   ... -Stage Prepare   app image and uninstaller into desktop\build\installer\stage, ready to be signed
#   ... -Stage Package   setup.exe from that stage, as it is when it comes back signed
# Without -Stage both run, one after the other.

param(
    [switch]$SkipApp,
    [switch]$Unsigned,
    [ValidateSet('All', 'Prepare', 'Package')][string]$Stage = 'All'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$desktop = Join-Path $root 'desktop'
$out = Join-Path $desktop 'build\installer'
$stageDir = Join-Path $out 'stage'
# One version for Android and Windows: they share GitHub releases, and the Windows updater compares
# the release tag against the version the app was built with.
$version = (Select-String -Path "$root\app\build.gradle.kts" -Pattern '^\s*versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
if (-not $version) { throw "No versionName in app\build.gradle.kts" }
New-Item -ItemType Directory -Force $out | Out-Null

# Code signing, when there's something to sign with. Without it every build is unsigned: SmartScreen
# warns, and Windows 11's Smart App Control refuses to run it at all.
#   $env:EXHALE_SIGN_ENDPOINT, _ACCOUNT, _PROFILE   Microsoft Artifact Signing (Trusted Signing). signtool
#                              and the Artifact Signing client are looked for in $env:EXHALE_SIGN_TOOLS
#                              (default %LOCALAPPDATA%\exhale-signing: the Microsoft.Windows.SDK.BuildTools
#                              and Microsoft.ArtifactSigning.Client NuGet packages, unpacked into sdk\ and
#                              dlib\). Sign-in is Azure's: az login, AZURE_CLIENT_ID/_TENANT_ID/_CLIENT_SECRET,
#                              or a browser window.
#   $env:EXHALE_SIGN_PFX       a .pfx file (and $env:EXHALE_SIGN_PASSWORD, if it has one), signed with signtool
#   otherwise                  a code-signing certificate in the current user's store whose subject matches
#                              $env:EXHALE_SIGN_SUBJECT (default "CN=ozyern"), signed with PowerShell
# A self-signed certificate only helps the PC that trusts it: see desktop\scripts\trust-signing-cert.ps1.
function Get-SigningCert {
    $subject = if ($env:EXHALE_SIGN_SUBJECT) { $env:EXHALE_SIGN_SUBJECT } else { 'CN=ozyern' }
    Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert -ErrorAction SilentlyContinue |
        Where-Object { $_.Subject -eq $subject -and $_.HasPrivateKey -and $_.NotAfter -gt (Get-Date) } |
        Sort-Object NotAfter -Descending | Select-Object -First 1
}

function Find-SignTool {
    $tools = if ($env:EXHALE_SIGN_TOOLS) { $env:EXHALE_SIGN_TOOLS } else { Join-Path $env:LOCALAPPDATA 'exhale-signing' }
    @(Get-ChildItem "$tools\sdk\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue) +
    @(Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue) |
        Sort-Object FullName -Descending | Select-Object -First 1
}

function Invoke-Sign {
    param([string[]]$Paths)
    if ($Unsigned -or -not $Paths) { return }
    # jpackage leaves its launcher read-only.
    foreach ($path in $Paths) { Set-ItemProperty -LiteralPath $path -Name IsReadOnly -Value $false }

    if ($env:EXHALE_SIGN_ENDPOINT -and $env:EXHALE_SIGN_ACCOUNT -and $env:EXHALE_SIGN_PROFILE) {
        $tools = if ($env:EXHALE_SIGN_TOOLS) { $env:EXHALE_SIGN_TOOLS } else { Join-Path $env:LOCALAPPDATA 'exhale-signing' }
        $dlib = Join-Path $tools 'dlib\bin\x64\Azure.CodeSigning.Dlib.dll'
        $tool = Find-SignTool
        if (-not $tool) { throw "No signtool.exe under $tools\sdk or the Windows SDK" }
        if (-not (Test-Path $dlib)) { throw "No Artifact Signing client at $dlib" }
        $metadata = Join-Path $out 'signing-metadata.json'
        @{ Endpoint = $env:EXHALE_SIGN_ENDPOINT; CodeSigningAccountName = $env:EXHALE_SIGN_ACCOUNT; CertificateProfileName = $env:EXHALE_SIGN_PROFILE } |
            ConvertTo-Json | Set-Content -Encoding ASCII $metadata
        # Every file in one run, so Azure's sign-in is asked for once.
        & $tool.FullName sign /fd SHA256 /tr 'http://timestamp.acs.microsoft.com' /td SHA256 /dlib $dlib /dmdf $metadata @Paths
        if ($LASTEXITCODE -ne 0) { throw "Artifact Signing failed" }
        return
    }

    $pfx = $env:EXHALE_SIGN_PFX
    if ($pfx) {
        if (-not (Test-Path $pfx)) { Write-Host "EXHALE_SIGN_PFX is set but $pfx isn't there; leaving the build unsigned."; return }
        $tool = Find-SignTool
        if (-not $tool) { Write-Host "No signtool.exe (install the Windows SDK); leaving the build unsigned."; return }
        $signArgs = @('sign', '/fd', 'SHA256', '/f', $pfx)
        if ($env:EXHALE_SIGN_PASSWORD) { $signArgs += @('/p', $env:EXHALE_SIGN_PASSWORD) }
        # A timestamp keeps the signature valid after the certificate itself expires.
        $signArgs += @('/tr', 'http://timestamp.digicert.com', '/td', 'SHA256')
        & $tool.FullName @signArgs @Paths
        if ($LASTEXITCODE -ne 0) { throw "Signing failed" }
        return
    }

    $cert = Get-SigningCert
    if (-not $cert) { Write-Host "No signing certificate; leaving the build unsigned."; return }
    foreach ($path in $Paths) {
        $result = Set-AuthenticodeSignature -FilePath $path -Certificate $cert -HashAlgorithm SHA256 -TimestampServer 'http://timestamp.digicert.com' -ErrorAction SilentlyContinue
        # No network for the timestamp is no reason to ship unsigned.
        if (-not $result -or -not $result.SignerCertificate) {
            $result = Set-AuthenticodeSignature -FilePath $path -Certificate $cert -HashAlgorithm SHA256
        }
        if (-not $result.SignerCertificate) { throw "Signing failed for $path : $($result.StatusMessage)" }
    }
}

# Every native file the app loads. Smart App Control judges each executable and library, not just the
# installer, so one unsigned runtime DLL is enough to stop the app starting. Files someone else already
# signed (the runtime's Microsoft DLLs) keep their own signature.
function Invoke-SignTree {
    param([string]$Root)
    $todo = Get-ChildItem $Root -Recurse -File | Where-Object { $_.Extension -in '.exe', '.dll' } |
        Where-Object { (Get-AuthenticodeSignature $_.FullName).Status -eq 'NotSigned' } | ForEach-Object FullName
    if ($todo -and -not $Unsigned) {
        Write-Host ("Signing {0} native files..." -f @($todo).Count)
        Invoke-Sign @($todo)
    }
}

# Versions setup shows in its "What's included" table, read from what is actually being packed.
function Write-VersionFile {
    param([string]$Image)
    $runtime = (Select-String -Path "$Image\runtime\release" -Pattern '^JAVA_VERSION="([^"]+)"').Matches[0].Groups[1].Value
    $mpv = ''
    $mpvDll = Get-ChildItem $Image -Recurse -Filter 'libmpv-2.dll' | Select-Object -First 1
    if ($mpvDll -and $mpvDll.VersionInfo.FileVersion -match '(\d+\.\d+(\.\d+)?)') { $mpv = $Matches[1] }
    Set-Content -Encoding UTF8 "$out\Version.cs" ("namespace ExhaleSetup { static partial class Product { " +
        "public const string Version = `"$version`"; public const string RuntimeVersion = `"$runtime`"; public const string MpvVersion = `"$mpv`"; } }")
}

$fw = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319"
$csc = "$fw\csc.exe"
$refs = @(
    "$fw\WPF\PresentationFramework.dll", "$fw\WPF\PresentationCore.dll", "$fw\WPF\WindowsBase.dll",
    "$fw\System.Xaml.dll", "$fw\System.IO.Compression.dll", "$fw\System.IO.Compression.FileSystem.dll",
    "$fw\System.Windows.Forms.dll", "$fw\Microsoft.CSharp.dll", "$fw\System.Core.dll"
) | ForEach-Object { "/r:$_" }
$common = @(
    '/nologo', '/target:winexe', '/optimize+', '/platform:anycpu', '/utf8output',
    "/win32icon:$desktop\icons\exhale.ico",
    "/win32manifest:$desktop\installer\app.manifest",
    "/resource:$desktop\src\main\resources\exhale.png,exhale.png",
    "/resource:$desktop\src\main\resources\font\linotte.ttf,linotte.ttf"
)
$sources = @("$desktop\installer\ExhaleSetup.cs", "$out\Version.cs")
$image = Join-Path $stageDir 'Exhale'
$uninstaller = Join-Path $stageDir 'Uninstall Exhale.exe'

if ($Stage -ne 'Package') {
    if (-not $SkipApp) {
        if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
        & "$root\gradlew.bat" -p $root :desktop:createDistributable --console=plain
        if ($LASTEXITCODE -ne 0) { throw "createDistributable failed" }
    }
    $built = Join-Path $desktop 'build\compose\binaries\main\app\Exhale'
    if (-not (Test-Path "$built\Exhale.exe")) { throw "No app image at $built" }

    # The stage is what gets signed and packed: a copy, so a test launch of the build can't leave
    # anything in it.
    if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
    New-Item -ItemType Directory -Force $stageDir | Out-Null
    Copy-Item $built $image -Recurse
    # A class-data archive left by a test launch belongs to that machine; don't ship it.
    Remove-Item "$image\app\exhale.jsa" -ErrorAction SilentlyContinue
    Write-VersionFile $image

    Write-Host "Compiling the uninstaller..."
    & $csc @common @refs '/define:UNINSTALLER' "/out:$uninstaller" @sources
    if ($LASTEXITCODE -ne 0) { throw "Uninstaller didn't compile" }

    # Signed before they go into the payload, so the installed app is signed too.
    Invoke-SignTree $stageDir
}

if ($Stage -ne 'Prepare') {
    if (-not (Test-Path "$image\Exhale.exe") -or -not (Test-Path $uninstaller)) { throw "Nothing staged in $stageDir; run -Stage Prepare first" }
    Write-VersionFile $image

    Write-Host "Packing the app image..."
    $zip = Join-Path $out 'payload.zip'
    Remove-Item $zip -ErrorAction SilentlyContinue
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($image, $zip, [IO.Compression.CompressionLevel]::Optimal, $false)

    Write-Host "Compiling setup..."
    $setup = Join-Path $out "Exhale-Setup-$version.exe"
    & $csc @common @refs "/resource:$uninstaller,uninstaller.exe" "/resource:$zip,payload.zip" "/out:$setup" @sources
    if ($LASTEXITCODE -ne 0) { throw "Setup didn't compile" }
    Invoke-Sign @($setup)

    Write-Host ("Built {0} ({1:N0} MB)" -f $setup, ((Get-Item $setup).Length / 1MB))
}
