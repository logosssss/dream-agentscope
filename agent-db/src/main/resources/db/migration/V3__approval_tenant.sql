-- 审批单补租户。用户进程按租户和用户查待确认，不再把通知放在进程内存里。
ALTER TABLE approval_record
  ADD COLUMN tenant_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '租户，没有租户时为空串' AFTER approval_id;

CREATE INDEX idx_tenant_user_decision ON approval_record (tenant_id, user_id, decision);
