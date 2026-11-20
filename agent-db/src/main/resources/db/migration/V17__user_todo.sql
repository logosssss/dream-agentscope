-- 待确认时给发起人开一条待办。这次只写 APPROVAL / OPEN，不关闭。
CREATE TABLE user_todo (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    VARCHAR(128)    NOT NULL COMMENT '发起用户',
  todo_type  VARCHAR(32)     NOT NULL COMMENT '这次只写 APPROVAL',
  ref_id     VARCHAR(128)    NOT NULL COMMENT '审批单标识',
  title      VARCHAR(256)    NULL COMMENT '工具名，不存参数',
  status     VARCHAR(32)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / DONE / EXPIRED / CANCELLED',
  due_at     DATETIME(3)     NULL COMMENT '这次不写',
  created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_ref (user_id, todo_type, ref_id),
  KEY idx_user_status (user_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：用户待办';
