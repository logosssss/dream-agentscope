-- 聊天附件正文阅读工具。只读本人附件，全局 ALLOW，避免每次读文档走审批。
INSERT INTO tool_registry (tool_id, name, version, module, description, enabled)
VALUES (
  'attachment_read',
  '读附件',
  '1.0.0',
  'runtime',
  '按 attachmentId 读取 PDF/Word/纯文本正文',
  1
)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), enabled = 1;

INSERT INTO tool_permission (tool_id, tenant_id, role_id, decision, conditions, priority, enabled)
VALUES ('attachment_read', '*', '*', 'ALLOW', NULL, 100, 1)
ON DUPLICATE KEY UPDATE decision = 'ALLOW', enabled = 1, priority = 100, conditions = NULL;
