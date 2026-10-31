-- 评测数据集。这次只登记 1.0.0，不写用例，也不执行。
CREATE TABLE eval_dataset (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dataset_id  VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '数据集标识',
  name        VARCHAR(256)    NOT NULL COMMENT '名称',
  version     VARCHAR(32)     NOT NULL DEFAULT '1.0.0' COMMENT '这次固定 1.0.0',
  description VARCHAR(1000)   NULL COMMENT '短说明',
  enabled     TINYINT(1)      NOT NULL DEFAULT 1,
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dataset_version (dataset_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：评测数据集';
