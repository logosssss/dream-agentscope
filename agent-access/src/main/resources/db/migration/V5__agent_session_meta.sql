-- 会话列表。只记标题和最近活动，不存对话正文。用户进程的 Flyway 负责建表。
CREATE TABLE agent_session_meta (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '用户',
  session_id     VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '会话',
  tenant_id      VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户。空串表示没有租户',
  title          VARCHAR(256)    NULL COMMENT '列表上显示的短标题，不存完整输入',
  channel        VARCHAR(16)     NOT NULL DEFAULT 'WEB' COMMENT 'WEB / IM / API',
  status         VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED',
  last_run_id    VARCHAR(64)     NULL COMMENT '最近一次运行。当前写入不填',
  last_active_at DATETIME(3)     NOT NULL COMMENT '最近一轮对话的时间',
  message_count  INT             NOT NULL DEFAULT 0 COMMENT '已记录的对话轮数',
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                 ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_session (tenant_id, user_id, session_id),
  KEY idx_user_active (user_id, last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：会话列表元数据';
