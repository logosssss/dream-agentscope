-- 工具权限规则。MySQL 打开后由这张表决定 ALLOW、ASK、DENY。带条件的行先不参与匹配。
CREATE TABLE tool_permission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tool_id     VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '工具标识',
  tenant_id   VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '*' COMMENT '* 表示全部租户',
  role_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '*' COMMENT '* 表示全部角色',
  decision    VARCHAR(32)     NOT NULL COMMENT 'ALLOW / ASK / DENY',
  conditions  JSON            NULL COMMENT '条件表达式。非空时当前版本跳过这条规则',
  priority    INT             NOT NULL DEFAULT 100 COMMENT '数值越小越优先',
  enabled     TINYINT(1)      NOT NULL DEFAULT 1,
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
              ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule (tool_id, tenant_id, role_id),
  KEY idx_tool (tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：工具权限规则';
