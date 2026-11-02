-- 本地联调演示账号。不进 Flyway，按需在本机库执行。
-- 与 web/.env.example 默认预填一致：tenant_id=demo / user_id=demo-user

INSERT INTO tenant (tenant_id, name, plan, status)
VALUES ('demo', '本地联调租户', 'standard', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  status = 'ACTIVE';

INSERT INTO tenant_user (tenant_id, user_id, display_name, status)
VALUES ('demo', 'demo-user', '联调用户', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  status = 'ACTIVE';
