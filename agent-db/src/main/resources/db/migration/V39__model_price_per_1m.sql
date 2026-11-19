-- 模型单价改为「元/百万 token」，与百炼等主流报价单位对齐。
-- 按门禁约定清空旧「每千」行，避免数值语义混淆；历史 token_usage 金额不重算。
TRUNCATE TABLE model_price;

ALTER TABLE model_price
  CHANGE COLUMN input_per_1k input_per_1m DECIMAL(12, 6) NOT NULL DEFAULT 0 COMMENT '每百万输入 token 单价',
  CHANGE COLUMN output_per_1k output_per_1m DECIMAL(12, 6) NOT NULL DEFAULT 0 COMMENT '每百万输出 token 单价';
