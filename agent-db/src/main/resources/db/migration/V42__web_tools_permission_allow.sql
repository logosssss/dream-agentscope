-- 内置 web_search / web_fetch：全局 ALLOW（与现网放开口径一致；开关关时工具未装配，本行不生效）。
INSERT INTO tool_permission (tool_id, tenant_id, role_id, decision, conditions, priority, enabled)
VALUES ('web_fetch', '*', '*', 'ALLOW', NULL, 1, 1)
ON DUPLICATE KEY UPDATE decision = 'ALLOW', enabled = 1, priority = 1, conditions = NULL;

INSERT INTO tool_permission (tool_id, tenant_id, role_id, decision, conditions, priority, enabled)
VALUES ('web_search', '*', '*', 'ALLOW', NULL, 1, 1)
ON DUPLICATE KEY UPDATE decision = 'ALLOW', enabled = 1, priority = 1, conditions = NULL;
