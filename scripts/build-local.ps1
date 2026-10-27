param([string]$SdkPath = 'D:\Android\sdk', [string]$VersionName = '', [int]$VersionCode = 0)
$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom
$env:PYTHONIOENCODING = 'utf-8'
$env:PYTHONUTF8 = '1'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$signingFile = Join-Path $root '.private\signing.json'
if (-not (Test-Path -LiteralPath $signingFile)) { & (Join-Path $PSScriptRoot 'create-signing.ps1') }
$signing = Get-Content -LiteralPath $signingFile -Raw -Encoding UTF8 | ConvertFrom-Json
$versions = @{}
Get-Content -LiteralPath (Join-Path $root 'version.properties') | ForEach-Object { if ($_ -match '^([^=]+)=(.+)$') { $versions[$matches[1]]=$matches[2] } }
if (-not $VersionName) { $VersionName=$versions.versionName }
if (-not $VersionCode) { $VersionCode=[int]$versions.versionCode }
if ($VersionName -notmatch '^\d+\.\d+\.\d+$' -or $VersionCode -lt 1) { throw 'Invalid app version' }
$buildTools = Join-Path $SdkPath 'build-tools\35.0.0'
$androidJar = Join-Path $SdkPath 'platforms\android-34\android.jar'
foreach ($required in @($androidJar, (Join-Path $buildTools 'aapt2.exe'), (Join-Path $buildTools 'd8.bat'), (Join-Path $buildTools 'apksigner.bat'))) { if (-not (Test-Path -LiteralPath $required)) { throw "Missing Android SDK tool: $required" } }
$build = Join-Path ([System.IO.Path]::GetTempPath()) ('shijian-build-' + [Guid]::NewGuid().ToString('N'))
$generated = Join-Path $build 'generated'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$dist = Join-Path $root 'dist'
foreach ($directory in @($build,$generated,$classes,$dex,$dist)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
$stagedRes = Join-Path $build 'res'
$stagedAssets = Join-Path $build 'assets'
Copy-Item -LiteralPath (Join-Path $root 'app\src\main\res') -Destination $stagedRes -Recurse
Copy-Item -LiteralPath (Join-Path $root 'app\src\main\assets') -Destination $stagedAssets -Recurse
Write-Output 'Linking Android resources and bundled interface...'
[xml]$manifest = Get-Content -LiteralPath (Join-Path $root 'app\src\main\AndroidManifest.xml') -Raw -Encoding UTF8
$manifest.manifest.SetAttribute('package','com.mrkuangker.shijian')
$manifestPath = Join-Path $build 'AndroidManifest.xml'
$writerSettings = New-Object System.Xml.XmlWriterSettings
$writerSettings.Encoding = $utf8NoBom
$writer = [System.Xml.XmlWriter]::Create($manifestPath,$writerSettings)
$manifest.Save($writer)
$writer.Close()
& (Join-Path $buildTools 'aapt2.exe') compile --dir $stagedRes -o (Join-Path $build 'resources.zip')
if ($LASTEXITCODE -ne 0) { throw 'Resource compilation failed' }
& (Join-Path $buildTools 'aapt2.exe') link -o (Join-Path $build 'unsigned.apk') --manifest $manifestPath -I $androidJar --java $generated -A $stagedAssets --min-sdk-version 26 --target-sdk-version 34 --version-code $VersionCode --version-name $VersionName (Join-Path $build 'resources.zip')
if ($LASTEXITCODE -ne 0) { throw 'Resource linking failed' }
$configDir = Join-Path $generated 'com\mrkuangker\shijian'
New-Item -ItemType Directory -Path $configDir -Force | Out-Null
$buildConfig = "package com.mrkuangker.shijian; public final class BuildConfig { public static final boolean DEBUG=false; public static final int VERSION_CODE=$VersionCode; public static final String VERSION_NAME=`"$VersionName`"; public static final String GITHUB_REPO=`"MrKuangker/shijian-calendar`"; }"
[System.IO.File]::WriteAllText((Join-Path $configDir 'BuildConfig.java'),$buildConfig,$utf8NoBom)
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'app\src\main\java') -Recurse -Filter '*.java') + @(Get-ChildItem -LiteralPath $generated -Recurse -Filter '*.java')
$sourceList = Join-Path $build 'sources.txt'
$sourceArguments = ($sources | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }) -join [Environment]::NewLine
[System.IO.File]::WriteAllText($sourceList,$sourceArguments,$utf8NoBom)
Write-Output 'Compiling native Android code...'
& javac '-J-Duser.language=en' '-J-Dfile.encoding=UTF-8' -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$androidJar;$(Join-Path $buildTools 'core-lambda-stubs.jar')" -d $classes "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& jar cf (Join-Path $build 'classes.jar') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed' }
& (Join-Path $buildTools 'd8.bat') --release --min-api 26 --lib $androidJar --output $dex (Join-Path $build 'classes.jar')
if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed' }
& python (Join-Path $PSScriptRoot 'add-dex.py') (Join-Path $build 'unsigned.apk') $dex
if ($LASTEXITCODE -ne 0) { throw 'APK assembly failed' }
& (Join-Path $buildTools 'zipalign.exe') -f -p 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
$env:SHIJIAN_STORE_PASSWORD=$signing.storePassword
$env:SHIJIAN_KEY_PASSWORD=$signing.keyPassword
try {
    $outputApk = Join-Path $dist 'shijian.apk'
    & (Join-Path $buildTools 'apksigner.bat') sign --ks (Join-Path $root '.private\shijian-release.p12') --ks-key-alias $signing.keyAlias --ks-pass env:SHIJIAN_STORE_PASSWORD --key-pass env:SHIJIAN_KEY_PASSWORD --out $outputApk (Join-Path $build 'aligned.apk')
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
    & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $outputApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    & python (Join-Path $PSScriptRoot 'release-manifest.py') --apk $outputApk --version-name $VersionName --version-code $VersionCode --output (Join-Path $dist 'update.json')
    if ($LASTEXITCODE -ne 0) { throw 'Update manifest generation failed' }
    Write-Output "Built signed APK: $outputApk"
} finally { Remove-Item Env:SHIJIAN_STORE_PASSWORD,Env:SHIJIAN_KEY_PASSWORD -ErrorAction SilentlyContinue }
