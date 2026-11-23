#!/usr/bin/env bash
# 现网旁路：安装钉版 github-mcp-server 到 /opt/dream-agentscope/mcp/（不改 run.env / 不打印 Token）。
set -euo pipefail
APP_DIR=/opt/dream-agentscope
MCP_DIR="${APP_DIR}/mcp"
TAG="${GITHUB_MCP_TAG:-v2.2.0}"
ASSET="github-mcp-server_Linux_x86_64.tar.gz"
URL="https://github.com/github/github-mcp-server/releases/download/${TAG}/${ASSET}"

mkdir -p "${MCP_DIR}"
tmp=$(mktemp -d /tmp/github-mcp-XXXXXX)
trap 'rm -rf "${tmp}"' EXIT
curl -fsSL -o "${tmp}/${ASSET}" "${URL}"
tar -xzf "${tmp}/${ASSET}" -C "${tmp}"
test -f "${tmp}/github-mcp-server"
install -m 755 "${tmp}/github-mcp-server" "${MCP_DIR}/github-mcp-server"
printf '%s\n' "${TAG}" > "${MCP_DIR}/VERSION"
# 冒烟：无 Token 时仍应能打印帮助/版本类输出；不把环境密钥打出去。
"${MCP_DIR}/github-mcp-server" --help >/dev/null 2>&1 || true
ls -l "${MCP_DIR}/github-mcp-server"
echo "GITHUB_MCP_INSTALLED=${TAG}"

# 只报 GITHUB_TOKEN 键是否存在，不打印值
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
has_token = False
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
    if key == "GITHUB_TOKEN" and val.strip():
        has_token = True
    exports.append(f"export {key}={shlex.quote(val)}")
dst.write_text("\n".join(exports) + ("\n" if exports else ""), encoding="utf-8")
print("GITHUB_TOKEN_PRESENT=" + ("1" if has_token else "0"))
PY
rm -f "${cleaned}"
