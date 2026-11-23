#!/usr/bin/env bash
# 由 web-admin-dist-scp-deploy.ps1 上传到 /tmp 后执行。
# 整目录替换 /opt/dream-saas/ops（对应公网 https://www.dream-saas.com/ops/）。
set -euo pipefail

STAGING=/tmp/dream-web-admin-dist-staging
TARGET=/opt/dream-saas/ops
BACKUP_ROOT=/opt/dream-saas/backup

if ! test -f "${STAGING}/index.html"; then
  echo "missing index.html in staging"
  exit 1
fi

mkdir -p "${BACKUP_ROOT}" "${TARGET}"
stamp=$(date +%Y%m%d%H%M%S)
if [ "$(ls -A "${TARGET}" 2>/dev/null | wc -l)" -gt 0 ]; then
  tar -czf "${BACKUP_ROOT}/ops-${stamp}.tgz" -C /opt/dream-saas ops
  echo "backup: ${BACKUP_ROOT}/ops-${stamp}.tgz"
fi

rm -rf "${TARGET:?}/"*
cp -a "${STAGING}/." "${TARGET}/"
chmod -R a+rX "${TARGET}"
test -f "${TARGET}/index.html"
rm -rf "${STAGING}"
echo "web-admin-ops ok"
