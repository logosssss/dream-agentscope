#!/usr/bin/env bash
set -eu
PROP=/opt/nacos/nacos/conf/application.properties
DBUSER=$(grep -E '^db.user=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
DBPASS=$(grep -E '^db.password=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
echo "=== current tables ==="
docker exec mysql8 mysql -u"${DBUSER}" -p"${DBPASS}" -N -e "SHOW TABLES FROM nacos_config"
echo "=== 3.3 create tables ==="
grep -iE '^CREATE TABLE' /opt/nacos/nacos-3.3.0-extract/nacos/conf/mysql-schema.sql
echo "=== 3.1 bundled create tables ==="
grep -iE '^CREATE TABLE' /opt/nacos/nacos/conf/mysql-schema.sql
