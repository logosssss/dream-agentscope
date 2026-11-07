#!/usr/bin/env bash
# 由 web-dist-scp-deploy.ps1 上传到 /tmp 后执行。
# 整目录替换 /opt/dream-saas/web-dist：先打时间戳备份，再同步 staging。
set -euo pipefail

STAGING=/tmp/dream-web-dist-staging
TARGET=/opt/dream-saas/web-dist
BACKUP_ROOT=/opt/dream-saas/backup

if ! test -f "${STAGING}/index.html"; then
  echo "missing index.html in staging"
  exit 1
fi

mkdir -p "${BACKUP_ROOT}" "${TARGET}"
stamp=$(date +%Y%m%d%H%M%S)
# 现网仍有内容时再备份，避免空目录 tar 失败噪音。
if [ "$(ls -A "${TARGET}" 2>/dev/null | wc -l)" -gt 0 ]; then
  tar -czf "${BACKUP_ROOT}/web-dist-${stamp}.tgz" -C /opt/dream-saas web-dist
  echo "backup: ${BACKUP_ROOT}/web-dist-${stamp}.tgz"
fi

rm -rf "${TARGET:?}/"*
cp -a "${STAGING}/." "${TARGET}/"
chmod -R a+rX "${TARGET}"
test -f "${TARGET}/index.html"
echo "web-dist ok"
