-- 运行记录带来源地址：页面对话和 API 对话结束时写入。老行没有，允许为空。
-- 不建索引。列表不按地址筛选。
ALTER TABLE agent_run
    ADD COLUMN client_ip VARCHAR(45) NULL COMMENT '来源地址。没有则为空' AFTER request_id;
