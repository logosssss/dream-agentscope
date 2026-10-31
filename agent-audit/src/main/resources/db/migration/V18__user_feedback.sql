-- 用户对一次运行的评分。不存附件，也不存标签。
CREATE TABLE user_feedback (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id     VARCHAR(128)    NULL COMMENT '关联的运行。没有时为空',
  user_id    VARCHAR(128)    NOT NULL COMMENT '提交反馈的用户',
  rating     TINYINT         NOT NULL COMMENT '1 到 5',
  comment    VARCHAR(1000)   NULL COMMENT '短说明。不存全文对话',
  created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_run (run_id),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：用户反馈';
