param([string]$Cache = (Join-Path $PSScriptRoot '../../.model-cache'))
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$Cache = [IO.Path]::GetFullPath($Cache)
New-Item -ItemType Directory -Force $Cache | Out-Null
$manifest = Get-Content (Join-Path $PSScriptRoot 'model-manifest.json') -Raw | ConvertFrom-Json
foreach ($model in $manifest.models) {
    $archive = Join-Path $Cache $model.archive
    if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant() -ne $model.sha256) {
        & curl.exe -fL --retry 3 --connect-timeout 30 --max-time 1800 -H 'Accept: application/octet-stream' ($model.apiUrl + '?download=1') -o $archive
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $($model.name)" }
    }
    if ((Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant() -ne $model.sha256) { throw "Checksum failed: $archive" }
    if ($model.archive.EndsWith('.bz2')) {
        & tar -xjf $archive -C $Cache
        if ($LASTEXITCODE -ne 0) { throw "Extraction failed: $archive" }
    }
}
$asr = Join-Path $Cache 'sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23-mobile'
$tts = Join-Path $Cache 'kokoro-int8-multi-lang-v1_1'
foreach ($entry in $manifest.files) {
    $target = Join-Path $project $entry.path
    $relative = $entry.path -replace '^app/src/main/assets/assistant/(asr|tts)/', ''
    if ($entry.path.Contains('/prompts/')) {
        if (!(Test-Path -LiteralPath $target)) { throw 'Cached prompts missing. Restore tracked files or run cache-prompts.py.' }
    } else {
        $source = Join-Path $(if ($entry.path.Contains('/asr/')) { $asr } else { $tts }) $relative
        New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
        Copy-Item -LiteralPath $source -Destination $target -Force
    }
    if ((Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant() -ne $entry.sha256) { throw "Asset checksum failed: $target" }
}
$libs = Join-Path $project 'app/libs'
New-Item -ItemType Directory -Force $libs | Out-Null
Copy-Item -LiteralPath (Join-Path $Cache 'sherpa.aar') -Destination (Join-Path $libs 'sherpa-onnx-1.12.26.aar') -Force
Write-Output 'Pinned model and runtime assets verified. Ready for Gradle build.'
