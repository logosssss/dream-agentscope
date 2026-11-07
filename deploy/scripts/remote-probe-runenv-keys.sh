#!/usr/bin/env bash
# 只打印 run.env 的键名与结构，不打印值。
set -euo pipefail
echo "=== recent boot markers ==="
grep -nE 'Started |nacos |milvus|NacosStartup|Missing hostname|APPLICATION FAILED|datasource.url' \
  /opt/dream-agentscope/logs/boot.out | tail -n 80 || true
echo "=== run.env key shapes ==="
python3 - <<'PY'
from pathlib import Path
raw = Path('/opt/dream-agentscope/run.env').read_bytes()
print('bytes', len(raw), 'nul', raw.count(b'\x00'), 'cr', raw.count(b'\r'))
text = raw.replace(b'\x00', b'').decode('utf-8', 'replace')
for i, line in enumerate(text.splitlines(), 1):
    line = line.rstrip('\r')
    if not line or line.lstrip().startswith('#'):
        continue
    if '=' not in line:
        print(f'L{i}: NO_EQ len={len(line)}')
        continue
    key = line.split('=', 1)[0]
    print(
        f'L{i}: key={key!r} dotted={"." in key} '
        f'export={key.strip().startswith("export")} len_val={len(line.split("=",1)[1])}'
    )
PY
