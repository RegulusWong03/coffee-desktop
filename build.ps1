param([switch]$BuildInstaller, [switch]$DebugPreview, [string]$ReleaseTag)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$localDotnet = Join-Path $root '.tools\dotnet\dotnet.exe'
$dotnet = if (Test-Path -LiteralPath $localDotnet) { $localDotnet } else { (Get-Command dotnet -ErrorAction SilentlyContinue).Source }
$project = Join-Path $root 'src\Coffee.Desktop\Coffee.Desktop.csproj'
$configuration = if ($DebugPreview) { 'Debug' } else { 'Release' }
$publish = if ($DebugPreview) { Join-Path $root '.tools\qa-preview' } else { Join-Path $root 'release\CoffeeDesktop-win-x64' }
if ($BuildInstaller -and $DebugPreview) { throw 'BuildInstaller and DebugPreview cannot be used together.' }
$projectXml = [xml](Get-Content -LiteralPath $project -Raw)
$appVersion = [string]$projectXml.Project.PropertyGroup.Version
$semVerPattern = '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$'
function Test-SemVer([string]$value, [string]$pattern) {
    if ($value -cnotmatch $pattern) { return $false }
    foreach ($identifier in $Matches[4] -split '\.') {
        if ($identifier -match '^\d+$' -and $identifier.Length -gt 1 -and $identifier.StartsWith('0')) { return $false }
    }
    return $true
}
if ([string]::IsNullOrWhiteSpace($appVersion) -or -not (Test-SemVer $appVersion $semVerPattern)) {
    throw "Project Version must be a semantic version (MAJOR.MINOR.PATCH): $appVersion"
}
if ($ReleaseTag) {
    if ($ReleaseTag -cnotmatch '^v(.+)$' -or -not (Test-SemVer $Matches[1] $semVerPattern)) {
        throw "Release tag must be a semantic version prefixed with 'v': $ReleaseTag"
    }
    if ($ReleaseTag.Substring(1) -cne $appVersion) {
        throw "Release tag $ReleaseTag does not match project version $appVersion. Update the project Version before tagging."
    }
}
if (-not $dotnet) { throw 'No .NET SDK executable found. Install the SDK from global.json or provide .tools\dotnet\dotnet.exe.' }
$offlineFeed = Join-Path $root '.tools\nuget-feed'
New-Item -ItemType Directory -Force -Path $offlineFeed | Out-Null
& $dotnet restore $project -r win-x64 -p:NuGetAudit=false --source $offlineFeed --verbosity minimal
if ($LASTEXITCODE -ne 0) {
    $officialFeed = 'https://pkgs.dev.azure.com/dnceng/public/_packaging/dotnet-public/nuget/v3/index.json'
    & $dotnet restore $project -r win-x64 -p:NuGetAudit=false --source $officialFeed --verbosity minimal
}
if ($LASTEXITCODE -ne 0) { throw "dotnet restore failed ($LASTEXITCODE)" }
& $dotnet publish $project -c $configuration -r win-x64 --self-contained true --no-restore -p:PublishSingleFile=false -o $publish
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed ($LASTEXITCODE)" }
$exe = Join-Path $publish 'CoffeeDesktop.exe'
if (-not (Test-Path $exe)) { throw 'Publish output is missing CoffeeDesktop.exe' }
if ($BuildInstaller) {
    $localNsis = Join-Path $root '.tools\nsis\nsis-3.13\Bin\makensis.exe'
    $systemNsis = Join-Path ${env:ProgramFiles(x86)} 'NSIS\makensis.exe'
    $makensis = if (Test-Path -LiteralPath $localNsis) { $localNsis } elseif (Test-Path -LiteralPath $systemNsis) { $systemNsis } else { (Get-Command makensis.exe -ErrorAction SilentlyContinue).Source }
    if ($makensis) {
        $installedNsisVersion = (& $makensis /VERSION | Select-Object -Last 1).Trim()
        if ($installedNsisVersion -notmatch '^v?3\.13$') { $makensis = $null }
    }
    if (-not $makensis) {
        $nsisRoot = Join-Path $root '.tools\nsis'
        $archive = Join-Path $root '.tools\nsis-3.13.zip'
        $nsisVersionRoot = Join-Path $nsisRoot 'nsis-3.13'
        $expectedSha256 = 'BA63DFFC4410EE89193E1CB5A41989991BD77C61068DA17E3156D136B7B0B3D8'
        New-Item -ItemType Directory -Force -Path $nsisRoot | Out-Null
        if (-not (Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expectedSha256) {
            Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
            $nsisUrl = 'https://sf-eu-introserv-1.dl.sourceforge.net/project/nsis/NSIS%203/3.13/nsis-3.13.zip'
            $downloaded = $false
            for ($attempt = 1; $attempt -le 3 -and -not $downloaded; $attempt++) {
                try {
                    Invoke-WebRequest -Uri $nsisUrl -OutFile $archive -MaximumRedirection 5
                    $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
                    if ($actualSha256 -ne $expectedSha256) { throw "NSIS 3.13 SHA256 mismatch: $actualSha256" }
                    if ((Get-Item -LiteralPath $archive).Length -ne 2363925) { throw 'NSIS 3.13 archive has an unexpected length.' }
                    $archiveStream = [System.IO.File]::OpenRead($archive)
                    try {
                        $zipMagic = [byte[]]::new(4)
                        if ($archiveStream.Read($zipMagic, 0, $zipMagic.Length) -ne $zipMagic.Length -or $zipMagic[0] -ne 0x50 -or $zipMagic[1] -ne 0x4B) {
                            throw 'NSIS 3.13 response is not a ZIP archive.'
                        }
                    } finally {
                        $archiveStream.Dispose()
                    }
                    $downloaded = $true
                } catch {
                    Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
                    if ($attempt -eq 3) { throw }
                    Start-Sleep -Seconds ([Math]::Pow(2, $attempt))
                }
            }
        }
        if (-not (Test-Path -LiteralPath (Join-Path $nsisVersionRoot 'Bin\makensis.exe'))) {
            Expand-Archive -LiteralPath $archive -DestinationPath $nsisRoot -Force
        }
        $makensis = Join-Path $nsisVersionRoot 'Bin\makensis.exe'
    }
    if (-not (Test-Path -LiteralPath $makensis)) { throw "NSIS makensis.exe not found: $makensis" }
    $files = Get-ChildItem -LiteralPath $publish -File -Recurse | Sort-Object FullName
    $directories = Get-ChildItem -LiteralPath $publish -Directory -Recurse | Sort-Object { $_.FullName.Length } -Descending
    $manifest = [System.Collections.Generic.List[string]]::new()
    $manifest.Add('!macro RemoveInstalledFiles')
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($publish.Length).TrimStart([char]92).Replace('/', [char]92)
        if ($relative -match '["$]') { throw "Unsupported character in published file path: $relative" }
        $manifest.Add(('  Delete "$INSTDIR\{0}"' -f $relative))
    }
    foreach ($directory in $directories) {
        $relative = $directory.FullName.Substring($publish.Length).TrimStart([char]92).Replace('/', [char]92)
        if ($relative -match '["$]') { throw "Unsupported character in published directory path: $relative" }
        $manifest.Add(('  RMDir "$INSTDIR\{0}"' -f $relative))
    }
    $manifest.Add('!macroend')
    [System.IO.File]::WriteAllLines((Join-Path $root 'installer\generated-files.nsh'), $manifest, [System.Text.UTF8Encoding]::new($false))
    $numericVersion = ($appVersion -split '[+-]')[0].Split('.')
    & $makensis "/DPROJECT_ROOT=$root" "/DPUBLISH_DIR=$publish" "/DAPP_VERSION=$appVersion" "/DAPP_VERSION_MAJOR=$($numericVersion[0])" "/DAPP_VERSION_MINOR=$($numericVersion[1])" "/DAPP_VERSION_PATCH=$($numericVersion[2])" (Join-Path $root 'installer\coffee.nsi')
    if ($LASTEXITCODE -ne 0) { throw "NSIS failed ($LASTEXITCODE)" }
}
Get-Item $exe | Select-Object FullName,Length
