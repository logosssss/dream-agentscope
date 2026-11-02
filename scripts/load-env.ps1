# 把仓库根目录 .env 载入当前进程环境。值按字面量，不展开 * ! ^。
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    throw ".env not found: $envFile"
}
Get-Content -LiteralPath $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_
    if ($null -eq $line) { return }
    $trim = $line.Trim()
    if ($trim.Length -eq 0 -or $trim.StartsWith("#")) { return }
    $eq = $trim.IndexOf("=")
    if ($eq -lt 1) { return }
    $name = $trim.Substring(0, $eq).Trim()
    $value = $trim.Substring($eq + 1)
    if ($name.Length -eq 0) { return }
    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
}
Write-Host "loaded $envFile"
Write-Host ("AGENTSCOPE_REDIS_HOST=" + [System.Environment]::GetEnvironmentVariable("AGENTSCOPE_REDIS_HOST", "Process"))
Write-Host ("NACOS_ADDR=" + [System.Environment]::GetEnvironmentVariable("NACOS_ADDR", "Process"))
$adminToken = [System.Environment]::GetEnvironmentVariable("AGENTSCOPE_ADMIN_TOKEN", "Process")
if ([string]::IsNullOrWhiteSpace($adminToken)) {
    Write-Host "AGENTSCOPE_ADMIN_TOKEN=(empty)"
} else {
    Write-Host "AGENTSCOPE_ADMIN_TOKEN=(set)"
}
