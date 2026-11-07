# 上传 web/dist：打成单包 scp，再远端执行 remote-apply-web-dist.sh（整目录替换）。
# 多文件 scp -r 易被现网 SSH 掐断；单包更稳。
param(
    [string]$Server = "root@47.119.115.162",
    [string]$SshKey = "$env:USERPROFILE\.ssh\id_ed25519"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$scpArgs = @(
    "-i", $SshKey,
    "-O",
    "-o", "StrictHostKeyChecking=no",
    "-o", "ConnectTimeout=20",
    "-o", "ServerAliveInterval=15",
    "-o", "ServerAliveCountMax=12",
    "-o", "TCPKeepAlive=yes"
)
$sshArgs = @(
    "-i", $SshKey,
    "-o", "BatchMode=yes",
    "-o", "ConnectTimeout=20",
    "-o", "ServerAliveInterval=15",
    "-o", "ServerAliveCountMax=12",
    "-o", "TCPKeepAlive=yes",
    $Server
)

function Ssh-Run([string]$remoteCmd) {
    & ssh @sshArgs $remoteCmd
    if ($LASTEXITCODE -ne 0) { throw "ssh failed: $remoteCmd" }
}

$dist = Join-Path $RepoRoot "web\dist"
if (-not (Test-Path (Join-Path $dist "index.html"))) {
    throw "missing web/dist — run: cd web && npm ci && npm run build"
}

$applySh = Join-Path $PSScriptRoot "remote-apply-web-dist.sh"
if (-not (Test-Path $applySh)) {
    throw "missing $applySh"
}

$tgz = Join-Path $env:TEMP "dream-agentscope-web-dist.tgz"
if (Test-Path $tgz) { Remove-Item -Force $tgz }

Write-Host "== pack web/dist =="
Push-Location $dist
try {
    & tar -czf $tgz .
    if ($LASTEXITCODE -ne 0) { throw "tar pack failed" }
} finally {
    Pop-Location
}
$localSize = (Get-Item -LiteralPath $tgz).Length
Write-Host ("tarball: {0:N1} MB" -f ($localSize / 1MB))

Write-Host "== scp web dist tarball =="
Ssh-Run "rm -rf /tmp/dream-web-dist-staging /tmp/dream-web-dist.tgz && mkdir -p /tmp/dream-web-dist-staging"
& scp @scpArgs $tgz "${Server}:/tmp/dream-web-dist.tgz"
if ($LASTEXITCODE -ne 0) { throw "scp tarball failed" }

Ssh-Run "tar -xzf /tmp/dream-web-dist.tgz -C /tmp/dream-web-dist-staging && test -f /tmp/dream-web-dist-staging/index.html && rm -f /tmp/dream-web-dist.tgz"

& scp @scpArgs $applySh "${Server}:/tmp/remote-apply-web-dist.sh"
if ($LASTEXITCODE -ne 0) { throw "scp apply script failed" }
Ssh-Run "bash /tmp/remote-apply-web-dist.sh"
Write-Host "== web dist deployed =="
