# 构建产物上传：web-admin/dist → 现网 /opt/dream-saas/ops，并启用 Nginx /ops + /api/admin。
# 构建时用网关模式，禁止把 Admin Token 打进浏览器包。
param(
    [string]$Server = "root@47.119.115.162",
    [string]$SshKey = "$env:USERPROFILE\.ssh\id_ed25519_dream_saas",
    [switch]$SkipBuild,
    [switch]$SkipNginx
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$scpArgs = @(
    "-i", $SshKey,
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

if (-not (Test-Path $SshKey)) {
    throw "SSH key not found: $SshKey"
}

$adminRoot = Join-Path $RepoRoot "web-admin"
$dist = Join-Path $adminRoot "dist"

if (-not $SkipBuild) {
    if ($env:VITE_ADMIN_TOKEN) {
        throw "检测到 VITE_ADMIN_TOKEN：现网构建禁止打进管理前端包（由 Nginx 注入）"
    }
    Write-Host "== web-admin: pnpm build (gateway /ops/) =="
    Push-Location $adminRoot
    try {
        $env:VITE_ADMIN_GATEWAY = "true"
        $env:VITE_ADMIN_BASE = "/ops/"
        if (Test-Path (Join-Path $adminRoot "pnpm-lock.yaml")) {
            pnpm install
            if ($LASTEXITCODE -ne 0) { throw "pnpm install failed" }
            pnpm run build
        } else {
            npm ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
            npm run build
        }
        if ($LASTEXITCODE -ne 0) { throw "web-admin build failed" }
    } finally {
        Remove-Item Env:VITE_ADMIN_GATEWAY -ErrorAction SilentlyContinue
        Remove-Item Env:VITE_ADMIN_BASE -ErrorAction SilentlyContinue
        Pop-Location
    }
}

if (-not (Test-Path (Join-Path $dist "index.html"))) {
    throw "missing web-admin/dist/index.html"
}

$applySh = Join-Path $PSScriptRoot "remote-apply-web-admin-dist.sh"
$nginxSh = Join-Path $PSScriptRoot "remote-enable-web-admin-nginx.sh"
if (-not (Test-Path $applySh)) { throw "missing $applySh" }
if (-not (Test-Path $nginxSh)) { throw "missing $nginxSh" }

$tgz = Join-Path $env:TEMP "dream-agentscope-web-admin-dist.tgz"
if (Test-Path $tgz) { Remove-Item -Force $tgz }

Write-Host "== pack web-admin/dist =="
Push-Location $dist
try {
    & tar -czf $tgz .
    if ($LASTEXITCODE -ne 0) { throw "tar pack failed" }
} finally {
    Pop-Location
}
Write-Host ("tarball: {0:N1} MB" -f ((Get-Item $tgz).Length / 1MB))

Write-Host "== scp + apply =="
Ssh-Run "rm -rf /tmp/dream-web-admin-dist-staging /tmp/dream-web-admin-dist.tgz && mkdir -p /tmp/dream-web-admin-dist-staging"
& scp @scpArgs $tgz "${Server}:/tmp/dream-web-admin-dist.tgz"
if ($LASTEXITCODE -ne 0) { throw "scp tarball failed" }
Ssh-Run "tar -xzf /tmp/dream-web-admin-dist.tgz -C /tmp/dream-web-admin-dist-staging && test -f /tmp/dream-web-admin-dist-staging/index.html && rm -f /tmp/dream-web-admin-dist.tgz"

# 统一 LF，避免 bash 因 CRLF 挂掉
& scp @scpArgs $applySh "${Server}:/tmp/remote-apply-web-admin-dist.sh"
Ssh-Run "sed -i 's/\r$//' /tmp/remote-apply-web-admin-dist.sh; bash /tmp/remote-apply-web-admin-dist.sh"

if (-not $SkipNginx) {
    Write-Host "== enable nginx /ops =="
    & scp @scpArgs $nginxSh "${Server}:/tmp/remote-enable-web-admin-nginx.sh"
    Ssh-Run "sed -i 's/\r$//' /tmp/remote-enable-web-admin-nginx.sh; bash /tmp/remote-enable-web-admin-nginx.sh"
}

Write-Host ""
Write-Host "公网入口: https://www.dream-saas.com/ops/" -ForegroundColor Green
Write-Host "== web-admin ops deployed =="
