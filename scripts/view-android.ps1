param(
    [Parameter(Mandatory = $true)][string]$ProjectRoot,
    [Parameter(Mandatory = $true)][string]$Prefix,
    [string]$JavaHome,
    [switch]$GenerateOnly
)

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath $ProjectRoot).Path
foreach ($module in @('app', 'domain', 'data')) {
    if (-not (Test-Path -LiteralPath (Join-Path $project "$module/src/main/kotlin") -PathType Container)) {
        throw "Missing $module/src/main/kotlin. For other layouts, use a custom policy; see examples/android.policy.edn."
    }
}
if ($Prefix -notmatch '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$') {
    throw 'Prefix must be a Kotlin package prefix, such as com.example.app.'
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $repoRoot '.uml-viewer'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
# Stable per-project filenames retain edited policies and proposals across launches.
$hasher = [Security.Cryptography.SHA256]::Create()
try {
    $hash = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes("$project|$Prefix"))).Replace('-', '').Substring(0, 12)
} finally { $hasher.Dispose() }
$policyPath = Join-Path $outputDir "android-$hash.policy.edn"
$graphPath = Join-Path $outputDir "android-$hash.edn"

if (-not (Test-Path -LiteralPath $policyPath)) {
    $policy = @'
{:title "Android project"
 :src __ROOT__
 :prefix __PREFIX__
 :lang :kotlin
 :modules [{:id :app :src "app/src/main/kotlin"}
           {:id :domain :src "domain/src/main/kotlin"}
           {:id :data :src "data/src/main/kotlin"}]
 :out __OUT__
 :hierarchical true
 :order [app data domain]
 :levels [[domain] [data] [app]]}
'@
    $policy = $policy.Replace('__ROOT__', (ConvertTo-Json -InputObject $project -Compress))
    $policy = $policy.Replace('__PREFIX__', (ConvertTo-Json -InputObject $Prefix -Compress))
    $policy = $policy.Replace('__OUT__', (ConvertTo-Json -InputObject $graphPath -Compress))
    [IO.File]::WriteAllText($policyPath, $policy, (New-Object Text.UTF8Encoding $false))
}

$launcher = Join-Path $PSScriptRoot 'clj.ps1'
Push-Location $repoRoot
try {
    & $launcher -JavaHome $JavaHome -M:kotlin:ir $policyPath $graphPath
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Local graph: $graphPath"
    if (-not $GenerateOnly) {
        & $launcher -JavaHome $JavaHome -M:kotlin:run --standalone $graphPath
        exit $LASTEXITCODE
    }
} finally { Pop-Location }
