-- 会话状态。框架按 (session_id, state_key, item_index) 存 Agent 状态，关闭自动建表后由本脚本创建。
CREATE TABLE agentscope_sessions (
  session_id   VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  state_key    VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  item_index   INT NOT NULL DEFAULT 0,
  state_data   LONGTEXT NOT NULL,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (session_id, state_key, item_index)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工作区文件 KV，例如 MEMORY.md、sessions、skills。
CREATE TABLE agentscope_store (
  namespace_path VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
  key_name       VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  value_data     LONGBLOB NOT NULL,
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (namespace_path, key_name)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 沙箱快照。大对象也先放 MySQL，后续若膨胀再单独换后端。
CREATE TABLE agentscope_sandbox_snapshots (
  snapshot_key VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  snapshot_data LONGBLOB NOT NULL,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (snapshot_key)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
