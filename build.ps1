param([switch]$BuildInstaller, [switch]$DebugPreview)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$dotnet = Join-Path $root '.tools\dotnet\dotnet.exe'
$project = Join-Path $root 'src\Coffee.Desktop\Coffee.Desktop.csproj'
$configuration = if ($DebugPreview) { 'Debug' } else { 'Release' }
$publish = if ($DebugPreview) { Join-Path $root '.tools\qa-preview' } else { Join-Path $root 'release\CoffeeDesktop-win-x64' }
if ($BuildInstaller -and $DebugPreview) { throw 'BuildInstaller and DebugPreview cannot be used together.' }
if (-not (Test-Path $dotnet)) { throw "Local .NET SDK missing: $dotnet" }
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
    $makensis = Join-Path $root '.tools\nsis\nsis-3.13\Bin\makensis.exe'
    if (-not (Test-Path $makensis)) { throw "Portable NSIS missing: $makensis" }
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
    & $makensis "/DPROJECT_ROOT=$root" "/DPUBLISH_DIR=$publish" (Join-Path $root 'installer\coffee.nsi')
    if ($LASTEXITCODE -ne 0) { throw "NSIS failed ($LASTEXITCODE)" }
}
Get-Item $exe | Select-Object FullName,Length
