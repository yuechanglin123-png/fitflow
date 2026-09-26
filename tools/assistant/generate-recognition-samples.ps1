param([string]$Output = '.model-cache/recognition-samples')
$ErrorActionPreference = 'Stop'
$Output = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force $Output | Out-Null
# Independent synthetic speech. No registry changes and no microphone capture.
$phrases = @(
    '小练小练', '小练小练，跳过休息', '小练小练，延长三十秒休息时间',
    '小练小练，暂停训练', '小练小练，完成本组', '小练小练，现在几点',
    '小练小练，北京今天天气怎么样', '小练小练，还剩几组',
    '跳过休息', '延长三十秒休息时间', '暂停训练', '完成本组',
    '帮我跳过休息吧', '麻烦暂停一下训练', '这一组做完了', '休息加三十秒',
    '再休息三十秒', '现在几点了', '今天星期几', '还要休息多久',
    '小练小练，不要完成本组', '小练小练，别暂停训练',
    '小练小练，完成本组然后暂停训练', '小练小练，延长十三秒',
    '我们一起喝杯水', '这首歌很好听', '我正在做深蹲', '今天工作怎么样'
)
$rows = @()
foreach ($name in @('Huihui','Kangkang')) {
    $voice = New-Object -ComObject SAPI.SpVoice
    $token = New-Object -ComObject SAPI.SpObjectToken
    $token.SetId("HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech_OneCore\Voices\Tokens\MSTTS_V110_zhCN_${name}M")
    $voice.Voice = $token
    $voice.Rate = 0
    for ($i=0; $i -lt $phrases.Count; $i++) {
        $file = '{0}-{1:D2}.wav' -f $name,$i
        $stream = New-Object -ComObject SAPI.SpFileStream
        $stream.Format.Type = 22 # 22.05 kHz mono PCM16
        $stream.Open((Join-Path $Output $file),3,$false)
        $voice.AudioOutputStream = $stream
        [void]$voice.Speak($phrases[$i])
        $stream.Close()
        $rows += @{file=$file; text=$phrases[$i]; voice=$name; negative=($i -ge 20)}
    }
}
$rows | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $Output 'samples.json')
Write-Output "Generated $($rows.Count) independent synthetic samples."
