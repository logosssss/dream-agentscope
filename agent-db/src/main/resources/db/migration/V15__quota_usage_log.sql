-- 每日请求通过后的流水。不回补，也不填运行标识。计数仍在 Redis。
CREATE TABLE quota_usage_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id   VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '租户业务标识，没有时为空串',
  user_id     VARCHAR(128)    NULL COMMENT '用户业务标识',
  quota_type  VARCHAR(64)     NOT NULL COMMENT '这次只写 REQUESTS',
  amount      BIGINT          NOT NULL COMMENT '这次固定为 1，不写负数',
  run_id      VARCHAR(128)    NULL COMMENT '不用。额度检查发生在运行开始之前',
  reason      VARCHAR(128)    NULL COMMENT '入口名，如 chat',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at),
  KEY idx_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：配额消耗流水';
