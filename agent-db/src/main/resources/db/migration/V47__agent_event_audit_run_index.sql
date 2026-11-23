-- 运维按运行号回查审计时用得上：P14 之后 agent_event_audit 才带 run_id，
-- P15 把「按运行号看一轮做了什么」做成管理端接口，这里补上对应的索引。
-- 老行 run_id 为 NULL，不占索引前缀，但也不影响按 run_id 查询（NULL 不会被等值命中）。
ALTER TABLE agent_event_audit
    ADD KEY idx_run (run_id, created_at);
