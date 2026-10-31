-- 一次运行里的步骤。只记类型和工具名，不存参数、正文或异常原文。
CREATE TABLE agent_run_step (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id         VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '对应 agent_run.run_id',
  step_no        INT UNSIGNED    NOT NULL COMMENT '同一次运行内从 1 递增',
  step_type      VARCHAR(32)     NOT NULL COMMENT 'REASONING / TOOL / APPROVAL',
  tool_id        VARCHAR(128)    COLLATE utf8mb4_bin NULL COMMENT '工具名。推理步骤为空',
  status         VARCHAR(32)     NOT NULL DEFAULT 'SUCCEEDED' COMMENT 'SUCCEEDED / FAILED / SKIPPED',
  input_summary  JSON            NULL COMMENT '保留列。当前不写，避免把参数放进来',
  output_summary JSON            NULL COMMENT '保留列。当前不写，正文摘要在 agent_run',
  token_input    INT UNSIGNED    NULL COMMENT '当前没有单步 token',
  token_output   INT UNSIGNED    NULL COMMENT '当前没有单步 token',
  duration_ms    BIGINT UNSIGNED NULL COMMENT '当前没有单步耗时',
  error_message  VARCHAR(1024)   NULL COMMENT '保留列。当前不写',
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_step (run_id, step_no),
  KEY idx_run (run_id),
  KEY idx_type_created (step_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：Agent 运行步骤';
