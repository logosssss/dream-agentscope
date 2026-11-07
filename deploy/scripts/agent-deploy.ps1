# dream-agentscope 现网一键部署（关卡制，失败即停，零自动重试）
# 用法（仓库根目录）:
#   .\deploy\scripts\agent-deploy.ps1 -Target frontend
#   .\deploy\scripts\agent-deploy.ps1 -Target user
#   .\deploy\scripts\agent-deploy.ps1 -Target admin
#   .\deploy\scripts\agent-deploy.ps1 -Target full
#   .\deploy\scripts\agent-deploy.ps1 -Target full -SkipFrontend
#
# 规范: deploy/AGENT-DEPLOY.md · AGENT-DEPLOY-USAGE.md
# 禁止: 改 Nginx / 覆盖 run.env / 读 Nacos 正文进对话 / 调用旧 Dream-SaaS agent-deploy
param(
    [ValidateSet("frontend", "user", "admin", "full")]
    [string]$Target = "full",
    [string]$Server = "root@47.119.115.162",
    [string]$SshKey = "$env:USERPROFILE\.ssh\id_ed25519",
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [switch]$SkipUpload,
    [int]$HealthWaitSec = 180
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $RepoRoot

if (-not (Test-Path $SshKey)) {
    throw "GATE1 FAIL: SSH key not found: $SshKey"
}

# ServerAlive*: 大 JAR / dist 长时间 scp 时避免中间设备空闲掐断
$scpArgs = @(
    "-i", $SshKey,
    "-O",
    "-o", "StrictHostKeyChecking=no",
    "-o", "ConnectTimeout=20",
    "-o", "Compression=yes",
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

$AppDir = "/opt/dream-agentscope"
$WebDist = "/opt/dream-saas/web-dist"
$UserPort = 8093
$AdminPort = 8100

function Write-Gate([string]$n, [string]$msg) {
    Write-Host ""
    Write-Host ("==== Gate{0}: {1} ====" -f $n, $msg) -ForegroundColor Cyan
}

function Fail-Gate([string]$n, [string]$msg, [string]$hint) {
    Write-Host ("GATE{0} FAIL: {1}" -f $n, $msg) -ForegroundColor Red
    if ($hint) { Write-Host "只看: $hint" -ForegroundColor Yellow }
    Write-Host @"
--- 汇报草稿 ---
目标: $Target
Gate: 卡在 Gate$n
回滚: JAR 用 $AppDir/backup-<ts>/；前端用 /opt/dream-saas/backup/web-dist-*.tgz
"@
    exit ([int]$n)
}

function Ssh-Run([string]$remoteCmd) {
    & ssh @sshArgs $remoteCmd
    if ($LASTEXITCODE -ne 0) { throw "ssh exit=$LASTEXITCODE" }
}

function Ssh-Capture([string]$remoteCmd) {
    $out = & ssh @sshArgs $remoteCmd 2>&1 | Out-String
    return @{ Code = $LASTEXITCODE; Out = $out }
}

# 大 JAR 先 tar.gz 再 scp，避免 Windows OpenSSH 对百兆明文 Broken pipe。
function Scp-File([string]$local, [string]$remote) {
    if (-not (Test-Path $local)) { throw "missing local file: $local" }
    $expected = (Get-Item -LiteralPath $local).Length
    $tmp = "$remote.uploading"
    if ($expected -gt 40MB) {
        $packDir = Split-Path -Parent $local
        $leaf = Split-Path -Leaf $local
        $stamp = Get-Date -Format "yyyyMMddHHmmss"
        $tgz = Join-Path $env:TEMP ("agentscope-upload-$stamp-$PID-$leaf.tgz")
        $remoteDir = ($remote -replace '/[^/]+$', '')
        $remoteTgz = "/tmp/agentscope-upload-$stamp-$leaf.tgz"
        Write-Host ("== tar.gz then scp ({0:N0} MB) ==" -f ($expected / 1MB))
        & tar -czf $tgz -C $packDir $leaf
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tgz)) { throw "tar pack failed: $local" }
        Write-Host ("packed: {0:N1} MB" -f ((Get-Item $tgz).Length / 1MB))
        try {
            & scp @scpArgs $tgz "${Server}:${remoteTgz}"
            if ($LASTEXITCODE -ne 0) { throw "scp failed: $tgz -> $remoteTgz" }
            $remoteSizeTxt = & ssh @sshArgs @"
set -e
mkdir -p '$remoteDir'
extractDir=`$(mktemp -d /tmp/agentscope-upload-XXXXXX)
tar -xzf '$remoteTgz' -C "`$extractDir"
rm -f '$remoteTgz'
test -f "`$extractDir/$leaf"
mv -f "`$extractDir/$leaf" '$remote'
rm -rf "`$extractDir"
stat -c %s '$remote'
"@
            if ($LASTEXITCODE -ne 0) { throw "ssh finalize failed: $remote" }
        } finally {
            Remove-Item -Force $tgz -ErrorAction SilentlyContinue
        }
        $remoteSizeTxt = ("$remoteSizeTxt").Trim()
        if ($remoteSizeTxt -ne "$expected") {
            throw "upload size mismatch: local=$expected remote=$remoteSizeTxt ($local)"
        }
        return
    }
    & scp @scpArgs $local "${Server}:${tmp}"
    if ($LASTEXITCODE -ne 0) { throw "scp failed: $local -> $tmp" }
    $remoteSizeTxt = & ssh @sshArgs "mv -f '$tmp' '$remote' && stat -c %s '$remote'"
    if ($LASTEXITCODE -ne 0) { throw "ssh finalize failed: $remote" }
    $remoteSizeTxt = ("$remoteSizeTxt").Trim()
    if ($remoteSizeTxt -ne "$expected") {
        throw "upload size mismatch: local=$expected remote=$remoteSizeTxt ($local)"
    }
}

function Need-Frontend {
    return ($Target -eq "frontend" -or ($Target -eq "full" -and -not $SkipFrontend))
}
function Need-User { return ($Target -eq "user" -or $Target -eq "full") }
function Need-Admin { return ($Target -eq "admin" -or $Target -eq "full") }

# 取 Spring Boot 可执行包：排除 sources/javadoc/original。
function Resolve-ModuleJar([string]$moduleDir, [string]$artifactPrefix) {
    $targetDir = Join-Path $RepoRoot (Join-Path $moduleDir "target")
    if (-not (Test-Path $targetDir)) { return $null }
    $jar = Get-ChildItem -Path $targetDir -Filter "$artifactPrefix-*.jar" -File |
        Where-Object {
            $_.Name -notmatch "(sources|javadoc|original)"
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) { return $null }
    return $jar.FullName
}

$built = @()

# ---------- Gate 1 ----------
Write-Gate 1 "Preflight"
try {
    $r = Ssh-Capture "df -h / | tail -1; test -d $AppDir && echo APP_DIR_OK; test -d /opt/dream-saas && echo SAAS_DIR_OK; echo PREFLIGHT_OK"
    if ($r.Code -ne 0 -or $r.Out -notmatch "PREFLIGHT_OK") {
        Fail-Gate 1 "SSH/磁盘/目录探测失败" "SSH 报错本身；勿继续 Build"
    }
    if ($r.Out -notmatch "APP_DIR_OK") {
        Fail-Gate 1 "缺少 $AppDir" "确认现网进程目录"
    }
    Write-Host $r.Out.Trim()
    if ($r.Out -match "9[5-9]%|100%") {
        Fail-Gate 1 "根分区磁盘过满" "df -h /"
    }
    Write-Host "PASS Gate1"
} catch {
    Fail-Gate 1 $_.Exception.Message "SSH 报错本身；勿继续 Build"
}

# ---------- Gate 2 ----------
Write-Gate 2 "LocalBuild"
if ($SkipBuild) {
    Write-Host "SkipBuild=on"
} else {
    if (Need-Frontend) {
        if ($env:VITE_CALLER_TOKEN) {
            Fail-Gate 2 "检测到 VITE_CALLER_TOKEN：生产构建禁止打进前端包" "清掉该环境变量后重跑；Token 由 Nginx 注入"
        }
        Write-Host "== web: npm ci && npm run build =="
        Push-Location (Join-Path $RepoRoot "web")
        try {
            npm ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
            npm run build
            if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }
        } finally { Pop-Location }
        $idx = Join-Path $RepoRoot "web\dist\index.html"
        if (-not (Test-Path $idx)) { Fail-Gate 2 "缺少 web/dist/index.html" "本机 npm 终端最后 80 行" }
        $built += "web/dist"
    }

    $pl = @()
    if (Need-User) { $pl += "agent-bootstrap" }
    if (Need-Admin) { $pl += "agent-admin" }
    $pl = $pl | Select-Object -Unique
    if ($pl.Count -gt 0) {
        $plArg = ($pl -join ",")
        Write-Host "== mvn -pl $plArg -am package -DskipTests =="
        mvn -pl $plArg -am package -DskipTests
        if ($LASTEXITCODE -ne 0) { Fail-Gate 2 "mvn package 失败" "本机 mvn 终端最后 80 行" }
    }
}

if (Need-Frontend) {
    $idx = Join-Path $RepoRoot "web\dist\index.html"
    if (-not (Test-Path $idx)) { Fail-Gate 2 "缺少 web/dist/index.html" "本机 npm 终端最后 80 行" }
}

$userJar = $null
$adminJar = $null
if (Need-User) {
    $userJar = Resolve-ModuleJar "agent-bootstrap" "agent-bootstrap"
    if (-not $userJar) { Fail-Gate 2 "缺少 agent-bootstrap/target/agent-bootstrap-*.jar" "本机 mvn 终端最后 80 行" }
    $built += $userJar
    Write-Host ("OK user jar ({0:N0} MB): {1}" -f ((Get-Item $userJar).Length / 1MB), $userJar)
}
if (Need-Admin) {
    $adminJar = Resolve-ModuleJar "agent-admin" "agent-admin"
    if (-not $adminJar) { Fail-Gate 2 "缺少 agent-admin/target/agent-admin-*.jar" "本机 mvn 终端最后 80 行" }
    $built += $adminJar
    Write-Host ("OK admin jar ({0:N0} MB): {1}" -f ((Get-Item $adminJar).Length / 1MB), $adminJar)
}
Write-Host "PASS Gate2"

# ---------- Gate 3 ----------
Write-Gate 3 "Upload"
if ($SkipUpload) {
    Write-Host "SkipUpload=on"
} else {
    if (Need-Frontend) {
        Write-Host "== web-dist-scp-deploy.ps1 =="
        & (Join-Path $PSScriptRoot "web-dist-scp-deploy.ps1") -Server $Server -SshKey $SshKey
        if ($LASTEXITCODE -ne 0) { Fail-Gate 3 "前端上传失败" "scp/ssh 与远端 ls $WebDist" }
        $chk = Ssh-Capture "test -f $WebDist/index.html && echo WEB_OK"
        if ($chk.Out -notmatch "WEB_OK") { Fail-Gate 3 "远端 web-dist 无 index.html" "远端 ls $WebDist" }
    }

    if ((Need-User) -or (Need-Admin)) {
        $stampCmd = 'date +%Y%m%d%H%M%S'
        $stamp = (& ssh @sshArgs $stampCmd).Trim()
        if (-not $stamp) { $stamp = Get-Date -Format "yyyyMMddHHmmss" }
        $backupDir = "$AppDir/backup-$stamp"
        Ssh-Run "mkdir -p '$backupDir'"

        if (Need-User) {
            Ssh-Run "if test -f $AppDir/boot.jar; then cp -a $AppDir/boot.jar '$backupDir/boot.jar'; fi"
            Write-Host "== scp boot.jar =="
            Scp-File $userJar "$AppDir/boot.jar"
            $chk = Ssh-Capture "test -f $AppDir/boot.jar && ls -lh $AppDir/boot.jar && echo JAR_OK"
            if ($chk.Out -notmatch "JAR_OK") { Fail-Gate 3 "远端缺少 boot.jar" "scp/ssh 与远端 ls" }
            Write-Host $chk.Out.Trim()
        }
        if (Need-Admin) {
            Ssh-Run "if test -f $AppDir/admin.jar; then cp -a $AppDir/admin.jar '$backupDir/admin.jar'; fi"
            Write-Host "== scp admin.jar =="
            Scp-File $adminJar "$AppDir/admin.jar"
            $chk = Ssh-Capture "test -f $AppDir/admin.jar && ls -lh $AppDir/admin.jar && echo JAR_OK"
            if ($chk.Out -notmatch "JAR_OK") { Fail-Gate 3 "远端缺少 admin.jar" "scp/ssh 与远端 ls" }
            Write-Host $chk.Out.Trim()
        }
        Write-Host "backup dir: $backupDir"
    }
}
Write-Host "PASS Gate3"

# ---------- Gate 4 ----------
Write-Gate 4 "Restart"
if (-not (Need-User) -and -not (Need-Admin)) {
    Write-Host "frontend-only: skip Restart"
} else {
    $restartLocal = Join-Path $PSScriptRoot "remote-restart-agentscope.sh"
    if (-not (Test-Path $restartLocal)) {
        Fail-Gate 4 "缺少 remote-restart-agentscope.sh" $restartLocal
    }
    try {
        Scp-File $restartLocal "/tmp/remote-restart-agentscope.sh"
        if ((Need-User) -and (Need-Admin)) {
            Ssh-Run "bash /tmp/remote-restart-agentscope.sh both"
        } elseif (Need-User) {
            Ssh-Run "bash /tmp/remote-restart-agentscope.sh user"
        } else {
            Ssh-Run "bash /tmp/remote-restart-agentscope.sh admin"
        }
    } catch {
        $hint = "tail -n 80 $AppDir/logs/boot.out; tail -n 80 $AppDir/logs/admin.out"
        Fail-Gate 4 "重启失败: $($_.Exception.Message)" $hint
    }
}
Write-Host "PASS Gate4"

# ---------- Gate 5 ----------
Write-Gate 5 "Health"
function Wait-RemoteHealth([string]$label, [string]$probeCmd, [string]$okPattern, [string]$failHint) {
    $deadline = (Get-Date).AddSeconds($HealthWaitSec)
    $last = ""
    while ((Get-Date) -lt $deadline) {
        $r = Ssh-Capture $probeCmd
        $last = $r.Out
        if ($r.Out -match $okPattern) {
            Write-Host ("PASS {0}: {1}" -f $label, ($r.Out.Trim() -replace "\s+", " "))
            return
        }
        Start-Sleep -Seconds 3
    }
    Write-Host $last
    Fail-Gate 5 "$label 健康检查超时 (${HealthWaitSec}s)" $failHint
}

if (Need-User) {
    Wait-RemoteHealth "user" `
        "curl -s -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:$UserPort/health/live" `
        "^200" `
        "tail -n 80 $AppDir/logs/boot.out"
}

if (Need-Admin) {
    Wait-RemoteHealth "admin" `
        "curl -s -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:$AdminPort/health/live" `
        "^200" `
        "tail -n 80 $AppDir/logs/admin.out"
}

if (Need-Frontend) {
    $pub = Ssh-Capture "curl -sI -m 5 https://www.dream-saas.com/ | head -n 3"
    Write-Host $pub.Out.Trim()
    if ($pub.Out -notmatch "HTTP/2 200|HTTP/1\.1 200") {
        Fail-Gate 5 "公网首页非 200" "nginx -t; tail -n 40 /var/log/nginx/error.log"
    }
    Write-Host "PASS public site"
}

Write-Host ""
Write-Host "==== ALL GATES PASS ====" -ForegroundColor Green
Write-Host @"
目标: $Target
Gate: 1..5 全部通过
产物: $($built -join ', ')
远端: $WebDist ; $AppDir/boot.jar ; $AppDir/admin.jar
健康检查: user :$UserPort /health/live；admin :$AdminPort /health/live；公网首页（若含 frontend）
回滚: $AppDir/backup-<ts>/ 或 /opt/dream-saas/backup/web-dist-*.tgz
"@
exit 0
