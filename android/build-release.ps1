param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(2, 2147483647)]
    [int]$VersionCode,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$')]
    [string]$VersionName,

    [switch]$P2Accepted
)

$ErrorActionPreference = 'Stop'
if (-not $P2Accepted) {
    throw 'Release is gated: rerun with -P2Accepted only after P1/P2 acceptance is recorded.'
}

$signingPropertiesPath = $env:COFFEE_SIGNING_PROPERTIES
if ([string]::IsNullOrWhiteSpace($signingPropertiesPath) -or -not (Test-Path -LiteralPath $signingPropertiesPath -PathType Leaf)) {
    throw 'Set COFFEE_SIGNING_PROPERTIES to the external signing.properties file before a release build.'
}

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$driveName = 'R'
if (Get-PSDrive -Name $driveName -ErrorAction SilentlyContinue) {
    throw 'R: is already in use. Wait for the other build to finish, then retry.'
}

$environmentNames = @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'GRADLE_USER_HOME', 'PATH')
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

& subst "${driveName}:" $workspaceRoot | Out-Null
$aliasCreated = $LASTEXITCODE -eq 0
if (-not $aliasCreated -or -not (Test-Path "${driveName}:\android\gradlew.bat")) {
    if ($aliasCreated) { & subst "${driveName}:" /D | Out-Null }
    throw 'Could not create the temporary R: workspace alias.'
}

try {
    $env:JAVA_HOME = "${driveName}:\.tools\android-jdk"
    $env:ANDROID_HOME = "${driveName}:\.tools\android-sdk"
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:GRADLE_USER_HOME = "${driveName}:\.tools\gradle-user-home"
    $env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

    & "${driveName}:\android\gradlew.bat" -p "${driveName}:\android" :app:assembleRelease --no-daemon `
        "-PcoffeeReleaseVersionCode=$VersionCode" "-PcoffeeReleaseVersionName=$VersionName"
    if ($LASTEXITCODE -ne 0) { throw "Gradle release build failed with exit code $LASTEXITCODE." }

    $apk = Get-Item "${driveName}:\android\app\build\outputs\apk\release\app-release.apk"
    $deliveryDirectory = "${driveName}:\outputs\android-release"
    New-Item -ItemType Directory -Path $deliveryDirectory -Force | Out-Null
    $deliveryApk = Join-Path $deliveryDirectory "coffee-$VersionName.apk"
    $displayDeliveryApk = Join-Path $workspaceRoot "outputs\android-release\coffee-$VersionName.apk"
    Copy-Item -LiteralPath $apk.FullName -Destination $deliveryApk -Force

    $apksigner = "${driveName}:\.tools\android-sdk\build-tools\36.0.0\apksigner.bat"
    $verifyOutput = & $apksigner verify --verbose --print-certs $deliveryApk 2>&1
    $verifyExitCode = $LASTEXITCODE
    $verifyOutput | ForEach-Object { Write-Output $_ }
    if ($verifyExitCode -ne 0) { throw 'apksigner verification failed.' }
    $certificateLine = $verifyOutput | Select-String 'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)' | Select-Object -First 1
    if (-not $certificateLine) { throw 'apksigner did not report a signer certificate SHA-256 digest.' }
    $actualCertificate = [regex]::Match($certificateLine.Line, 'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)').Groups[1].Value.ToLowerInvariant()
    $expectedCertificate = '2563e3bc4a436c79a83936e7511e90954ec94dada00af490c3f48178de581a49'
    if ($actualCertificate -ne $expectedCertificate) { throw 'The release APK certificate does not match the locally recorded Coffee release certificate.' }

    $aapt = "${driveName}:\.tools\android-sdk\build-tools\36.0.0\aapt.exe"
    $badging = & $aapt dump badging $deliveryApk 2>&1
    if ($LASTEXITCODE -ne 0 -or -not ($badging | Select-String "^package: name='com\.coffee\.pet\.android' versionCode='$VersionCode' versionName='$([regex]::Escape($VersionName))'")) {
        throw 'The APK package/version metadata did not match the requested release identity.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($deliveryApk)
    try {
        if ($archive.Entries.FullName | Where-Object { $_ -match '(?i)(signing\.properties|\.jks$|\.keystore$|\.p12$|\.pfx$|\.pem$)' }) {
            throw 'Signing material was found inside the APK archive.'
        }
    }
    finally { $archive.Dispose() }

    $hash = (Get-FileHash -LiteralPath $deliveryApk -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$deliveryApk.sha256" -Value "$hash  $(Split-Path -Leaf $deliveryApk)" -Encoding ascii
    $sourceHash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash
    Set-Content -LiteralPath "$($apk.FullName).sha256" -Value "$($sourceHash.ToLowerInvariant())  $($apk.Name)" -Encoding ascii
    $copyHash = (Get-FileHash -LiteralPath $deliveryApk -Algorithm SHA256).Hash
    if ($sourceHash -ne $copyHash) { throw 'The delivered APK copy hash differs from the Gradle output.' }

    [pscustomobject]@{ Apk = $displayDeliveryApk; Bytes = (Get-Item $deliveryApk).Length; Sha256 = $hash }
}
finally {
    if ($aliasCreated) { & subst "${driveName}:" /D | Out-Null }
    foreach ($name in $environmentNames) {
        $value = $previousEnvironment[$name]
        if ($null -eq $value) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item "Env:$name" $value }
    }
}
