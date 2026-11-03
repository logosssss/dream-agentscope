package com.zhu.bootstrap;

import com.zhu.common.security.Passwords;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 账号名和密码规则。不连数据库。
 */
class PasswordsTest {

    /** 首尾空白去掉，中间空白和过短过长都拒绝。大小写保留。 */
    @Test
    void nameTrimsAndRejectsWhitespace() {
        assertThat(Passwords.name("  abc  ")).isEqualTo("abc");
        assertThatThrownBy(() -> Passwords.name("ab")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Passwords.name("a b")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Passwords.name("AbC")).isEqualTo("AbC");
    }

    /** 密码不去掉空格。哈希能对上，盐不同则对不上。 */
    @Test
    void hashMatchesOnlyTheSameSalt() {
        assertThatThrownBy(() -> Passwords.password("12345")).isInstanceOf(IllegalArgumentException.class);
        String salt = Passwords.salt();
        String hash = Passwords.hash("secret1", salt);
        assertThat(Passwords.matches("secret1", salt, hash)).isTrue();
        assertThat(Passwords.matches("secret2", salt, hash)).isFalse();
        assertThat(Passwords.matches("secret1", Passwords.salt(), hash)).isFalse();
    }
}
