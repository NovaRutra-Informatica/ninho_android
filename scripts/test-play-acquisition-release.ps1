[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$SdkPath,
    [string]$JavaHome = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$configuration = Join-Path $project 'play-licensing.properties'
if (Test-Path -LiteralPath $configuration) { throw 'Este ensaio requer ausência de play-licensing.properties; não substitui uma configuração existente.' }
$fixture = Join-Path $project 'build/play-acquisition-release-fixture'
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
$initScript = Join-Path $fixture 'fixture-build-dir.gradle'
[IO.File]::WriteAllText($initScript, @'
gradle.beforeProject { project ->
    if (project.path == ':app') {
        project.layout.buildDirectory.set(new File(project.rootDir, 'build/play-acquisition-release-fixture/app'))
    }
}
'@)
$rsa = [Security.Cryptography.RSA]::Create(2048)
try { $publicKey = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()) }
finally { $rsa.Dispose() }
$contents = [Text.Encoding]::UTF8.GetBytes("play.license.publicKey=$publicKey`n")
$output = [IO.File]::Open($configuration, [IO.FileMode]::CreateNew)
try { $output.Write($contents, 0, $contents.Length) }
finally { $output.Dispose() }
try {
    Push-Location $project
    try {
        & .\gradlew.bat --init-script $initScript :app:assembleRelease :app:lintRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'O build release de ensaio falhou.' }
        $releaseApk = Join-Path $fixture 'app/outputs/apk/release/app-release-unsigned.apk'
        & (Join-Path $PSScriptRoot 'test-verify-apk-security.ps1') -SdkPath $SdkPath -JavaHome $JavaHome -ReleaseApk $releaseApk
        $result = @{
            kind = 'synthetic-public-key-release-build-not-for-distribution'
            apk = $releaseApk
            sha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash
            publicKeySha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($publicKey)))
            generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
        }
        $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $fixture 'result.json') -Encoding utf8
        Write-Output 'PASS: release/R8/lint e política do APK. Este artefato usa chave sintética sem chave privada persistida, não confirma compras reais e não deve ser distribuído.'
    } finally { Pop-Location }
} finally {
    if ([IO.File]::ReadAllText($configuration) -eq [Text.Encoding]::UTF8.GetString($contents)) {
        if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($configuration)) -ne $project) { throw 'Configuração fora da raiz esperada.' }
        Remove-Item -LiteralPath $configuration -Force
    } else { throw 'A configuração mudou durante o ensaio; foi preservada para revisão.' }
}
