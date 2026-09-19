param([string]$JavaHome)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$clojureArgs = @($args)

$toolsVersion = '1.12.5.1664'
$toolsSha256 = 'eb493fbef4cd8d5a77457f46e6c4538d89337c875da4fc9f086f183426a2a233'
$repoRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $repoRoot '.tools'
$toolsRootPrefix = [IO.Path]::GetFullPath($toolsRoot).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar

function Resolve-ToolsPath([string]$path) {
    $fullPath = [IO.Path]::GetFullPath($path)
    if (-not $fullPath.StartsWith($toolsRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside '$toolsRoot': $fullPath"
    }
    return $fullPath
}

$installRoot = Resolve-ToolsPath (Join-Path $toolsRoot "clojure-$toolsVersion")
$moduleManifest = Join-Path $installRoot 'ClojureTools\ClojureTools.psd1'

if ($JavaHome) {
    $java = Join-Path $JavaHome 'bin\java.exe'
} elseif ($env:JAVA_HOME) {
    $JavaHome = $env:JAVA_HOME
    $java = Join-Path $JavaHome 'bin\java.exe'
} else {
    $javaCommand = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($javaCommand) {
        $java = $javaCommand.Source
        $JavaHome = Split-Path -Parent (Split-Path -Parent $java)
    }
}

if (-not $java -or -not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw 'Java was not found. Pass -JavaHome <JDK 21+>, set JAVA_HOME, or put Java 21+ on PATH.'
}

$ErrorActionPreference = 'Continue'
$javaVersionText = (& $java -version 2>&1 | Out-String)
$javaVersionExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($javaVersionExitCode -ne 0 -or $javaVersionText -notmatch 'version "(?:1\.)?(\d+)') {
    throw "Could not determine the Java version from '$java'."
}
if ([int]$Matches[1] -lt 21) {
    throw "Java 21 or newer is required; '$java' reports Java $($Matches[1])."
}

if (-not (Test-Path -LiteralPath $moduleManifest -PathType Leaf)) {
    $downloadUrl = "https://github.com/clojure/brew-install/releases/download/$toolsVersion/clojure-tools.zip"
    $bootstrapRoot = Resolve-ToolsPath (Join-Path $toolsRoot ".bootstrap-$PID")
    $archive = Join-Path $bootstrapRoot 'clojure-tools.zip'
    try {
        New-Item -ItemType Directory -Force -Path $bootstrapRoot | Out-Null
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Write-Host "Downloading Clojure CLI $toolsVersion..."
        Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $archive
        $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualSha256 -ne $toolsSha256) {
            throw "Clojure CLI checksum mismatch: expected $toolsSha256, got $actualSha256."
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $bootstrapRoot
        $extractedModule = Resolve-ToolsPath (Join-Path $bootstrapRoot 'ClojureTools')
        if (-not (Test-Path -LiteralPath (Join-Path $extractedModule 'ClojureTools.psd1')) -or
            -not (Test-Path -LiteralPath (Join-Path $extractedModule "clojure-tools-$toolsVersion.jar"))) {
            throw 'The Clojure CLI archive does not contain the expected PowerShell module.'
        }
        if (Test-Path -LiteralPath $installRoot) {
            Remove-Item -LiteralPath $installRoot -Recurse -Force
        }
        New-Item -ItemType Directory -Path $installRoot | Out-Null
        Move-Item -LiteralPath $extractedModule -Destination $installRoot
    } finally {
        if (Test-Path -LiteralPath $bootstrapRoot) {
            Remove-Item -LiteralPath $bootstrapRoot -Recurse -Force
        }
    }
}

# Windows PowerShell 5.1 otherwise decodes UTF-8 classpath files as ANSI.
$moduleScript = Join-Path $installRoot 'ClojureTools\ClojureTools.psm1'
$moduleSource = [IO.File]::ReadAllText($moduleScript)
$utf8ModuleSource = $moduleSource.Replace('Get-Content $ManifestFile', 'Get-Content -Encoding UTF8 $ManifestFile')
$utf8ModuleSource = $utf8ModuleSource.Replace('Get-Content $CpFile', 'Get-Content -Encoding UTF8 $CpFile')
$utf8ModuleSource = $utf8ModuleSource.Replace('Get-Content $JvmFile', 'Get-Content -Encoding UTF8 $JvmFile')
$utf8ModuleSource = $utf8ModuleSource.Replace('Get-Content $MainFile', 'Get-Content -Encoding UTF8 $MainFile')
if ($utf8ModuleSource -ne $moduleSource) {
    [IO.File]::WriteAllText($moduleScript, $utf8ModuleSource, (New-Object Text.UTF8Encoding $false))
}

$env:JAVA_HOME = $JavaHome
$env:PATH = "$(Join-Path $JavaHome 'bin');$env:PATH"
$env:CLJ_CONFIG = Join-Path $toolsRoot 'clojure-config'
$env:CLJ_CACHE = Join-Path $toolsRoot 'clojure-cache'

Import-Module $moduleManifest -Force
Invoke-Clojure @clojureArgs
exit $LASTEXITCODE
