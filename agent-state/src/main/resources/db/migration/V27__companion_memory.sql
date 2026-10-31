-- 情景记忆。按租户和用户切开。不存对话全文。接入层和管理进程不写。
CREATE TABLE episodic_memory (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id  VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户，可空串',
  user_id    VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '记忆所属用户',
  session_id VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '来源会话，可空串',
  summary    VARCHAR(500)    NOT NULL COMMENT '短摘要。不存对话全文',
  salience   TINYINT         NOT NULL DEFAULT 1 COMMENT '1 到 5，越高越重要',
  created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_user_created (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='状态层：情景记忆';

-- 用户画像。每个租户用户一行。关系阶段由运行层写，接入层不改。
CREATE TABLE user_profile (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id          VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户，可空串',
  user_id            VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '画像所属用户',
  relationship_stage VARCHAR(32)     NOT NULL DEFAULT 'ACQUAINTANCE' COMMENT '关系阶段',
  notes              VARCHAR(1000)   NULL COMMENT '短备注。不存对话全文',
  created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='状态层：用户画像';
