-- 模型配置目录。这次只登记，不接到对话，也不解析 params。
CREATE TABLE model_config (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_id     VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '配置标识',
  model         VARCHAR(128)    NOT NULL COMMENT '模型名',
  provider      VARCHAR(64)     NOT NULL COMMENT '供应方',
  version       VARCHAR(32)     NOT NULL COMMENT '这次固定 1.0.0',
  tenant_scope  VARCHAR(64)     NOT NULL DEFAULT '*' COMMENT '* 表示全局',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_version (config_id, version),
  KEY idx_tenant_enabled (tenant_scope, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='执行内核配置：模型配置';
