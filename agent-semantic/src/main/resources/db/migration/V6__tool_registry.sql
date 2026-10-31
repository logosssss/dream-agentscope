-- 工具目录。MySQL 打开后，启用行成为这次可注册的工具标识。
-- 没有对应的代码实现时运行层不注册，避免把不能执行的名字交给模型。
-- param_schema 只保存，当前不解释。
CREATE TABLE tool_registry (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tool_id      VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '工具标识，和 @Tool 的 name 一致',
  name         VARCHAR(128)    NOT NULL COMMENT '展示名',
  version      VARCHAR(32)     NOT NULL DEFAULT '1.0.0' COMMENT '工具版本',
  module       VARCHAR(64)     NULL COMMENT '所属模块',
  param_schema JSON            NULL COMMENT '参数结构。当前只保存，不参与注册',
  description  TEXT            NULL COMMENT '给模型看的说明',
  enabled      TINYINT(1)      NOT NULL DEFAULT 1,
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
               ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_id (tool_id),
  KEY idx_module_enabled (module, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：工具目录';
