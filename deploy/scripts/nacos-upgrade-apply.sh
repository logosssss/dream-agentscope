#!/usr/bin/env bash
# 现网 Nacos 3.1.1 -> 3.3.0：补 AI 表 + permissions 升级 + 换 jar + 重启。
set -eu
PROP=/opt/nacos/nacos/conf/application.properties
DBUSER=$(grep -E '^db.user=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
DBPASS=$(grep -E '^db.password=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
NEW=/opt/nacos/nacos-3.3.0-extract/nacos
OLD=/opt/nacos/nacos
BK=/opt/nacos/backup-20261119-171024
SCHEMA="${NEW}/conf/mysql-schema.sql"
UPG="${NEW}/conf/mysql-upgrade-visibility-permission-resource.sql"
SQLDIR=/tmp/nacos-330-sql
mkdir -p "${SQLDIR}"

mysql_q() {
  docker exec mysql8 mysql -u"${DBUSER}" -p"${DBPASS}" "$@"
}

mysql_file() {
  docker exec -i mysql8 mysql -u"${DBUSER}" -p"${DBPASS}" nacos_config < "$1"
}

echo "=== create missing AI tables ==="
python3 - <<'PY'
from pathlib import Path
schema = Path("/opt/nacos/nacos-3.3.0-extract/nacos/conf/mysql-schema.sql").read_text(encoding="utf-8")
need = [
    "pipeline_execution",
    "ai_resource",
    "ai_resource_version",
    "ai_resource_search_document",
    "ai_resource_search_chunk",
    "ai_resource_task",
]
out = Path("/tmp/nacos-330-sql/missing-ai-tables.sql")
chunks = []
for name in need:
    marker = f"CREATE TABLE `{name}`"
    i = schema.find(marker)
    if i < 0:
        raise SystemExit(f"missing create for {name}")
    j = schema.find("CREATE TABLE", i + 1)
    chunk = schema[i:] if j < 0 else schema[i:j]
    # trim trailing comments between tables
    end = chunk.rfind(";")
    chunks.append(chunk[: end + 1].strip() + "\n")
out.write_text("\n".join(chunks), encoding="utf-8")
print(f"wrote {out} bytes={out.stat().st_size}")
PY

mysql_file "${SQLDIR}/missing-ai-tables.sql"

echo "=== permissions upgrade ==="
mysql_file "${UPG}"

echo "=== tables after ==="
mysql_q -N -e "SHOW TABLES FROM nacos_config"

echo "=== stop nacos ==="
bash "${OLD}/bin/shutdown.sh" || true
for i in $(seq 1 15); do
  if pgrep -f '/opt/nacos/nacos/target/nacos-server.jar' >/dev/null; then
    sleep 2
  else
    break
  fi
done
if pgrep -f '/opt/nacos/nacos/target/nacos-server.jar' >/dev/null; then
  pkill -f '/opt/nacos/nacos/target/nacos-server.jar' || true
  sleep 3
fi

echo "=== swap jar keep application.properties ==="
mkdir -p "${BK}"
cp -a "${OLD}/target/nacos-server.jar" "${BK}/nacos-server.jar.3.1.1"
cp -a "${NEW}/target/nacos-server.jar" "${OLD}/target/nacos-server.jar"
for f in mysql-schema.sql mysql-upgrade-visibility-permission-resource.sql nacos-logback.xml; do
  cp -a "${NEW}/conf/${f}" "${OLD}/conf/${f}"
done
unzip -p "${OLD}/target/nacos-server.jar" META-INF/MANIFEST.MF | grep -E 'Implementation-Version|Specification-Title' || true

# 3.3 把 auth/datasource 等实现拆到 plugins；只换 jar 不拷 plugins 会报
# Active critical plugin type 'auth' has no discovered implementation。
echo "=== sync plugins from 3.3.0 package ==="
mkdir -p "${OLD}/plugins"
cp -a "${NEW}/plugins/." "${OLD}/plugins/"

# 3.3 首选 nacos.plugin.auth.nacos.*；旧键仍写在 conf 时启动会缺 token.secret.key。
echo "=== migrate auth plugin keys (no secret printed) ==="
python3 - <<'PY'
from pathlib import Path
p = Path("/opt/nacos/nacos/conf/application.properties")
text = p.read_text(encoding="utf-8")
lines = text.splitlines(keepends=True)
legacy = None
for line in lines:
    s = line.strip()
    if s.startswith("#") or "=" not in s:
        continue
    k, v = s.split("=", 1)
    if k.strip() == "nacos.core.auth.plugin.nacos.token.secret.key":
        legacy = v.strip()
if not legacy:
    raise SystemExit("legacy nacos.core.auth.plugin.nacos.token.secret.key missing")
replacements = {
    "nacos.plugin.auth.type": "nacos",
    "nacos.plugin.auth.nacos.token.secret.key": legacy,
    "nacos.plugin.auth.nacos.token.expire.seconds": "18000",
    "nacos.plugin.auth.nacos.token.cache.enable": "false",
}
out = []
seen = set()
for line in lines:
    s = line.strip()
    if s and not s.startswith("#") and "=" in s:
        k = s.split("=", 1)[0].strip()
        if k in replacements:
            out.append(f"{k}={replacements[k]}\n")
            seen.add(k)
            continue
    out.append(line)
if out and not out[-1].endswith("\n"):
    out.append("\n")
for k, v in replacements.items():
    if k not in seen:
        out.append(f"\n# migrated for Nacos 3.3 plugin auth\n{k}={v}\n")
p.write_text("".join(out), encoding="utf-8")
print("migrated_auth_keys=", ",".join(sorted(replacements)))
print("secret_len=", len(legacy))
PY

echo "=== start nacos standalone ==="
bash "${OLD}/bin/startup.sh" -m standalone
for i in $(seq 1 45); do
  if ss -lntp | grep -q ':8848'; then
    echo "PORT_8848_UP"
    break
  fi
  sleep 2
done
ss -lntp | grep -E ':8080|:8848' || true
pgrep -af 'nacos-server.jar' | head -3 || true
# 3.3 console readiness 在 8080；8848 用 actuator
curl -s -o /dev/null -w "actuator_8848=%{http_code}\n" http://127.0.0.1:8848/nacos/actuator/health || true
curl -s -o /dev/null -w "console_ready=%{http_code}\n" http://127.0.0.1:8080/v3/console/health/readiness || true
echo "SWAP_START_DONE"
