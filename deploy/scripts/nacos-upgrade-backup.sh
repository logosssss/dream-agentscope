#!/usr/bin/env bash
set -eu
TS=$(date +%Y%m%d-%H%M%S)
BK=/opt/nacos/backup-${TS}
mkdir -p "${BK}"
PROP=/opt/nacos/nacos/conf/application.properties
DBUSER=$(grep -E '^db.user=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
DBPASS=$(grep -E '^db.password=' "${PROP}" | head -1 | cut -d= -f2- | tr -d '\r')
echo "BK=${BK}"
echo "DBUSER_LEN=${#DBUSER}"
docker exec mysql8 mysqldump -u"${DBUSER}" -p"${DBPASS}" --single-transaction --routines --triggers nacos_config > "${BK}/nacos_config.sql"
wc -c "${BK}/nacos_config.sql"
cp -a /opt/nacos/nacos/conf "${BK}/conf"
docker exec mysql8 mysql -u"${DBUSER}" -p"${DBPASS}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nacos_config'"
ls -la "${BK}"
echo "BACKUP_OK ${BK}"