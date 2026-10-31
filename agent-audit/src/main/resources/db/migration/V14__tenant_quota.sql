-- 租户每日请求上限。已用量不写这张表，计数仍在 Redis。没有启用行时用进程配置。
CREATE TABLE tenant_quota (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id   VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '租户业务标识',
  quota_type  VARCHAR(64)     NOT NULL COMMENT '这次只写 REQUESTS',
  period      VARCHAR(32)     NOT NULL DEFAULT 'DAILY' COMMENT '这次只写 DAILY',
  limit_value BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上限，0 表示不限额',
  used_value  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '不用。当日计数在 Redis',
  reset_at    DATETIME(3)     NULL COMMENT '不用。按 UTC 日切在键上',
  enabled     TINYINT(1)      NOT NULL DEFAULT 1,
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_quota (tenant_id, quota_type, period),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：租户配额';
