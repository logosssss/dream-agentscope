-- 对齐 AgentScope Java 2.0.4 JdbcStore。
-- V1 的 key_name/value_data 与框架 SQL（item_key/value_json/version/updated_at）不兼容。
-- 工作区 KV 可重建；会话状态 agentscope_sessions 由框架 ensureVersionColumn 自行补 version。

DROP TABLE IF EXISTS agentscope_store;

CREATE TABLE agentscope_store (
  namespace_path VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT '命名空间路径，段间 0x1F，末尾带分隔符',
  item_key       VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '条目键，例如 AGENTS.md',
  value_json     LONGTEXT     NOT NULL COMMENT 'Jackson 序列化的 Map<String,Object>',
  version        BIGINT       NOT NULL COMMENT '乐观锁版本，写入时递增',
  updated_at     BIGINT       NOT NULL COMMENT '最后写入时间，epoch millis',
  PRIMARY KEY (namespace_path, item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Harness 远程工作区文件 KV（JdbcStore）';
