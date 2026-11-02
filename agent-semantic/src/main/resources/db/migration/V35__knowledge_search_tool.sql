-- Naive RAG 检索工具。只读，全局 ALLOW，避免每次查库走审批。
INSERT INTO tool_registry (tool_id, name, version, module, description, enabled)
VALUES (
  'knowledge_search',
  '知识检索',
  '1.0.0',
  'companion',
  '当需要查找当前用户专有知识、文档或规范时使用。输入是检索问题。',
  1
);

INSERT INTO tool_permission (tool_id, tenant_id, role_id, decision, conditions, priority, enabled)
VALUES ('knowledge_search', '*', '*', 'ALLOW', NULL, 100, 1);
