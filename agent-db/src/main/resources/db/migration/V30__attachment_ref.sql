-- 附件和消息或运行的关联。这次只登记引用，不核对该消息或运行是否存在。
CREATE TABLE attachment_ref (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  attachment_id VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '已登记的附件',
  ref_type      VARCHAR(32)     NOT NULL COMMENT 'MESSAGE / RUN',
  ref_id        VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT '消息或运行标识',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_attachment_ref (attachment_id, ref_type, ref_id),
  KEY idx_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：附件引用';
