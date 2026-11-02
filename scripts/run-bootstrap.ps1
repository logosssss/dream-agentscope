# 加载 .env 后启动用户进程。在仓库根目录执行：
#   powershell -File scripts\run-bootstrap.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
& (Join-Path $PSScriptRoot "load-env.ps1")
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "E:\Program Files\Java\jdk-21.0.11"
}
$hostName = [System.Environment]::GetEnvironmentVariable("AGENTSCOPE_REDIS_HOST", "Process")
if ([string]::IsNullOrWhiteSpace($hostName)) {
    throw "AGENTSCOPE_REDIS_HOST is empty after loading .env"
}
mvn -pl agent-bootstrap -am spring-boot:run
