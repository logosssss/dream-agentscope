-- 客户端登录凭证。密码账号和微信账号分行，不表示管理员。
-- 登录名在租户内唯一。openid 全局唯一。停用仍看 tenant_user。
CREATE TABLE login_credential (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id     VARCHAR(64)     COLLATE utf8mb4_bin NOT NULL COMMENT '已登记的租户',
  user_id       VARCHAR(128)    COLLATE utf8mb4_bin NOT NULL COMMENT 'tenant_user 的业务标识',
  login_name    VARCHAR(32)     COLLATE utf8mb4_bin NULL COMMENT '密码登录名。微信账号为空',
  password_salt VARCHAR(64)     NULL COMMENT '十六进制盐。微信账号为空',
  password_hash VARCHAR(128)    NULL COMMENT 'PBKDF2-HMAC-SHA256。微信账号为空',
  open_id       VARCHAR(64)     COLLATE utf8mb4_bin NULL COMMENT '微信 openid。密码账号为空',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_login_name (tenant_id, login_name),
  UNIQUE KEY uk_open_id (open_id),
  UNIQUE KEY uk_credential_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品域：客户端登录凭证';
