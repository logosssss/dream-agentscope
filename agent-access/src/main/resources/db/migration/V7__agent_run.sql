-- 一次对话的运行记录。只存短摘要，不存输入或输出全文。用户进程的 Flyway 负责建表。
CREATE TABLE agent_run (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id         VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '这次运行的标识',
  tenant_id      VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户。空串表示没有租户',
  user_id        VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '用户',
  session_id     VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '会话',
  trigger_type   VARCHAR(32)     NOT NULL DEFAULT 'USER' COMMENT 'USER / API',
  status         VARCHAR(32)     NOT NULL COMMENT 'SUCCEEDED / FAILED / CANCELLED',
  model          VARCHAR(128)    NULL COMMENT '这次使用的模型坐标',
  input_summary  VARCHAR(256)    NULL COMMENT '输入摘要，不存全文',
  output_summary VARCHAR(256)    NULL COMMENT '输出摘要，不存全文',
  error_code     VARCHAR(64)     NULL COMMENT '失败时的异常类型，不存异常原文',
  error_message  VARCHAR(1024)   NULL COMMENT '保留列。当前不写，避免把用户输入带进去',
  started_at     DATETIME(3)     NULL,
  finished_at    DATETIME(3)     NULL,
  duration_ms    BIGINT UNSIGNED NULL,
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_id (run_id),
  KEY idx_user_session (user_id, session_id, created_at),
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：Agent 运行记录';
