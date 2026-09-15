param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$probeRoot = Join-Path $projectRoot ('build/security/integrity-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $probeRoot 'gradle') -Force | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $probeRoot 'settings.gradle'), "rootProject.name = 'integrity-probe'", $utf8)
$probeBuild = @'
repositories { mavenCentral() }
configurations { probe }
dependencies { probe 'org.apache.commons:commons-lang3:3.18.0' }
tasks.register('resolveProbe') {
    doLast {
        if (configurations.probe.files.size() != 1) throw new GradleException('Unexpected artifact count')
        println('INTEGRITY_PROBE_RESOLVED')
    }
}
'@
[System.IO.File]::WriteAllText((Join-Path $probeRoot 'build.gradle'), $probeBuild, $utf8)

$metadataPath = Join-Path $probeRoot 'gradle/verification-metadata.xml'
Copy-Item -LiteralPath (Join-Path $projectRoot 'gradle/verification-metadata.xml') -Destination $metadataPath
$wrapper = Join-Path $projectRoot 'gradlew.bat'
function Invoke-GradleProbe([string]$LogPath) {
    $previousErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $wrapper -p $probeRoot resolveProbe --offline --no-daemon --no-configuration-cache --console=plain *> $LogPath
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
}

$validLog = Join-Path $probeRoot 'valid.log'
$validExit = Invoke-GradleProbe $validLog
if ($validExit -ne 0 -or (Get-Content $validLog -Raw) -notmatch 'INTEGRITY_PROBE_RESOLVED') {
    throw "Valid artifact did not resolve. See $validLog"
}

[xml]$metadata = Get-Content -LiteralPath $metadataPath -Raw
$checksum = $metadata.SelectSingleNode("//*[local-name()='component'][@group='org.apache.commons'][@name='commons-lang3'][@version='3.18.0']/*[local-name()='artifact'][@name='commons-lang3-3.18.0.jar']/*[local-name()='sha256']")
if ($null -eq $checksum) { throw 'Expected audited checksum is missing.' }
$checksum.SetAttribute('value', ('0' * 64))
$metadata.Save($metadataPath)
$invalidLog = Join-Path $probeRoot 'invalid.log'
$invalidExit = Invoke-GradleProbe $invalidLog
$invalidOutput = Get-Content $invalidLog -Raw
if ($invalidExit -eq 0 -or $invalidOutput -notmatch 'Dependency verification failed' -or $invalidOutput -match 'INTEGRITY_PROBE_RESOLVED') {
    throw "Invalid checksum was not rejected as expected. See $invalidLog"
}

[pscustomobject]@{
    ValidArtifactAccepted = $true
    InvalidChecksumRejected = $true
    Evidence = $probeRoot
} | ConvertTo-Json
