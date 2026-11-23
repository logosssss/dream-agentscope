#!/usr/bin/env bash
set -euo pipefail

echo "=== api admin via nginx ==="
curl -s --http1.1 -m 5 -o /tmp/ops-api.out -w "http=%{http_code}\n" \
  "https://127.0.0.1/api/admin/tenants" -H "Host: www.dream-saas.com" -k || true
python3 - <<'PY'
from pathlib import Path
t = Path("/tmp/ops-api.out").read_text(encoding="utf-8", errors="ignore")
print("unauth", "UNAUTHORIZED" in t)
print("ok_true", '"ok":true' in t.replace(" ", ""))
print("len", len(t))
print("head", t[:120].replace("\n", " "))
PY

echo "=== ops page ==="
curl -sI --http1.1 -m 5 "https://127.0.0.1/ops/" -H "Host: www.dream-saas.com" -k | head -n 15 || true
curl -s --http1.1 -m 5 "https://127.0.0.1/ops/index.html" -H "Host: www.dream-saas.com" -k | head -c 300 || true
echo

echo "=== admin location block ==="
awk '/location \^~ \/api\/admin\//,/^    }/' /etc/nginx/conf.d/dream-saas-ssl.conf | sed -E 's/(Token|token).*/\1 ***/I'

echo "=== public ==="
curl -sI --http1.1 -m 10 "https://www.dream-saas.com/ops/" | head -n 12 || true
curl -s --http1.1 -m 10 -o /tmp/pub.out -w "pub_api=%{http_code}\n" "https://www.dream-saas.com/api/admin/tenants" || true
python3 - <<'PY'
from pathlib import Path
t = Path("/tmp/pub.out").read_text(encoding="utf-8", errors="ignore")
print("pub_unauth", "UNAUTHORIZED" in t)
print("pub_ok_true", '"ok":true' in t.replace(" ", ""))
print("pub_len", len(t))
PY
