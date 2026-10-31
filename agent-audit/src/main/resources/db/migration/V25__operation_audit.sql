-- 后台操作审计。区别于 agent_event_audit。这次只登记，不写前后快照。
CREATE TABLE operation_audit (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id   VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '操作人',
  tenant_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户，可空串',
  operation     VARCHAR(128)    NOT NULL COMMENT 'CREATE / UPDATE / DELETE 等',
  object_type   VARCHAR(64)     NOT NULL COMMENT '对象类型',
  object_id     VARCHAR(128)    NULL COMMENT '对象标识，可以空',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_operator_time (operator_id, created_at),
  KEY idx_object (object_type, object_id),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：后台操作审计';
