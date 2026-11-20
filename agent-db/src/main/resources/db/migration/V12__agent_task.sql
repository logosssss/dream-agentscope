-- 同步任务的状态。不存 payload，避免用户内容入库。调度和队列消费还没做。
CREATE TABLE agent_task (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id       VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '任务标识',
  run_id        VARCHAR(128)    COLLATE utf8mb4_bin NULL COMMENT '关联的运行。同步任务先空着',
  tenant_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户。空串表示没有租户',
  user_id       VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL,
  session_id    VARCHAR(128)    COLLATE utf8mb4_bin NULL,
  task_type     VARCHAR(64)     NOT NULL,
  status        VARCHAR(32)     NOT NULL COMMENT 'RUNNING / SUCCEEDED / FAILED',
  payload       JSON            NULL COMMENT '保留列。不写任务参数',
  result_ref    VARCHAR(512)    NULL,
  retry_count   INT UNSIGNED    NOT NULL DEFAULT 0,
  max_retries   INT UNSIGNED    NOT NULL DEFAULT 3,
  next_run_at   DATETIME(3)     NULL,
  error_message VARCHAR(64)     NULL COMMENT '失败时的异常类型，不存异常原文',
  started_at    DATETIME(3)     NULL,
  finished_at   DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_id (task_id),
  KEY idx_status_next (status, next_run_at),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：Agent 任务';
