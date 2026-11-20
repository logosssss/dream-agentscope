-- 计费汇总。按租户和区间把 token_usage 收成一张草稿。确认和开票还没做。
CREATE TABLE billing_record (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id    VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '租户',
  period_start DATETIME(3)     NOT NULL COMMENT '区间起点，含这一刻',
  period_end   DATETIME(3)     NOT NULL COMMENT '区间终点，不含这一刻',
  token_total  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  cost_amount  DECIMAL(14,6)   NOT NULL DEFAULT 0 COMMENT '用量金额合计，保留 6 位小数',
  currency     CHAR(3)         NOT NULL DEFAULT 'CNY',
  status       VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '当前只写 DRAFT',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_period (tenant_id, period_start, period_end),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='审计：计费汇总';
