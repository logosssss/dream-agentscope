-- 用户附件元数据。这次只登记路径和文件名，不接对象存储，也不写引用。
CREATE TABLE attachment (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  attachment_id VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '附件标识',
  tenant_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户。空串表示没有租户',
  user_id       VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '所属用户',
  session_id    VARCHAR(128)    COLLATE utf8mb4_bin NULL COMMENT '关联会话。可以空',
  file_name     VARCHAR(512)    NOT NULL COMMENT '原始文件名。不存文件内容',
  storage_type  VARCHAR(32)     NOT NULL COMMENT 'LOCAL / S3 / OSS / MINIO',
  storage_path  VARCHAR(1024)   NOT NULL COMMENT '声称的存储路径。这次不校验文件是否存在',
  mime_type     VARCHAR(128)    NULL,
  size_bytes    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '调用方声称的大小。这次不核对',
  checksum      VARCHAR(64)     NULL COMMENT 'SHA-256。可以空',
  status        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED。这次只写 ACTIVE',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_attachment_id (attachment_id),
  KEY idx_user_created (user_id, created_at),
  KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：用户附件';
