[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$SdkPath,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ReleaseApk,
    [string]$DebugApk
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ReleaseApk) { $ReleaseApk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release-unsigned.apk' }
if (-not $DebugApk) { $DebugApk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk' }
$verifier = Join-Path $PSScriptRoot 'verify-apk-security.ps1'
$release = & $verifier -SdkPath $SdkPath -JavaHome $JavaHome -ApkPath $ReleaseApk -PassThru
if (-not $release.Passed -or $release.SourceType -ne 'Apk' -or -not $release.Sha256) {
    throw 'The production APK was not verified.'
}
Write-Output 'PASS: compiled release APK.'

$checks = 1
function Assert-Rejected([scriptblock]$Action, [string]$ExpectedError, [string]$Name) {
    $caught = $null
    try { & $Action | Out-Null } catch { $caught = $_.Exception.Message }
    if (-not $caught -or $caught -notmatch $ExpectedError) {
        throw "FAIL: $Name did not fail for the expected reason ($ExpectedError). Actual: $caught"
    }
    Write-Output "PASS: rejected $Name."
}

Assert-Rejected { & $verifier -SdkPath $SdkPath -JavaHome $JavaHome -ApkPath $DebugApk } 'debuggable' 'compiled debug APK'
$checks++

$testRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\build\apk-security-tests'))
$fixtureDirectory = Join-Path $testRoot ([guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureDirectory -Force | Out-Null
$androidNamespace = 'http://schemas.android.com/apk/res/android'
try {
    $mutations = @(
        @{ Name = 'backup enabled'; Error = 'allowBackup'; Change = { param($xml) $xml.manifest.application.SetAttribute('allowBackup', $androidNamespace, 'true') } },
        @{ Name = 'implicit default backup'; Error = 'allowBackup'; Change = { param($xml) $xml.manifest.application.RemoveAttribute('allowBackup', $androidNamespace) } },
        @{ Name = 'full backup enabled'; Error = 'fullBackupContent'; Change = { param($xml) $xml.manifest.application.SetAttribute('fullBackupContent', $androidNamespace, 'true') } },
        @{ Name = 'cleartext enabled'; Error = 'usesCleartextTraffic'; Change = { param($xml) $xml.manifest.application.SetAttribute('usesCleartextTraffic', $androidNamespace, 'true') } },
        @{ Name = 'network policy override'; Error = 'networkSecurityConfig'; Change = { param($xml) $xml.manifest.application.SetAttribute('networkSecurityConfig', $androidNamespace, '@xml/unsafe_network') } },
        @{ Name = 'test-only application'; Error = 'testOnly'; Change = { param($xml) $xml.manifest.application.SetAttribute('testOnly', $androidNamespace, 'true') } },
        @{ Name = 'new Internet permission'; Error = 'unexpected requested permission'; Change = { param($xml)
            $node = $xml.CreateElement('uses-permission')
            $node.SetAttribute('name', $androidNamespace, 'android.permission.INTERNET')
            $xml.manifest.AppendChild($node)
        } },
        @{ Name = 'weakened internal permission'; Error = 'protection level'; Change = { param($xml) $xml.manifest.permission.SetAttribute('protectionLevel', $androidNamespace, 'normal') } },
        @{ Name = 'exported provider'; Error = 'non-exported'; Change = { param($xml) $xml.manifest.application.provider.SetAttribute('exported', $androidNamespace, 'true') } },
        @{ Name = 'receiver without DUMP protection'; Error = 'non-exported'; Change = { param($xml) $xml.manifest.application.receiver.RemoveAttribute('permission', $androidNamespace) } },
        @{ Name = 'unexpected exported service'; Error = 'non-exported'; Change = { param($xml)
            $node = $xml.CreateElement('service')
            $node.SetAttribute('name', $androidNamespace, '.UnexpectedService')
            $node.SetAttribute('exported', $androidNamespace, 'true')
            $xml.manifest.application.AppendChild($node)
        } },
        @{ Name = 'implicitly exported receiver'; Error = 'non-exported'; Change = { param($xml)
            $node = $xml.CreateElement('receiver')
            $node.SetAttribute('name', $androidNamespace, '.UnexpectedReceiver')
            $node.AppendChild($xml.manifest.application.receiver.'intent-filter'[0].CloneNode($true))
            $xml.manifest.application.AppendChild($node)
        } },
        @{ Name = 'launcher deep link'; Error = 'launcher intent'; Change = { param($xml)
            $node = $xml.CreateElement('data')
            $node.SetAttribute('scheme', $androidNamespace, 'https')
            $xml.manifest.application.activity.'intent-filter'.AppendChild($node)
        } },
        @{ Name = 'production instrumentation'; Error = 'instrumentation'; Change = { param($xml) $xml.manifest.AppendChild($xml.CreateElement('instrumentation')) } }
    )
    foreach ($mutation in $mutations) {
        $xml = [System.Xml.XmlDocument]::new()
        $xml.XmlResolver = $null
        $xml.LoadXml($release.ManifestXml)
        & $mutation.Change $xml | Out-Null
        $fixturePath = Join-Path $fixtureDirectory 'manifest.xml'
        $xml.Save($fixturePath)
        Assert-Rejected { & $verifier -ManifestPath $fixturePath } $mutation.Error $mutation.Name
        $checks++
    }

    $fixturePath = Join-Path $fixtureDirectory 'manifest.xml'
    [System.IO.File]::WriteAllText($fixturePath, $release.ManifestXml)
    $backupPath = Join-Path $fixtureDirectory 'backup.xml'
    $backupMutations = @(
        @{ Name = 'device transfer of databases'; Error = 'device-transfer does not exclude database'; Change = { param($xml)
            $node = $xml.SelectSingleNode("/data-extraction-rules/device-transfer/exclude[@domain='database']")
            $node.ParentNode.RemoveChild($node)
        } },
        @{ Name = 'cloud backup of preferences'; Error = 'cloud-backup does not exclude sharedpref'; Change = { param($xml)
            $node = $xml.SelectSingleNode("/data-extraction-rules/cloud-backup/exclude[@domain='sharedpref']")
            $node.SetAttribute('path', 'only-one-preference.xml')
        } }
    )
    foreach ($mutation in $backupMutations) {
        $xml = [System.Xml.XmlDocument]::new()
        $xml.XmlResolver = $null
        $xml.LoadXml($release.BackupRulesXml[0])
        & $mutation.Change $xml | Out-Null
        $xml.Save($backupPath)
        Assert-Rejected { & $verifier -ManifestPath $fixturePath -BackupRulesPath $backupPath } $mutation.Error $mutation.Name
        $checks++
    }

    $invalidPath = Join-Path $fixtureDirectory 'invalid.apk'
    [System.IO.File]::WriteAllText($invalidPath, 'not an APK')
    Assert-Rejected { & $verifier -SdkPath $SdkPath -JavaHome $JavaHome -ApkPath $invalidPath } 'apkanalyzer failed' 'invalid APK'
    $checks++
} finally {
    $resolvedFixtureDirectory = [System.IO.Path]::GetFullPath($fixtureDirectory)
    if (-not $resolvedFixtureDirectory.StartsWith($testRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Fixture cleanup target is outside the test directory.'
    }
    Remove-Item -LiteralPath $resolvedFixtureDirectory -Recurse -Force
}
Write-Output "PASS: $checks APK security checks (2 compiled APKs, mutated XML fixtures, invalid APK)."
