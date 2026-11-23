-- 任务调度：补上触发所需的归属列与结果列。
-- 以前只登记、不算下次触发、也不消费；现在由用户进程按 next_fire_at 取出，
-- 发布一条 AgentTask（task_type 原样传），并把 last_fire_at / last_status / fire_count 写回。
-- 发布要带用户，所以新登记的调度必须给出 user_id；历史行留空，不会被扫到。
ALTER TABLE agent_task_schedule
  ADD COLUMN tenant_id   VARCHAR(64)  COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '租户。空串表示没有租户' AFTER task_type,
  ADD COLUMN user_id     VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT '归属用户。触发时发布任务要带' AFTER tenant_id,
  ADD COLUMN agent_id    VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT '目标 Agent。空表示运行配置的默认 Agent' AFTER user_id,
  ADD COLUMN last_status VARCHAR(32)  NULL COMMENT '最近一次触发的任务状态。没触发过为空' AFTER last_fire_at,
  ADD COLUMN fire_count  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已触发次数' AFTER last_status;
