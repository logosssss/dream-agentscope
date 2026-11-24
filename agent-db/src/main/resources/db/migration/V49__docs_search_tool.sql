-- 平台文档检索工具。只读，全局 ALLOW，避免每次查文档走审批。
INSERT INTO tool_registry (tool_id, name, version, module, description, enabled)
VALUES (
  'docs_search',
  '文档检索',
  '1.0.0',
  'companion',
  '当需要查阅本平台自己的设计文档、落地记录或架构说明时使用。输入是检索问题。',
  1
);

INSERT INTO tool_permission (tool_id, tenant_id, role_id, decision, conditions, priority, enabled)
VALUES ('docs_search', '*', '*', 'ALLOW', NULL, 100, 1);
