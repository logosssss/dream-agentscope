-- 用户私有知识库。向量仍在 Milvus knowledge，正文以本表为真相源。

CREATE TABLE kb_knowledge_base (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  kb_id      VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '业务主键',
  tenant_id  VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户，空串表示无租户',
  user_id    VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '所有者，与 Milvus user_id 一致',
  agent_id   VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '绑定 Agent，检索过滤用',
  name       VARCHAR(128)    NOT NULL COMMENT '展示名',
  created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_kb_id (kb_id),
  KEY idx_owner (tenant_id, user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='知识库：用户私有库定义';

-- 同 doc_id 多版本。检索只认 SUCCESS；更新时旧成功版改 ARCHIVED。
CREATE TABLE kb_document (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  doc_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '文档业务主键，跨版本稳定',
  version    INT UNSIGNED    NOT NULL COMMENT '从 1 递增',
  kb_id      VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL,
  tenant_id  VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '',
  user_id    VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL,
  agent_id   VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL,
  title      VARCHAR(255)    NOT NULL DEFAULT '',
  content    MEDIUMTEXT      NOT NULL COMMENT '待索引正文，接口层已限 2MB',
  status     VARCHAR(32)     NOT NULL COMMENT 'PENDING / PROCESSING / SUCCESS / FAILED / ARCHIVED',
  error_msg  VARCHAR(512)    NULL COMMENT '失败摘要，不堆栈',
  created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_version (doc_id, version),
  KEY idx_kb_status (kb_id, status),
  KEY idx_owner_status (user_id, agent_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='知识库：文档版本';

-- chunk.id 与 Milvus knowledge.id 一致。
CREATE TABLE kb_chunk (
  id           VARCHAR(64)  COLLATE utf8mb4_bin NOT NULL COMMENT '与向量主键一致',
  kb_id        VARCHAR(64)  COLLATE utf8mb4_bin NOT NULL,
  doc_id       VARCHAR(64)  COLLATE utf8mb4_bin NOT NULL,
  version      INT UNSIGNED NOT NULL,
  tenant_id    VARCHAR(64)  COLLATE utf8mb4_bin NOT NULL DEFAULT '',
  user_id      VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  agent_id     VARCHAR(64)  COLLATE utf8mb4_bin NOT NULL,
  content      TEXT         NOT NULL,
  chunk_index  INT UNSIGNED NOT NULL,
  token_count  INT UNSIGNED NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_doc_version (doc_id, version),
  KEY idx_owner (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='知识库：切块正文；全文索引留给 Advanced RAG';
