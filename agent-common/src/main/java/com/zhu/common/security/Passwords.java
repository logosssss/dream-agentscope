package com.zhu.common.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 客户端账号名和密码。不保存账号，也不调用微信。
 */
public final class Passwords {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MIN_NAME = 3;

    private static final int MAX_NAME = 32;

    private static final int MIN_PASSWORD = 6;

    private static final int MAX_PASSWORD = 128;

    private static final int ITERATIONS = 10_000;

    private Passwords() {
    }

    /**
     * 去掉首尾空白。短于 3、长于 32，或中间还有空白时拒绝。
     * 区分大小写，调用方按原样保存。
     */
    public static String name(String accountName) {
        String name = accountName == null ? "" : accountName.trim();
        if (name.length() < MIN_NAME) {
            throw new IllegalArgumentException("账号至少 3 个字符");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("账号太长");
        }
        if (name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("账号不能有空格");
        }
        return name;
    }

    /**
     * 只校验长度，不去掉空格。短于 6 或长于 128 时拒绝，避免把不合格的密码拿去哈希。
     */
    public static String password(String password) {
        String secret = password == null ? "" : password;
        if (secret.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        if (secret.length() > MAX_PASSWORD) {
            throw new IllegalArgumentException("密码太长");
        }
        return secret;
    }

    /** 16 字节盐，写成十六进制。每次注册新生成。 */
    public static String salt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * PBKDF2-HMAC-SHA256，迭代 10000 次，输出 256 位。
     * 盐必须是偶数位十六进制。失败时不把盐或密码写进异常。
     */
    public static String hash(String password, String salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(salt), ITERATIONS, 256);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return HexFormat.of().formatHex(encoded);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("password hash failed");
        }
    }

    /**
     * 盐或哈希为空时直接失败。比较两边的十六进制文本，不提前返回。
     */
    public static boolean matches(String password, String salt, String expected) {
        if (salt == null || salt.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        String actual = hash(password == null ? "" : password, salt);
        return MessageDigest.isEqual(
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
