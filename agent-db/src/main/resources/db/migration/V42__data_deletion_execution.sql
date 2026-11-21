-- 删除请求从「登记」变成「真执行」。
-- executed_at 为 NULL 表示还没有执行；DONE / FAILED 由用户进程的执行器回写。
-- idx_status_requested 给执行器扫「已批准但未执行」用，避免全表扫。
ALTER TABLE data_deletion_request
  ADD COLUMN executed_at   DATETIME(3)  NULL COMMENT '执行时间；NULL 表示还没执行' AFTER requested_at,
  ADD COLUMN executed_note VARCHAR(512) NULL COMMENT '执行结果摘要：各表删除行数与被跳过的步骤' AFTER executed_at,
  ADD KEY idx_status_requested (status, requested_at);
