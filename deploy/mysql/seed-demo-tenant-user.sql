-- 本地联调演示租户和用户行。不进 Flyway，按需在本机库执行。
-- 这一行没有登录凭证，客户端不能用它登录。请走注册或微信扫码。

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
