-- 第一把业务工具目录行。没有对应代码实现时运行层仍不注册。
INSERT INTO tool_registry (tool_id, name, version, module, description, enabled)
VALUES ('echo_text', '回声', '1.0.0', 'companion', '原样返回', 1);

-- 全局 ASK，用来跑通审批。conditions 留空，当前版本才能命中。
INSERT INTO tool_permission (tool_id, tenant_id, role_id, decision, conditions, priority, enabled)
VALUES ('echo_text', '*', '*', 'ASK', NULL, 100, 1);
