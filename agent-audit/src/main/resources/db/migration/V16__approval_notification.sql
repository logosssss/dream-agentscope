-- 待确认时记下还没推送。这次只写 PENDING，不发消息。
CREATE TABLE approval_notification (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  approval_id  VARCHAR(64)     NOT NULL COMMENT '关联 approval_record.approval_id',
  receiver_id  VARCHAR(128)    NOT NULL COMMENT '接收人，取发起用户',
  channel      VARCHAR(64)     NOT NULL COMMENT '这次只写 WEB',
  send_status  VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED',
  retry_count  INT UNSIGNED    NOT NULL DEFAULT 0,
  last_error   VARCHAR(1024)   NULL COMMENT '这次不写',
  sent_at      DATETIME(3)     NULL COMMENT '这次不写',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_approval (approval_id),
  KEY idx_receiver_status (receiver_id, send_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='接入层：审批通知发送记录';
