#!/usr/bin/env bash
# 修复 ops snippet（去掉重复 /api/admin/），并为已有 ^~ /api/admin/ 注入 Admin Token。
set -euo pipefail

TOKEN_FILE=/etc/nginx/snippets/dream-agentscope-ops.token
SNIPPET=/etc/nginx/snippets/dream-agentscope-ops.conf
CONF=/etc/nginx/conf.d/dream-saas-ssl.conf

test -s "${TOKEN_FILE}"
test -f "${CONF}"

umask 077
cat > "${SNIPPET}" <<EOF
# dream-agentscope ops 静态 + admin token 变量
set \$admin_token "$(cat "${TOKEN_FILE}")";

location = /ops {
    return 302 /ops/;
}

location ^~ /ops/assets/ {
    alias /opt/dream-saas/ops/assets/;
    access_log off;
    expires 7d;
}

location ^~ /ops/ {
    alias /opt/dream-saas/ops/;
    index index.html;
}
EOF
chmod 600 "${SNIPPET}"

python3 - <<'PY'
from pathlib import Path

conf = Path("/etc/nginx/conf.d/dream-saas-ssl.conf")
text = conf.read_text(encoding="utf-8")
needle = "location ^~ /api/admin/"
if needle not in text:
    raise SystemExit("missing location ^~ /api/admin/")
start = text.index(needle)
i = text.index("{", start)
brace = 0
j = i
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
    out = []
    done = False
    for line in lines:
        out.append(line)
        if (not done) and ("proxy_pass" in line) and ("8100" in line):
            out.append("        proxy_set_header X-Caller-Token $admin_token;\n")
            done = True
    if not done:
        out.insert(-1, "        proxy_set_header X-Caller-Token $admin_token;\n")
    new_block = "".join(out)
    Path(str(conf) + ".bak-ops-token").write_text(text, encoding="utf-8")
    conf.write_text(text[:start] + new_block + text[j:], encoding="utf-8")
    print("patched_api_admin_header")

# include 必须仍在 ssl server 内
if "dream-agentscope-ops.conf" not in conf.read_text(encoding="utf-8"):
    raise SystemExit("ops snippet include missing from ssl conf")
print("include_ok")
PY

nginx -t
systemctl reload nginx
echo OPS_FIX_OK

curl -sI -m 5 "https://127.0.0.1/ops/" -H "Host: www.dream-saas.com" -k | head -n 10
code=$(curl -s -m 5 -o /tmp/ops-api.out -w "%{http_code}" "https://127.0.0.1/api/admin/tenants" -H "Host: www.dream-saas.com" -k || echo ERR)
echo "admin_api_http=${code}"
python3 - <<'PY'
from pathlib import Path
t = Path("/tmp/ops-api.out").read_text(encoding="utf-8", errors="ignore")
print("body_len", len(t))
print("unauthorized", "UNAUTHORIZED" in t)
print("ok_true", '"ok":true' in t.replace(" ", ""))
PY
curl -s -m 3 -o /dev/null -w "admin_live=%{http_code}\n" http://127.0.0.1:8100/health/live
