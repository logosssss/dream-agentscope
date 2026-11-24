# 端到端问答评测：串行打本地 /api/chat，落原始 JSON + meta.json，再重建 summary.md。
# 用法（仓库根目录）：
#   $env:EVAL_TOKEN = "<登录 Bearer>"
#   $env:EVAL_CALLER_TOKEN = "<与进程一致的 Caller Token>"   # 本地直连必填
#   $env:EVAL_BASE_URL = "http://127.0.0.1:8093"            # 可选，默认本机用户进程
#   .\scripts\eval-ask.ps1
#   .\scripts\eval-ask.ps1 -ProbeOnly   # 只做步骤 0：问一句，看答案是否含文档链接且中文正常
#
# 红线：只打本地；不打印 token；不并发；不改判定列。
param(
    [switch]$ProbeOnly
)

$ErrorActionPreference = "Stop"
# 控制台按 UTF-8 打印中文答案。请求和响应都按 UTF-8 字节处理，不依赖控制台代码页。
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

$BaseUrl = if ($env:EVAL_BASE_URL -and $env:EVAL_BASE_URL.Trim()) {
    $env:EVAL_BASE_URL.Trim().TrimEnd("/")
} else {
    "http://127.0.0.1:8093"
}
$Token = if ($env:EVAL_TOKEN) { $env:EVAL_TOKEN.Trim() } else { "" }
$Caller = if ($env:EVAL_CALLER_TOKEN) { $env:EVAL_CALLER_TOKEN.Trim() } else { "" }

if ($BaseUrl -match "dream-saas\.com") {
    throw "拒绝：评测只允许本地 BaseUrl，当前是 $BaseUrl"
}
if (-not $Token) {
    throw "缺少 EVAL_TOKEN（登录后的 Bearer，不要写进脚本）"
}
if (-not $Caller) {
    throw "缺少 EVAL_CALLER_TOKEN（本地直连用户进程的 X-Caller-Token）"
}

$CasesPath = Join-Path $RepoRoot "eval\ask-cases.csv"
if (-not (Test-Path $CasesPath)) {
    throw "缺少用例表: $CasesPath"
}

function New-EvalHeaders {
    # Content-Type 在发请求时单独带 charset，避免中文问句被系统默认编码打成问号。
    return @{
        "Authorization"  = "Bearer $Token"
        "X-Caller-Token" = $Caller
        "X-Request-Id"   = [guid]::NewGuid().ToString()
    }
}

function Read-Utf8Body($response) {
    # Content-Type 不带 charset 时，Invoke-RestMethod 在 Windows PowerShell 5.1 会按 ISO-8859-1 解 JSON。
    # 这里只信原始字节，再按 UTF-8 解码。流可能已被读过，先拨回开头。
    $stream = $response.RawContentStream
    if ($null -eq $stream) {
        return [string]$response.Content
    }
    if ($stream.CanSeek) {
        $stream.Position = 0
    }
    $buffer = New-Object System.IO.MemoryStream
    $stream.CopyTo($buffer)
    return [System.Text.Encoding]::UTF8.GetString($buffer.ToArray())
}

function Invoke-Chat([string]$text, [string]$sessionId) {
    $bodyObj = [ordered]@{ sessionId = $sessionId; text = $text }
    $json = ($bodyObj | ConvertTo-Json -Compress)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $headers = New-EvalHeaders
    $headers["Content-Type"] = "application/json; charset=utf-8"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $raw = Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/chat" -Headers $headers -Body $bytes -UseBasicParsing
    $sw.Stop()
    $decoded = Read-Utf8Body $raw
    return @{ Data = ($decoded | ConvertFrom-Json); ElapsedMs = [int]$sw.ElapsedMilliseconds }
}

function Get-RunDetail([string]$runId) {
    if (-not $runId) {
        return $null
    }
    try {
        $raw = Invoke-WebRequest -Method Get -Uri "$BaseUrl/api/runs/$([uri]::EscapeDataString($runId))" -Headers (New-EvalHeaders) -UseBasicParsing
        $decoded = Read-Utf8Body $raw
        return ($decoded | ConvertFrom-Json)
    } catch {
        return $null
    }
}

function Test-AnswerHasDocLink([string]$answer) {
    return [bool]($answer -and $answer -match "/docs/doc/")
}

function Get-CitedDocs([string]$answer) {
    $found = New-Object System.Collections.Generic.List[string]
    if (-not $answer) {
        return @()
    }
    $matches = [regex]::Matches($answer, "/docs/doc/([^#\s\)\]\""<>]+)")
    foreach ($m in $matches) {
        $id = [uri]::UnescapeDataString($m.Groups[1].Value)
        if (-not $found.Contains($id)) {
            $found.Add($id)
        }
    }
    return $found.ToArray()
}

function Truncate-Text([string]$text, [int]$max = 200) {
    if (-not $text) {
        return ""
    }
    $flat = ($text -replace "\s+", " ").Trim()
    if ($flat.Length -le $max) {
        return $flat
    }
    return $flat.Substring(0, $max) + "…"
}

function Write-Utf8Json($path, $payload) {
    $json = $payload | ConvertTo-Json -Depth 8
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $json, $utf8)
}

function Write-RunMeta([string]$runDir, [string]$startedAt) {
    # 记下跑的是哪次提交、工作区脏了哪些文件、提示词文件的哈希。不写令牌。
    $porcelain = @(git status --porcelain)
    $promptPath = Join-Path $RepoRoot "agent-semantic\src\main\resources\prompts\prompt.companion.system.md"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $promptBytes = [System.IO.File]::ReadAllBytes($promptPath)
        $promptHash = ([System.BitConverter]::ToString($sha.ComputeHash($promptBytes))).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
    $commit = (git rev-parse HEAD).Trim()
    $meta = [ordered]@{
        commit     = $commit
        dirty      = ($porcelain.Count -gt 0)
        porcelain  = @($porcelain)
        promptHash = $promptHash
        baseUrl    = $BaseUrl
        startedAt  = $startedAt
    }
    Write-Utf8Json (Join-Path $runDir "meta.json") $meta
}

# ---------- 步骤 0：探针 ----------
# 用具体检索问句，避免模型只口头说「可以查」却不给链接。
$probeQuestion = "请检索本平台文档，说明 AgentStateStore 是做什么的，并给出文档链接。"
Write-Host ("==== Probe: {0} ====" -f $probeQuestion) -ForegroundColor Cyan
$probeSession = [guid]::NewGuid().ToString()
try {
    $probe = Invoke-Chat $probeQuestion $probeSession
} catch {
    throw "步骤 0 失败：本地对话不可用（$BaseUrl）。先起用户进程并确认 Flyway 含 V49。错误: $($_.Exception.Message)"
}
$probeData = $probe.Data.data
if (-not $probeData) {
    throw "步骤 0 失败：响应无 data（可能鉴权失败）。不要把 token 贴进对话。"
}
$probeAnswer = [string]$probeData.text
$probeRunId = [string]$probeData.runId
$probeRun = Get-RunDetail $probeRunId
$probeSteps = $null
if ($probeRun -and $probeRun.data -and $null -ne $probeRun.data.steps) {
    $probeSteps = @($probeRun.data.steps).Count
}
$probeHasLink = Test-AnswerHasDocLink $probeAnswer
Write-Host ("probe runId 存在: {0}; 答案含文档链接: {1}; runStepsCount: {2}" -f ([bool]$probeRunId), $probeHasLink, $probeSteps)
Write-Host ("probe answer 截断: {0}" -f (Truncate-Text $probeAnswer 160))
if ($probeAnswer -match "[æãå]") {
    throw "步骤 0 失败：答案仍是乱码（含 æ/ã/å）。响应没有按 UTF-8 解码。"
}
if (-not $probeHasLink) {
    Write-Host "答案没有 /docs/doc/ 链接。按 Prompt 停下，不改产品代码。" -ForegroundColor Red
    exit 2
}
if ($ProbeOnly) {
    Write-Host "ProbeOnly 通过，退出。"
    exit 0
}

# ---------- 步骤 2/3：逐条用例 ----------
$startedAt = [DateTimeOffset]::Now.ToString("o")
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$runDir = Join-Path $RepoRoot "eval\runs\$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-RunMeta $runDir $startedAt

$cases = Import-Csv -Path $CasesPath
$index = 0
foreach ($case in $cases) {
    $index++
    $id = $case.id
    $question = $case.question
    Write-Host ""
    Write-Host ("==== {0}/{1} {2} ====" -f $index, $cases.Count, $id) -ForegroundColor Cyan
    $sessionId = [guid]::NewGuid().ToString()
    $started = Get-Date
    try {
        $chat = Invoke-Chat $question $sessionId
        $data = $chat.Data.data
        $answer = [string]$data.text
        $runId = [string]$data.runId
        $runDetail = Get-RunDetail $runId
        $runStepsCount = $null
        $runSteps = @()
        if ($runDetail -and $runDetail.data -and $null -ne $runDetail.data.steps) {
            $runSteps = @($runDetail.data.steps)
            $runStepsCount = $runSteps.Count
        }
        $payload = [ordered]@{
            id               = $id
            question         = $question
            expectDoc        = $case.expect_doc
            mustRefuse       = $case.must_refuse
            category         = $case.category
            sessionId        = $sessionId
            runId            = $runId
            elapsedMs        = $chat.ElapsedMs
            answerHasDocLink = (Test-AnswerHasDocLink $answer)
            runStepsCount    = $runStepsCount
            citedDocs        = @(Get-CitedDocs $answer)
            answer           = $answer
            runSteps         = $runSteps
            startedAt        = $started.ToString("o")
        }
        Write-Utf8Json (Join-Path $runDir ("{0}.json" -f $id)) $payload
    } catch {
        $err = $_.Exception.Message
        $payload = [ordered]@{
            id         = $id
            question   = $question
            expectDoc  = $case.expect_doc
            mustRefuse = $case.must_refuse
            error      = $err
            startedAt  = $started.ToString("o")
        }
        Write-Utf8Json (Join-Path $runDir ("{0}.json" -f $id)) $payload
        Write-Host ("FAIL {0}: {1}" -f $id, $err) -ForegroundColor Yellow
    }
    if ($index -lt $cases.Count) {
        Start-Sleep -Seconds 2
    }
}

& (Join-Path $PSScriptRoot "eval-summary.ps1") -RunDir $runDir

Write-Host ""
Write-Host "==== DONE ====" -ForegroundColor Green
Write-Host "runDir: $runDir"
Write-Host "判定列留空，请人工填写。"
