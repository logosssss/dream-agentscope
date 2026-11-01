-- 任务调度目录。这次只登记，不计算下次触发，也不消费。
-- 不写 payload，避免任务参数入库。
CREATE TABLE agent_task_schedule (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  schedule_id   VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '调度标识',
  task_type     VARCHAR(64)     NOT NULL COMMENT 'BACKFILL / REPORT / BATCH',
  cron_expr     VARCHAR(128)    NULL COMMENT 'cron 表达式。延迟任务为空',
  delay_seconds INT UNSIGNED    NULL COMMENT '延迟秒数。cron 任务为空',
  payload       JSON            NULL COMMENT '保留列。不写任务参数',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1 启用。这次登记后就是启用',
  next_fire_at  DATETIME(3)     NULL COMMENT '下次触发。这次不写',
  last_fire_at  DATETIME(3)     NULL COMMENT '上次触发。这次不写',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_id (schedule_id),
  KEY idx_enabled_next (enabled, next_fire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：任务调度';
