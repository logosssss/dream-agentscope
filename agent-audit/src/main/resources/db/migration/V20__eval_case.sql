-- 评测用例。只挂在已经登记的 1.0.0 上。不存标签，也不执行。
CREATE TABLE eval_case (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dataset_id    VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '数据集标识',
  version       VARCHAR(32)     NOT NULL COMMENT '这次固定 1.0.0',
  case_no       INT UNSIGNED    NOT NULL COMMENT '用例序号，从 1 起',
  input_text    VARCHAR(4000)   NOT NULL COMMENT '输入',
  expected_text VARCHAR(4000)   NULL COMMENT '期望输出，可以空',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dataset_case (dataset_id, version, case_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：评测用例';
