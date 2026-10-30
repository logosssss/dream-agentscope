-- 权限审批。ASK 时插入，决策先为 PENDING。批准和拒绝以后再更新。
CREATE TABLE approval_record (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  approval_id   VARCHAR(64)     NOT NULL COMMENT '审批唯一标识',
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NOT NULL,
  run_id        VARCHAR(128)    NULL COMMENT 'AgentRun 标识',
  tool_name     VARCHAR(128)    NULL COMMENT '待审批工具',
  action        VARCHAR(256)    NULL COMMENT '动作描述或参数摘要',
  decision      VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED / EXPIRED',
  approver      VARCHAR(128)    NULL COMMENT '审批人',
  channel       VARCHAR(64)     NULL COMMENT '审批通道',
  reason        VARCHAR(1024)   NULL COMMENT '审批意见',
  requested_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  decided_at    DATETIME(3)     NULL,
  executed_at   DATETIME(3)     NULL COMMENT '批准后实际执行时间',
  result        JSON            NULL COMMENT '执行结果回写',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_approval_id (approval_id),
  KEY idx_session (user_id, session_id),
  KEY idx_decision_requested (decision, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='权限审批记录';

-- 执行审计。只存动作和短说明，不存提示词全文。
CREATE TABLE agent_event_audit (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id    VARCHAR(64)     NOT NULL COMMENT '事件唯一标识',
  event_type  VARCHAR(64)     NOT NULL COMMENT '动作名',
  user_id     VARCHAR(128)    NULL,
  session_id  VARCHAR(128)    NULL,
  run_id      VARCHAR(128)    NULL,
  source      VARCHAR(32)     NOT NULL COMMENT 'ACCESS/EXECUTOR/STORE/PERMISSION',
  payload     JSON            NULL COMMENT '短说明，不是提示词',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_id (event_id),
  KEY idx_session_created (user_id, session_id, created_at),
  KEY idx_type_created (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Agent 事件审计';
