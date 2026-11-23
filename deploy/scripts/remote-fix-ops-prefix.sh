#!/usr/bin/env bash
# /ops/ 必须用 ^~，否则会被站点全局 ~* \.(js|css)$ 正则抢走导致 assets 404。
set -euo pipefail

TOKEN_FILE=/etc/nginx/snippets/dream-agentscope-ops.token
SNIPPET=/etc/nginx/snippets/dream-agentscope-ops.conf
test -s "${TOKEN_FILE}"

python3 - <<'PY'
from pathlib import Path

token = Path("/etc/nginx/snippets/dream-agentscope-ops.token").read_text().strip()
esc = token.replace("\\", "\\\\").replace('"', '\\"')
text = f"""# dream-agentscope ops：^~ 避免被全局 js/css 正则抢走
set $admin_token "{esc}";

location = /ops {{
    return 302 /ops/;
}}

location ^~ /ops/ {{
    root /opt/dream-saas;
    try_files $uri $uri/ /ops/index.html;
}}
"""
path = Path("/etc/nginx/snippets/dream-agentscope-ops.conf")
path.write_text(text, encoding="utf-8")
path.chmod(0o600)
print("snippet_ok")
PY

# 同步本地模板脚本里的约定（enable 脚本下次也用 ^~）
nginx -t
systemctl reload nginx

echo "=== asset probes ==="
curl -sI --http1.1 -m 5 "https://127.0.0.1/ops/assets/index-D1VRJPPK.js" -H "Host: www.dream-saas.com" -k | head -n 10
curl -sI --http1.1 -m 5 "https://127.0.0.1/ops/assets/index-C2NyygAR.css" -H "Host: www.dream-saas.com" -k | head -n 8
curl -sI --http1.1 -m 5 "https://www.dream-saas.com/ops/assets/index-D1VRJPPK.js" | head -n 8
curl -sI --http1.1 -m 5 "https://www.dream-saas.com/ops/" | head -n 8
