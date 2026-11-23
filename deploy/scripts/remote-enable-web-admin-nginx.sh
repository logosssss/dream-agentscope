#!/usr/bin/env bash
# 挂上 /ops/；确保 AGENTSCOPE_ADMIN_TOKEN；为已有 ^~ /api/admin/ 注入令牌头。
# 令牌正文不 echo。
set -euo pipefail

APP_DIR=/opt/dream-agentscope
RUN_ENV="${APP_DIR}/run.env"
SNIPPET=/etc/nginx/snippets/dream-agentscope-ops.conf
TOKEN_FILE=/etc/nginx/snippets/dream-agentscope-ops.token
MARK="# dream-agentscope-ops-include"
CONF_CANDIDATES=(/etc/nginx/conf.d/*.conf /etc/nginx/sites-enabled/*)

if [ ! -f "${RUN_ENV}" ]; then
  echo "missing ${RUN_ENV}"
  exit 1
fi

mkdir -p /etc/nginx/snippets /opt/dream-saas/ops

ADMIN_TOKEN=$(python3 - "${RUN_ENV}" <<'PY'
import secrets
import sys
from pathlib import Path

path = Path(sys.argv[1])
raw = path.read_bytes()
if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
    text = raw.decode("utf-16")
elif b"\x00" in raw:
    text = "\n".join(chunk.decode("utf-8", "replace") for chunk in raw.split(b"\x00"))
else:
    text = raw.decode("utf-8", "replace")

entries = []
token = ""
for line in text.splitlines():
    line = line.strip().lstrip("\ufeff")
    if not line or line.startswith("#") or "=" not in line:
        continue
    if line.startswith("export "):
        line = line[7:].lstrip()
    key, val = line.split("=", 1)
    key = key.strip()
    if not key.isidentifier():
        continue
    if key == "AGENTSCOPE_ADMIN_TOKEN":
        token = val
        continue
    entries.append((key, val))

created = False
if not token.strip():
    token = secrets.token_urlsafe(32)
    created = True
entries.append(("AGENTSCOPE_ADMIN_TOKEN", token))
path.write_bytes(b"\x00".join(f"{k}={v}".encode("utf-8") for k, v in entries))
print("TOKEN_CREATED" if created else "TOKEN_EXISTS")
print(token)
PY
)

TOKEN_STATUS=$(printf '%s\n' "${ADMIN_TOKEN}" | sed -n '1p')
ADMIN_TOKEN=$(printf '%s\n' "${ADMIN_TOKEN}" | sed -n '2p')
echo "admin_token_status=${TOKEN_STATUS}"
test -n "${ADMIN_TOKEN}"

umask 077
printf '%s' "${ADMIN_TOKEN}" > "${TOKEN_FILE}"
chmod 600 "${TOKEN_FILE}"

if [ "${TOKEN_STATUS}" = "TOKEN_CREATED" ]; then
  echo "restarting admin to pick up new AGENTSCOPE_ADMIN_TOKEN"
  bash /tmp/remote-restart-agentscope.sh admin
fi

python3 - <<PY
from pathlib import Path
token = Path("${TOKEN_FILE}").read_text().strip()
esc = token.replace("\\\\", "\\\\\\\\").replace('"', '\\\\"')
Path("${SNIPPET}").write_text(
    f'''# 由 remote-enable-web-admin-nginx.sh 生成。勿提交 git。
# ^~ + alias：避开站点全局 ~* \\.(js|css)$，并正确落到 /opt/dream-saas/ops
set $admin_token "{esc}";

location = /ops {{
    return 302 /ops/;
}}

location ^~ /ops/assets/ {{
    alias /opt/dream-saas/ops/assets/;
    access_log off;
    expires 7d;
}}

location ^~ /ops/ {{
    alias /opt/dream-saas/ops/;
    index index.html;
}}
''',
    encoding="utf-8",
)
Path("${SNIPPET}").chmod(0o600)
print("snippet_written")
PY
ADMIN_TOKEN=""
unset ADMIN_TOKEN

CONF=""
for c in ${CONF_CANDIDATES[@]+"${CONF_CANDIDATES[@]}"}; do
  [ -f "$c" ] || continue
  if grep -qE 'server_name[[:space:]].*www\.dream-saas\.com' "$c" && grep -qE 'listen[[:space:]].*443' "$c"; then
    CONF="$c"
    break
  fi
done
test -n "${CONF}"
echo "nginx_conf=${CONF}"

if ! grep -qF "${MARK}" "${CONF}"; then
  python3 - <<PY
from pathlib import Path
conf = Path("${CONF}")
text = conf.read_text(encoding="utf-8")
include_line = "    ${MARK}\n    include ${SNIPPET};\n"
lines = text.splitlines(keepends=True)
out, in_target, brace, inserted, i = [], False, 0, False, 0
while i < len(lines):
    line = lines[i]
    if (not in_target) and "server" in line and "{" in line:
        window = "".join(lines[i:i+40])
        if "www.dream-saas.com" in window and "443" in window:
            in_target = True
            brace = line.count("{") - line.count("}")
            out.append(line)
            i += 1
            continue
    if in_target and not inserted:
        brace += line.count("{") - line.count("}")
        if brace == 0 and line.strip() == "}":
            out.append(include_line)
            out.append(line)
            inserted = True
            in_target = False
            i += 1
            continue
    out.append(line)
    i += 1
if not inserted:
    raise SystemExit("failed to insert include")
Path(str(conf) + ".bak-ops").write_text(text, encoding="utf-8")
conf.write_text("".join(out), encoding="utf-8")
print("inserted include")
PY
fi

# 现网已有 location ^~ /api/admin/ → 8100；只补令牌头，不重复声明 location
python3 - <<'PY'
from pathlib import Path
conf = Path("/etc/nginx/conf.d/dream-saas-ssl.conf")
if not conf.exists():
    # fallback: 上面找到的 CONF
    raise SystemExit("expected /etc/nginx/conf.d/dream-saas-ssl.conf")
text = conf.read_text(encoding="utf-8")
needle = "location ^~ /api/admin/"
if needle not in text:
    raise SystemExit("missing location ^~ /api/admin/")
start = text.index(needle)
i = text.index("{", start)
brace, j = 0, i
while j < len(text):
    if text[j] == "{":
        brace += 1
    elif text[j] == "}":
        brace -= 1
        if brace == 0:
            j += 1
            break
    j += 1
block = text[start:j]
if "X-Caller-Token" in block and "$admin_token" in block:
    print("api_admin_token_header_already_ok")
else:
    lines = block.splitlines(keepends=True)
    out, done = [], False
    for line in lines:
        out.append(line)
        if (not done) and ("proxy_pass" in line) and ("8100" in line):
            out.append("        proxy_set_header X-Caller-Token $admin_token;\n")
            done = True
    if not done:
        out.insert(-1, "        proxy_set_header X-Caller-Token $admin_token;\n")
    Path(str(conf) + ".bak-ops-token").write_text(text, encoding="utf-8")
    conf.write_text(text[:start] + "".join(out) + text[j:], encoding="utf-8")
    print("patched_api_admin_header")
PY

nginx -t
systemctl reload nginx
echo "OPS_NGINX_OK"
curl -sI --http1.1 -m 5 "https://127.0.0.1/ops/" -H "Host: www.dream-saas.com" -k | head -n 5 || true
code=$(curl -s --http1.1 -m 5 -o /dev/null -w "%{http_code}" "https://127.0.0.1/api/admin/tenants" -H "Host: www.dream-saas.com" -k || echo ERR)
echo "admin_api_http=${code}"
