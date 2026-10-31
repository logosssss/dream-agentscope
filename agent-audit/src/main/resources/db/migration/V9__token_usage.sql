-- 一次模型调用的 token 用量。金额在没有价格表时记 0，不在这里计价。
CREATE TABLE token_usage (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id        VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '这次运行的标识',
  tenant_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户。空串表示没有租户',
  user_id       VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '用户',
  session_id    VARCHAR(128)    COLLATE utf8mb4_bin NULL COMMENT '会话',
  model         VARCHAR(128)    NOT NULL COMMENT '模型坐标',
  input_tokens  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  output_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
  total_tokens  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  cost_amount   DECIMAL(14,6)   NOT NULL DEFAULT 0 COMMENT '没有价格表时记 0',
  currency      CHAR(3)         NOT NULL DEFAULT 'CNY',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_run (run_id),
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='审计：Token 用量';
