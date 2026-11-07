#!/usr/bin/env bash
# 只打印 run.env 结构元数据，不输出任何变量值。
set -euo pipefail
f=/opt/dream-agentscope/run.env
echo "=== meta ==="
ls -la "$f"
file "$f"
echo "nul_count=$(grep -aob $'\0' "$f" | wc -l || true)"
echo "cr_count=$(grep -aob $'\r' "$f" | wc -l || true)"
python3 - <<'PY'
from pathlib import Path
raw = Path('/opt/dream-agentscope/run.env').read_bytes()
print('bytes', len(raw), 'nul', raw.count(b'\x00'), 'cr', raw.count(b'\r'))
text = raw.decode('utf-8', 'replace')
for i, line in enumerate(text.splitlines(), 1):
    has_eq = '=' in line
    has_paren = '(' in line or ')' in line
    key = line.split('=', 1)[0] if has_eq else ''
    print(f'L{i}: len={len(line)} eq={has_eq} paren={has_paren} key={key!r}')
PY
