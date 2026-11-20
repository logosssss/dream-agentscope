-- 数据删除请求。这次只记下待处理，不批准，也不执行删除。
CREATE TABLE data_deletion_request (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id    VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '请求标识',
  user_id       VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '发起人',
  tenant_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户，可空串',
  data_type     VARCHAR(64)     NOT NULL COMMENT 'SESSION / ATTACHMENT / ALL',
  status        VARCHAR(32)     NOT NULL COMMENT '这次固定 PENDING',
  reason        VARCHAR(1024)   NULL COMMENT '原因，可以空',
  requested_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：数据删除请求';
