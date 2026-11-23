#!/usr/bin/env bash
# 现网：把 agent-bootstrap.yaml 的 web-tools.enabled 写成 true 并发布；不打印配置正文。
set -euo pipefail
APP_DIR=/opt/dream-agentscope
cd "$APP_DIR"

cleaned=$(mktemp /tmp/agentscope-runenv.XXXXXX)
python3 - "${APP_DIR}/run.env" "${cleaned}" <<'PY'
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
set -a
# shellcheck disable=SC1090
. "${cleaned}"
set +a
rm -f "${cleaned}"

set +e
for f in nohup.out boot.out user.out logs/spring.log logs/user.log; do
  if [ -f "$f" ]; then
    echo "LOG_FILE=$f"
    grep -E "V42__web_tools|Migrating schema to version \"?42|web-tools|web_tools|nacos business config refreshed" "$f" | tail -n 12
  fi
done
ls -t logs/*.log 2>/dev/null | head -3 | while read -r lf; do
  echo "LOG_FILE=$lf"
  grep -E "V42__web_tools|Migrating schema to version \"?42|web-tools|nacos business config refreshed" "$lf" | tail -n 8
done
echo FLYWAY_SCAN_DONE
set -e

if [ -z "${NACOS_ADDR:-}" ]; then
  echo "ERR missing NACOS_ADDR"
  exit 3
fi
if [ -z "${NACOS_NAMESPACE:-}" ]; then
  echo "ERR missing NACOS_NAMESPACE"
  exit 3
fi
NACOS_HOST=$(echo "$NACOS_ADDR" | cut -d: -f1)
NACOS_PORT=$(echo "$NACOS_ADDR" | cut -d: -f2)
[ -n "${NACOS_PORT}" ] || NACOS_PORT=8848
export NACOS_HOST NACOS_PORT NACOS_NAMESPACE NACOS_USERNAME NACOS_PASSWORD
echo "NACOS_HOST_SET=1 PORT_SET=$([ -n "$NACOS_PORT" ] && echo 1 || echo 0) NS_SET=1"

python3 - <<'PY'
import json
import os
import re
import urllib.parse
import urllib.request

host = os.environ["NACOS_HOST"]
port = os.environ["NACOS_PORT"]
ns = os.environ["NACOS_NAMESPACE"]
user = os.environ["NACOS_USERNAME"]
pwd = os.environ["NACOS_PASSWORD"]
base = f"http://{host}:{port}"


def post(path, data: dict):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(base + path, data=body, method="POST")
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode()


login = json.loads(post("/nacos/v1/auth/login", {"username": user, "password": pwd}))
token = login.get("accessToken") or (login.get("data") or {}).get("accessToken")
if not token:
    raise SystemExit("LOGIN_FAIL")

q = urllib.parse.urlencode({
    "dataId": "agent-bootstrap.yaml",
    "group": "AGENT_PLATFORM",
    "tenant": ns,
    "accessToken": token,
})
with urllib.request.urlopen(base + "/nacos/v1/cs/configs?" + q, timeout=20) as resp:
    content = resp.read().decode()
if not content or not content.strip():
    raise SystemExit("EMPTY_CONFIG")

if re.search(r"web-tools\s*:", content):
    new = re.sub(
        r"(web-tools\s*:\s*\n(?:[ \t]+.+\n)*?[ \t]+enabled\s*:\s*)(false|true)",
        r"\1true",
        content,
        count=1,
    )
    if new == content:
        new = re.sub(r"(web-tools\s*:\s*\n)", r"\1      enabled: true\n", content, count=1)
else:
    if re.search(r"(?m)^  runtime:\s*$", content):
        new = re.sub(
            r"(?m)^(  runtime:\s*\n)",
            r"\1    web-tools:\n      enabled: true\n",
            content,
            count=1,
        )
    else:
        new = content.rstrip() + "\n  runtime:\n    web-tools:\n      enabled: true\n"

changed = new != content
pub = post("/nacos/v1/cs/configs", {
    "dataId": "agent-bootstrap.yaml",
    "group": "AGENT_PLATFORM",
    "tenant": ns,
    "type": "yaml",
    "content": new,
    "accessToken": token,
})
m = re.search(r"web-tools\s*:\s*\n((?:[ \t]+.+\n)+)", new)
seg = m.group(1) if m else ""
has_true = "enabled: true" in seg or re.search(r"enabled\s*:\s*true", seg) is not None
print("NACOS_PUB=" + str(pub).strip())
print("NACOS_CHANGED=" + ("1" if changed else "0"))
print("NACOS_WEB_TOOLS_ENABLED=" + ("1" if has_true else "0"))
PY
