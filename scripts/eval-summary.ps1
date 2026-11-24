# 从一次评测目录里的 Q*.json 重建 summary.md。不打接口，不填三列判定。
# 用法（仓库根目录或任意目录）：
#   .\scripts\eval-summary.ps1 -RunDir eval\runs\20261124-2134
param(
    [Parameter(Mandatory = $true)]
    [string]$RunDir
)

$ErrorActionPreference = "Stop"

function Truncate-Text([string]$text, [int]$max = 200) {
    if (-not $text) {
        return ""
    }
    # 表格单元格不能带换行或竖线，否则行会被拆开。
    $flat = ($text -replace "\s+", " ").Trim() -replace "\|", "/"
    if ($flat.Length -le $max) {
        return $flat
    }
    return $flat.Substring(0, $max) + "…"
}

if (-not (Test-Path -LiteralPath $RunDir)) {
    throw "评测目录不存在: $RunDir"
}
$RunDir = (Resolve-Path -LiteralPath $RunDir).Path

$files = @(Get-ChildItem -LiteralPath $RunDir -Filter "Q*.json" | Sort-Object Name)
$rows = New-Object System.Collections.Generic.List[string]
$rows.Add("| id | 答案含文档链接 | 字数 | 引用文档 | 答案（截断 200 字） | 引用命中 | 事实正确 | 是否编造 |")
$rows.Add("|----|----------------|------|---------|--------------------|---------|---------|---------|")

foreach ($file in $files) {
    # 显式 UTF-8，避免 Windows PowerShell 按系统代码页把中文读坏。
    $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $item = $raw | ConvertFrom-Json
    $id = [string]$item.id
    if ($item.error) {
        $rows.Add(("| {0} | 否 | 0 |  | 错误: {1} | | | |" -f $id, (Truncate-Text ([string]$item.error) 160)))
        continue
    }
    $answer = [string]$item.answer
    $linkCell = if ($item.answerHasDocLink) { "是" } else { "否" }
    $len = if ($null -eq $item.answer) { 0 } else { $answer.Length }
    $cited = @()
    if ($item.citedDocs) {
        $cited = @($item.citedDocs | ForEach-Object { [string]$_ })
    }
    $citeCell = if ($cited.Count -gt 0) { ($cited -join ", ") } else { "" }
    $rows.Add(("| {0} | {1} | {2} | {3} | {4} | | | |" -f $id, $linkCell, $len, $citeCell, (Truncate-Text $answer 200)))
}

$rows.Add("")
$rows.Add("统计：引用命中 __/20 · 事实正确 __/20 · 编造 __ · 负样本正确拒答 __/5")
$summaryPath = Join-Path $RunDir "summary.md"
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($summaryPath, (($rows -join "`n") + "`n"), $utf8)
Write-Host "summary: $summaryPath"
