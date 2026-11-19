-- 模型价格。按生效时间取当前价。没有命中的行时用量金额记 0。
CREATE TABLE model_price (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  model          VARCHAR(128)    NOT NULL COMMENT '模型坐标',
  currency       CHAR(3)         NOT NULL DEFAULT 'CNY',
  input_per_1k   DECIMAL(12,6)   NOT NULL DEFAULT 0 COMMENT '每千输入 token 单价',
  output_per_1k  DECIMAL(12,6)   NOT NULL DEFAULT 0 COMMENT '每千输出 token 单价',
  effective_from DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始生效，含这一刻',
  effective_to   DATETIME(3)     NULL COMMENT '结束生效，不含这一刻。空表示仍有效',
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_model_effective (model, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='审计：模型价格';
