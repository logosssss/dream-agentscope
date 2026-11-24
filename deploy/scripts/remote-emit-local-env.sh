#!/usr/bin/env bash
# 在现网机器上跑：把 run.env 收成给开发机用的环境文件。
# 把 NACOS_ADDR 里的 127.0.0.1 换成公网机地址，便于本机进程连 Nacos。
# 不往 stdout 打任何密钥值；只写目标文件。
set -euo pipefail

SRC=/opt/dream-agentscope/run.env
DST=${1:-/tmp/dream-agentscope-local.env}
PUBLIC_HOST=${2:-47.119.115.162}

if [ ! -f "$SRC" ]; then
  echo "missing $SRC" >&2
  exit 1
fi

python3 - "$SRC" "$DST" "$PUBLIC_HOST" <<'PY'
import shlex
import sys
from pathlib import Path

src, dst, public_host = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
raw = src.read_bytes()
if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
    text = raw.decode("utf-16")
elif b"\x00" in raw:
    text = "\n".join(chunk.decode("utf-8", "replace") for chunk in raw.split(b"\x00"))
else:
    text = raw.decode("utf-8", "replace")

lines = []
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
    if key == "NACOS_ADDR":
        # 现网进程连本机 Nacos；开发机要改成公网。
        val = val.replace("127.0.0.1", public_host).replace("localhost", public_host)
        if public_host not in val and val.strip():
            # 只有端口时补主机
            if val.strip().startswith(":"):
                val = public_host + val.strip()
            elif ":" not in val.strip():
                val = f"{public_host}:8848"
    lines.append(f"{key}={val}")

# 保证本机启动常用端口提示写在注释外：开发机用 8093
if not any(l.startswith("SERVER_PORT=") for l in lines):
    lines.append("SERVER_PORT=8093")

dst.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"wrote keys={len(lines)} path={dst}")
PY
