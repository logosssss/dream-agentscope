#!/usr/bin/env bash
set -euo pipefail

echo "=== snippet before ==="
sed -E 's/(token).*/\1=***/I' /etc/nginx/snippets/dream-agentscope-ops.conf
echo "=== file ==="
ls -la /opt/dream-saas/ops/assets/index-D1VRJPPK.js
echo "=== nginx locations involving js/css/ops ==="
nginx -T 2>/dev/null | grep -nE 'location .*(ops|js|css|assets)' | head -60

python3 - <<'PY'
from pathlib import Path

token = Path("/etc/nginx/snippets/dream-agentscope-ops.token").read_text().strip()
esc = token.replace("\\", "\\\\").replace('"', '\\"')
# 用 alias 明确映射；assets 单独 ^~，避免 try_files 干扰
text = f"""# dream-agentscope ops（alias + ^~）
set $admin_token "{esc}";

location = /ops {{
    return 302 /ops/;
}}

location ^~ /ops/assets/ {{
    alias /opt/dream-saas/ops/assets/;
    access_log off;
    expires 7d;
    add_header Cache-Control "public";
}}

location ^~ /ops/ {{
    alias /opt/dream-saas/ops/;
    index index.html;
    try_files $uri $uri/ /ops/index.html;
}}
"""
path = Path("/etc/nginx/snippets/dream-agentscope-ops.conf")
path.write_text(text, encoding="utf-8")
path.chmod(0o600)
print("snippet_rewritten_alias")
PY

if ! nginx -t; then
  echo "alias+try_files may be invalid; fallback without try_files named quirk"
  python3 - <<'PY'
from pathlib import Path
token = Path("/etc/nginx/snippets/dream-agentscope-ops.token").read_text().strip()
esc = token.replace("\\", "\\\\").replace('"', '\\"')
text = f"""# dream-agentscope ops（alias + ^~，SPA fallback 用 if）
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
"""
Path("/etc/nginx/snippets/dream-agentscope-ops.conf").write_text(text, encoding="utf-8")
print("fallback_no_try_files")
PY
  nginx -t
fi

systemctl reload nginx

echo "=== probes ==="
for u in \
  "https://127.0.0.1/ops/" \
  "https://127.0.0.1/ops/index.html" \
  "https://127.0.0.1/ops/assets/index-D1VRJPPK.js" \
  "https://127.0.0.1/ops/assets/index-C2NyygAR.css" \
  "https://www.dream-saas.com/ops/assets/index-D1VRJPPK.js"
 do
  code=$(curl -sI --http1.1 -m 5 "$u" -H "Host: www.dream-saas.com" -k 2>/dev/null | head -n 1)
  # public URL already has host
  if [[ "$u" == https://www* ]]; then
    code=$(curl -sI --http1.1 -m 5 "$u" | head -n 1)
  fi
  echo "$code  $u"
 done
