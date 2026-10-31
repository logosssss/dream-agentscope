-- 安全干预。只记档位和来源，不存用户原文。
CREATE TABLE safety_intervention (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id  VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户，可空串',
  user_id    VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '被干预的用户',
  session_id VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '会话，可空串',
  risk_level VARCHAR(16)     NOT NULL COMMENT 'ELEVATED / CRITICAL',
  source     VARCHAR(16)     NOT NULL COMMENT 'KEYWORD / SEMANTIC / JUDGE_FAILED',
  summary    VARCHAR(128)    NOT NULL COMMENT '短摘要。不含用户原文',
  created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at),
  KEY idx_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：安全干预';
