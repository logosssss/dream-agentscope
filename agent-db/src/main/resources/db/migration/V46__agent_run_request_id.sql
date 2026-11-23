-- 运行记录带上请求号：调用方带 X-Request-Id 时，报障可以按它定位到具体一轮。
-- 老行没有这个值，允许为空；索引只用于按请求号回查，不参与列表排序。
ALTER TABLE agent_run
    ADD COLUMN request_id VARCHAR(64) COLLATE utf8mb4_bin NULL AFTER session_id,
    ADD KEY idx_request_id (request_id);
