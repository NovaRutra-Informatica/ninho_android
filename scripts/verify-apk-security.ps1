[CmdletBinding(DefaultParameterSetName = 'Apk')]
param(
    [Parameter(ParameterSetName = 'Apk')]
    [string]$ApkPath,
    [Parameter(Mandatory, ParameterSetName = 'Manifest')]
    [string]$ManifestPath,
    [Parameter(ParameterSetName = 'Manifest')]
    [string]$BackupRulesPath,
    [Parameter(ParameterSetName = 'Manifest')]
    [string]$LegacyBackupRulesPath,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ExpectedPackage = 'app.ninho.android',
    [switch]$PassThru
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-SafeXml([string]$Text) {
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $settings.MaxCharactersInDocument = 16MB
    $reader = [System.Xml.XmlReader]::Create([System.IO.StringReader]::new($Text), $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
        return ,$document
    } finally {
        $reader.Dispose()
    }
}

function Get-AndroidAttribute([System.Xml.XmlElement]$Element, [string]$Name) {
    return $Element.GetAttribute($Name, 'http://schemas.android.com/apk/res/android')
}

function Assert-FalseAttribute([System.Xml.XmlElement]$Element, [string]$Name, [bool]$Required) {
    $value = Get-AndroidAttribute $Element $Name
    if (($Required -or $value -ne '') -and $value -ne 'false') {
        throw "Manifest policy: android:$Name must be false."
    }
}

function Assert-ManifestSecurity([System.Xml.XmlDocument]$Document, [string]$Package) {
    $manifest = $Document.DocumentElement
    if ($manifest.LocalName -ne 'manifest' -or $manifest.GetAttribute('package') -ne $Package) {
        throw "Manifest policy: unexpected application package."
    }
    $applications = @($manifest.SelectNodes('application'))
    if ($applications.Count -ne 1) { throw 'Manifest policy: exactly one application is required.' }
    $application = $applications[0]
    Assert-FalseAttribute $application 'debuggable' $false
    Assert-FalseAttribute $application 'testOnly' $false
    if ((Get-AndroidAttribute $application 'allowBackup') -ne 'true') { throw 'Manifest policy: allowBackup must explicitly enable the compact-only backup policy.' }
    if ((Get-AndroidAttribute $application 'fullBackupContent') -notmatch '^@') { throw 'Manifest policy: fullBackupContent must reference the compact-only rules.' }
    Assert-FalseAttribute $application 'usesCleartextTraffic' $true
    if ((Get-AndroidAttribute $application 'networkSecurityConfig') -ne '') {
        throw 'Manifest policy: networkSecurityConfig needs a separate policy review.'
    }
    if ($manifest.SelectNodes('instrumentation').Count -ne 0) {
        throw 'Manifest policy: instrumentation is not allowed in the production APK.'
    }

    $internalPermission = "$Package.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    $permissionDefinitions = @($manifest.SelectNodes('permission'))
    foreach ($permission in $permissionDefinitions) {
        if ((Get-AndroidAttribute $permission 'name') -ne $internalPermission -or
            (Get-AndroidAttribute $permission 'protectionLevel') -notin @('signature', '0x2', '2')) {
            throw 'Manifest policy: unexpected permission definition or protection level.'
        }
    }
    foreach ($permission in $manifest.SelectNodes('uses-permission | uses-permission-sdk-23 | uses-permission-sdk-m')) {
        $name = Get-AndroidAttribute $permission 'name'
        $allowedNoticePermissions = @('android.permission.POST_NOTIFICATIONS', 'android.permission.RECEIVE_BOOT_COMPLETED')
        if (($name -ne $internalPermission -and $name -notin $allowedNoticePermissions) -or $permissionDefinitions.Count -ne 1) {
            throw "Manifest policy: unexpected requested permission: $name."
        }
    }

    $namespace = [System.Xml.XmlNamespaceManager]::new($Document.NameTable)
    $namespace.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
    $launcherCount = 0
    $exportedNames = [System.Collections.Generic.List[string]]::new()
    foreach ($component in $application.SelectNodes('activity | activity-alias | service | receiver | provider')) {
        $name = Get-AndroidAttribute $component 'name'
        if ($name.StartsWith('.')) { $name = "$Package$name" }
        elseif (-not $name.Contains('.')) { $name = "$Package.$name" }
        $exported = Get-AndroidAttribute $component 'exported'
        if ($component.LocalName -eq 'activity' -and $name -eq "$Package.MainActivity") {
            $filters = @($component.SelectNodes('intent-filter'))
            $launcherFilter = $component.SelectNodes("intent-filter[action/@android:name='android.intent.action.MAIN' and category/@android:name='android.intent.category.LAUNCHER']", $namespace)
            if ($exported -ne 'true' -or $filters.Count -ne 1 -or $launcherFilter.Count -ne 1 -or
                $filters[0].SelectNodes('action | category').Count -ne 2 -or $filters[0].SelectNodes('data').Count -ne 0) {
                throw 'Manifest policy: the main activity must expose only its launcher intent.'
            }
            $launcherCount++
        } elseif ($component.LocalName -eq 'receiver' -and $name -eq 'androidx.profileinstaller.ProfileInstallReceiver' -and
            $exported -eq 'true' -and (Get-AndroidAttribute $component 'permission') -eq 'android.permission.DUMP') {
            # AndroidX exposes this tools-only receiver behind the platform DUMP permission.
        } elseif ($exported -ne 'false') {
            throw "Manifest policy: component must explicitly be non-exported: $name."
        }
        if ($name -eq "$Package.StudyBackupJob" -and (Get-AndroidAttribute $component 'permission') -ne 'android.permission.BIND_JOB_SERVICE') {
            throw 'Manifest policy: StudyBackupJob must require BIND_JOB_SERVICE.'
        }
        if ($exported -eq 'true') { $exportedNames.Add($name) }
    }
    if ($launcherCount -ne 1) { throw 'Manifest policy: exactly one main launcher activity is required.' }
    return $exportedNames.ToArray()
}

function Assert-CompactInclude([System.Xml.XmlElement]$Node, [bool]$Legacy) {
    $children = @($Node.SelectNodes('*'))
    if ($children.Count -ne 1 -or $children[0].LocalName -ne 'include' -or
        $children[0].GetAttribute('domain') -ne 'file' -or $children[0].GetAttribute('path') -ne 'compact-backup/latest.ninho-backup.gz') {
        throw 'Backup policy: only the compact snapshot may be included.'
    }
    if ($Legacy -and $children[0].GetAttribute('requireFlags') -ne 'clientSideEncryption') {
        throw 'Backup policy: legacy cloud backup requires clientSideEncryption.'
    }
}
function Assert-BackupRules([System.Xml.XmlDocument]$Document) {
    if ($Document.DocumentElement.LocalName -ne 'data-extraction-rules' -or $Document.DocumentElement.SelectNodes('*').Count -ne 2) {
        throw 'Backup policy: expected only cloud-backup and device-transfer rules.'
    }
    foreach ($mode in @('cloud-backup', 'device-transfer')) {
        $rules = @($Document.DocumentElement.SelectNodes($mode))
        if ($rules.Count -ne 1) { throw "Backup policy: exactly one $mode is required." }
        Assert-CompactInclude $rules[0] $false
        if ($mode -eq 'cloud-backup' -and $rules[0].GetAttribute('disableIfNoEncryptionCapabilities') -ne 'true') {
            throw 'Backup policy: cloud backup must require encryption capabilities.'
        }
    }
}
function Assert-LegacyBackupRules([System.Xml.XmlDocument]$Document) {
    if ($Document.DocumentElement.LocalName -ne 'full-backup-content') { throw 'Backup policy: expected full-backup-content.' }
    Assert-CompactInclude $Document.DocumentElement $true
}

function Invoke-ApkAnalyzer([string[]]$Arguments) {
    # The SDK batch launcher misreads Java versions without a minor component, such as "25".
    $lines = & $java "-Dcom.android.sdklib.toolsdir=$toolsDirectory" -classpath $analyzer com.android.tools.apk.analyzer.ApkAnalyzerCli @Arguments
    if ($LASTEXITCODE -ne 0) { throw "apkanalyzer failed with exit code $LASTEXITCODE." }
    return $lines -join [Environment]::NewLine
}

$artifactHash = $null
if ($PSCmdlet.ParameterSetName -eq 'Apk') {
    if (-not $ApkPath) { $ApkPath = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release-unsigned.apk' }
    $sourcePath = (Resolve-Path -LiteralPath $ApkPath).Path
    if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
    if (-not $SdkPath) { throw 'Set -SdkPath to the Android SDK directory.' }
    $toolsDirectory = Join-Path $SdkPath 'cmdline-tools\latest'
    $analyzer = Join-Path $toolsDirectory 'lib\apkanalyzer-classpath.jar'
    if (-not (Test-Path -LiteralPath $analyzer -PathType Leaf)) {
        throw 'Install Android SDK command-line tools (latest) before verifying the APK.'
    }
    $java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'Set -JavaHome to a compatible JDK directory.' }
    $artifactHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
    $manifestText = Invoke-ApkAnalyzer @('manifest', 'print', $sourcePath)
} else {
    $sourcePath = (Resolve-Path -LiteralPath $ManifestPath).Path
    $manifestText = [System.IO.File]::ReadAllText($sourcePath)
}

$document = Read-SafeXml $manifestText
$exportedComponents = @(Assert-ManifestSecurity $document $ExpectedPackage)
$rulesReference = Get-AndroidAttribute $document.DocumentElement.SelectSingleNode('application') 'dataExtractionRules'
if (-not $rulesReference) { throw 'Backup policy: dataExtractionRules is required.' }
$legacyReference = Get-AndroidAttribute $document.DocumentElement.SelectSingleNode('application') 'fullBackupContent'
$backupRules = [System.Collections.Generic.List[string]]::new()
$legacyBackupRules = [System.Collections.Generic.List[string]]::new()
if ($PSCmdlet.ParameterSetName -eq 'Apk') {
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkPath 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Install a stable Android SDK Build Tools version with aapt2.' }
    $aapt = Join-Path $buildTools.FullName 'aapt2.exe'
    $table = (& $aapt dump resources $sourcePath) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0) { throw "aapt2 failed with exit code $LASTEXITCODE." }
    foreach ($reference in @(@{ Value = $rulesReference; Legacy = $false }, @{ Value = $legacyReference; Legacy = $true })) {
        if ($reference.Value -notmatch '^@(?:ref/)?(0x[0-9a-fA-F]{8})$') { throw 'Backup policy: rules must reference a compiled resource.' }
        $resourceId = $Matches[1]
    $resourcePattern = '(?ms)^\s+resource\s+' + [regex]::Escape($resourceId) + '\s+xml/[^\r\n]+\r?\n(?<entries>.*?)(?=^\s+resource\s+|\z)'
    $resource = [regex]::Match($table, $resourcePattern)
    if (-not $resource.Success) { throw 'Backup policy: the referenced XML resource is missing from the APK.' }
    # aapt2 resource entries can end in CRLF anywhere in the table.
    $entries = [regex]::Matches($resource.Groups['entries'].Value, '(?m)^[ \t]+\([^\r\n]*?\) (?<value>[^\r\n]+)\r?$')
    if ($entries.Count -eq 0) { throw 'Backup policy: the resource has no configurations.' }
    foreach ($entry in $entries) {
        if ($entry.Groups['value'].Value -notmatch '^\(file\) (res/\S+\.xml) type=XML$') {
            throw 'Backup policy: an unsupported resource configuration needs review.'
        }
        $backupText = Invoke-ApkAnalyzer @('resources', 'xml', '--file', $Matches[1], $sourcePath)
        if ($reference.Legacy) { Assert-LegacyBackupRules (Read-SafeXml $backupText); $legacyBackupRules.Add($backupText) }
        else { Assert-BackupRules (Read-SafeXml $backupText); $backupRules.Add($backupText) }
    }
    }
    if ($artifactHash -ne (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash) {
        throw 'The APK changed during verification; retry after the build finishes.'
    }
} else {
    if (-not $BackupRulesPath -or -not $LegacyBackupRulesPath) { throw 'Set -BackupRulesPath and -LegacyBackupRulesPath when verifying a decoded manifest.' }
    $backupText = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $BackupRulesPath).Path)
    Assert-BackupRules (Read-SafeXml $backupText)
    $backupRules.Add($backupText)
    $legacyText = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $LegacyBackupRulesPath).Path)
    Assert-LegacyBackupRules (Read-SafeXml $legacyText)
    $legacyBackupRules.Add($legacyText)
}
$result = [pscustomobject]@{
    SourceType = $PSCmdlet.ParameterSetName
    Source = $sourcePath
    Sha256 = $artifactHash
    Package = $ExpectedPackage
    ExportedComponents = $exportedComponents
    ManifestXml = $document.OuterXml
    BackupRulesXml = $backupRules.ToArray()
    LegacyBackupRulesXml = $legacyBackupRules.ToArray()
    Passed = $true
}
if ($PassThru) { $result }
else { Write-Output "PASS: $($result.SourceType) security policy: $sourcePath" }
