-- 一次评测执行。这次只记下用例条数，状态停在 RUNNING。不调用模型，也不写通过率。
CREATE TABLE eval_run (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  eval_run_id      VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '这次执行的标识',
  dataset_id       VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '数据集标识',
  dataset_version  VARCHAR(32)     NOT NULL COMMENT '这次固定 1.0.0',
  total_cases      INT UNSIGNED    NOT NULL COMMENT '开始时的用例条数',
  passed_cases     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '这次不打分，保持 0',
  pass_rate        DECIMAL(6,4)    NULL COMMENT '这次不写',
  status           VARCHAR(32)     NOT NULL COMMENT 'RUNNING / SUCCEEDED / FAILED',
  started_at       DATETIME(3)     NULL,
  finished_at      DATETIME(3)     NULL COMMENT '这次不写',
  created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_eval_run_id (eval_run_id),
  KEY idx_dataset (dataset_id, dataset_version),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：评测执行记录';
