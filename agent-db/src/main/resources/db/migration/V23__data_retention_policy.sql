-- 数据保留策略。这次只登记，不跑清理任务，也不改已有数据。
CREATE TABLE data_retention_policy (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  data_type     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT 'SESSION / MESSAGE / EVENT / ATTACHMENT',
  scope         VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '*' COMMENT '租户或 *',
  retain_days   INT UNSIGNED    NOT NULL COMMENT '保留天数，从 1 起',
  action        VARCHAR(32)     NOT NULL COMMENT 'ARCHIVE / DELETE / ANONYMIZE',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_scope (data_type, scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：数据保留策略';
