-- 租户。MySQL 打开时，对话只接受 ACTIVE。用户账号不在这张表。
CREATE TABLE tenant (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id  VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '租户业务标识',
  name       VARCHAR(128)    NOT NULL COMMENT '租户名称',
  plan       VARCHAR(64)     NOT NULL DEFAULT 'standard' COMMENT '套餐，空则 standard',
  status     VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SUSPENDED / CLOSED',
  created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_id (tenant_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：租户';
