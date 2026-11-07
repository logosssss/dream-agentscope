#!/usr/bin/env bash
# 现网 nohup 重启用户/管理进程。只按 jar 路径匹配，避免误杀 Nacos(:8080)。
# 用法: bash remote-restart-agentscope.sh user|admin|both
# 约束: 不改 Nginx；不打印 run.env；JVM/端口与现网手工启动保持一致。
set -euo pipefail

APP_DIR=/opt/dream-agentscope
MODE="${1:-}"

if [ -z "${MODE}" ]; then
  echo "usage: $0 user|admin|both"
  exit 2
fi

mkdir -p "${APP_DIR}/logs"

# 结束匹配该 jar 的 java 进程；没有进程视为已停。
stop_by_jar() {
  local jar_path="$1"
  local pids
  pids=$(pgrep -f "java .*${jar_path}" || true)
  if [ -z "${pids}" ]; then
    echo "no process for ${jar_path}"
    return 0
  fi
  echo "stopping: ${pids} (${jar_path})"
  # 先 TERM，再短等，必要时 KILL。
  kill ${pids} 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    pids=$(pgrep -f "java .*${jar_path}" || true)
    [ -z "${pids}" ] && break
    sleep 1
  done
  pids=$(pgrep -f "java .*${jar_path}" || true)
  if [ -n "${pids}" ]; then
    echo "force kill: ${pids}"
    kill -9 ${pids} 2>/dev/null || true
  fi
}

# 载入远端环境变量；内容不得 echo 到终端。
# 现网 run.env 含 NUL：直接 source / 简单去 NUL 都会把多行粘成一行，丢掉 NACOS_ADDR。
# 用 Python 按 NUL/换行切开，写出可 source 的 export 临时文件（shlex 引号），再载入。
load_env() {
  local f="${APP_DIR}/run.env"
  if [ ! -f "${f}" ]; then
    echo "WARN: missing ${f}"
    return 0
  fi
  local cleaned
  cleaned=$(mktemp /tmp/agentscope-runenv.XXXXXX)
  if ! python3 - "${f}" "${cleaned}" <<'PY'
import shlex
import sys
from pathlib import Path

src, dst = Path(sys.argv[1]), Path(sys.argv[2])
raw = src.read_bytes()
if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
    text = raw.decode("utf-16")
elif b"\x00" in raw:
    text = "\n".join(chunk.decode("utf-8", "replace") for chunk in raw.split(b"\x00"))
else:
    text = raw.decode("utf-8", "replace")

exports = []
for line in text.splitlines():
    line = line.strip().lstrip("\ufeff")
    if not line or line.startswith("#"):
        continue
    if line.startswith("export "):
        line = line[7:].lstrip()
    if "=" not in line:
        continue
    key, val = line.split("=", 1)
    key = key.strip()
    if not key.isidentifier():
        continue
    exports.append(f"export {key}={shlex.quote(val)}")

dst.write_text("\n".join(exports) + ("\n" if exports else ""), encoding="utf-8")
print(f"run.env prepared exports={len(exports)}")
PY
  then
    rm -f "${cleaned}"
    echo "WARN: failed to parse run.env"
    return 1
  fi
  set -a
  # shellcheck disable=SC1090
  . "${cleaned}"
  set +a
  rm -f "${cleaned}"
  if [ -z "${NACOS_ADDR:-}" ]; then
    echo "WARN: NACOS_ADDR still empty after load_env"
  else
    echo "NACOS_ADDR is set (value not printed)"
  fi
}

start_user() {
  if [ ! -f "${APP_DIR}/boot.jar" ]; then
    echo "missing ${APP_DIR}/boot.jar"
    exit 1
  fi
  load_env
  cd "${APP_DIR}"
  nohup java -Xms256m -Xmx768m -XX:+UseG1GC \
    -jar "${APP_DIR}/boot.jar" --server.port=8093 \
    >> "${APP_DIR}/logs/boot.out" 2>&1 &
  echo "started user pid=$! port=8093"
}

start_admin() {
  if [ ! -f "${APP_DIR}/admin.jar" ]; then
    echo "missing ${APP_DIR}/admin.jar"
    exit 1
  fi
  load_env
  cd "${APP_DIR}"
  nohup java -Xms256m -Xmx768m -XX:+UseG1GC \
    -jar "${APP_DIR}/admin.jar" --server.port=8100 \
    >> "${APP_DIR}/logs/admin.out" 2>&1 &
  echo "started admin pid=$! port=8100"
}

case "${MODE}" in
  user)
    stop_by_jar "${APP_DIR}/boot.jar"
    start_user
    ;;
  admin)
    stop_by_jar "${APP_DIR}/admin.jar"
    start_admin
    ;;
  both)
    stop_by_jar "${APP_DIR}/boot.jar"
    stop_by_jar "${APP_DIR}/admin.jar"
    start_user
    start_admin
    ;;
  *)
    echo "usage: $0 user|admin|both"
    exit 2
    ;;
esac
