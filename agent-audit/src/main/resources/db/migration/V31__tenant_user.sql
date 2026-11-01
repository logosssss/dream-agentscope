-- 租户用户。这次只登记身份，不写角色，对话也不按这张表鉴权。
CREATE TABLE tenant_user (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id    VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '已登记的租户',
  user_id      VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '用户业务标识，与请求头一致',
  display_name VARCHAR(128)    NULL COMMENT '显示名，可以空',
  roles        JSON            NULL COMMENT '这次不写',
  status       VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_user (tenant_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：租户用户';
