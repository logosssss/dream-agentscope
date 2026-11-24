-- 文档站的可丢弃索引。正文以 git 为准，这里的 markdown / text 可以整表丢掉再同步。
-- 不回写源文件。embedded_hash 为空表示向量还没写上，下次同步只补这些块。
CREATE TABLE docs_document (
  id            VARCHAR(64)  NOT NULL PRIMARY KEY,
  repo          VARCHAR(64)  NOT NULL,
  path          VARCHAR(512) NOT NULL,
  title         VARCHAR(255) NOT NULL,
  topic         VARCHAR(32)  NOT NULL,
  doc_type      VARCHAR(32)  NOT NULL,
  visibility    VARCHAR(16)  NOT NULL,
  source        VARCHAR(64)  NOT NULL,
  content_hash  VARCHAR(64)  NOT NULL,
  markdown      MEDIUMTEXT   NOT NULL,
  indexed_at    DATETIME     NOT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_repo_path (repo, path),
  KEY idx_topic (topic),
  KEY idx_vis (visibility)
);

CREATE TABLE docs_chunk (
  id             VARCHAR(64)  NOT NULL PRIMARY KEY,
  doc_id         VARCHAR(64)  NOT NULL,
  heading        VARCHAR(255) NOT NULL,
  anchor         VARCHAR(255) NOT NULL,
  ordinal        INT          NOT NULL,
  text_hash      VARCHAR(64)  NOT NULL,
  embedded_hash  VARCHAR(64)  NULL,
  `text`         MEDIUMTEXT   NOT NULL,
  KEY idx_doc (doc_id)
);

CREATE TABLE docs_sync_run (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  started_at  DATETIME     NOT NULL,
  finished_at DATETIME     NULL,
  scanned     INT          NOT NULL DEFAULT 0,
  added       INT          NOT NULL DEFAULT 0,
  updated     INT          NOT NULL DEFAULT 0,
  removed     INT          NOT NULL DEFAULT 0,
  status      VARCHAR(16)  NOT NULL,
  note        VARCHAR(512) NULL
);
