#!/usr/bin/env bash
set -eu
cd /opt/nacos
if [ ! -f nacos-server-3.3.0.tar.gz ]; then
  curl -fL --retry 3 -o nacos-server-3.3.0.tar.gz \
    https://github.com/alibaba/nacos/releases/download/3.3.0/nacos-server-3.3.0.tar.gz
fi
ls -lh nacos-server-3.3.0.tar.gz
rm -rf /opt/nacos/nacos-3.3.0-extract
mkdir -p /opt/nacos/nacos-3.3.0-extract
tar -xzf nacos-server-3.3.0.tar.gz -C /opt/nacos/nacos-3.3.0-extract
ls /opt/nacos/nacos-3.3.0-extract
find /opt/nacos/nacos-3.3.0-extract -iname '*mysql*' -o -iname '*schema*' -o -iname '*upgrade*' | head -50
PROP=/opt/nacos/nacos/conf/application.properties
DBUSER=$(grep -E '^db.user=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
DBPASS=$(grep -E '^db.password=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
echo "=== current tables ==="
docker exec mysql8 mysql -u"${DBUSER}" -p"${DBPASS}" -N -e "SHOW TABLES FROM nacos_config"