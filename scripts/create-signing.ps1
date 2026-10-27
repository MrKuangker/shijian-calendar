$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom
$root = Split-Path -Parent $PSScriptRoot
$privateDir = Join-Path $root '.private'
$configPath = Join-Path $privateDir 'signing.json'
$keyPath = Join-Path $privateDir 'shijian-release.p12'
if (Test-Path -LiteralPath $configPath) {
    if (-not (Test-Path -LiteralPath $keyPath)) { throw 'Signing key missing. Restore the original key; do not generate a replacement.' }
    Write-Output 'Existing release signing key retained.'
    exit 0
}
if (Test-Path -LiteralPath $keyPath) { throw 'Key already exists without configuration. Restore signing.json first.' }
New-Item -ItemType Directory -Path $privateDir -Force | Out-Null
$randomBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($randomBytes)
$rng.Dispose()
$password = [Convert]::ToBase64String($randomBytes)
$env:SHIJIAN_NEW_SIGN_PASSWORD = $password
try {
    & keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias shijian -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Shijian, OU=Android, O=Shijian, C=CN' -storepass:env SHIJIAN_NEW_SIGN_PASSWORD -keypass:env SHIJIAN_NEW_SIGN_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create release signing key.' }
    $config = @{storePassword=$password; keyPassword=$password; keyAlias='shijian'} | ConvertTo-Json
    [System.IO.File]::WriteAllText($configPath, $config, $utf8NoBom)
    Write-Output 'Release signing key created in .private. Back up this directory securely.'
} finally { Remove-Item Env:SHIJIAN_NEW_SIGN_PASSWORD -ErrorAction SilentlyContinue }
