-- 产品对话气泡。只在对话成功后双写 user / assistant，不存 system / tool。
CREATE TABLE chat_message (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id      VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户。空串表示没有租户',
  user_id        VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '用户',
  session_id     VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '会话',
  seq            BIGINT UNSIGNED NOT NULL COMMENT '会话内单调序号',
  role           VARCHAR(16)     NOT NULL COMMENT 'user / assistant',
  content        LONGTEXT        NULL COMMENT '气泡正文',
  run_id         VARCHAR(128)    COLLATE utf8mb4_bin NULL COMMENT '同轮运行标识',
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_seq (tenant_id, user_id, session_id, seq),
  KEY idx_session_created (tenant_id, user_id, session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：会话消息历史';
