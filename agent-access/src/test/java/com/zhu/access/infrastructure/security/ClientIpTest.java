package com.zhu.access.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

/**
 * 来源地址只认单一的 X-Real-IP，伪造的转发内容退回连接对端。不启动 Spring。
 */
class ClientIpTest {

    /** 单一的真实地址头优先于连接对端。 */
    @Test
    void usesASingleRealIpHeader() {
        HttpServletRequest request = request("203.0.113.8", "10.0.0.1");

        assertThat(ClientIp.from(request)).isEqualTo("203.0.113.8");
    }

    /** 带逗号的头是调用方拼出来的，改用连接对端。对端也不是单一地址时留空。 */
    @Test
    void rejectsACommaSeparatedHeader() {
        assertThat(ClientIp.from(request("203.0.113.8, 10.0.0.1", "10.0.0.2"))).isEqualTo("10.0.0.2");
        assertThat(ClientIp.from(request("not an ip", "nope"))).isEmpty();
    }

    /** 没有这个头时用连接对端。两边都没有就空串。 */
    @Test
    void fallsBackToTheRemoteAddress() {
        assertThat(ClientIp.from(request(null, "::1"))).isEqualTo("::1");
        assertThat(ClientIp.from(request("   ", null))).isEmpty();
        assertThat(ClientIp.from(null)).isEmpty();
    }

    /** 没有请求上下文时不抛，给空串。调度和非 HTTP 调用走这里。 */
    @Test
    void captureWithoutARequestIsBlank() {
        assertThat(ClientIp.capture()).isEmpty();
    }

    /** 头和对端按测试给定。 */
    private static HttpServletRequest request(String realIp, String remote) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ClientIp.REAL_IP_HEADER)).thenReturn(realIp);
        when(request.getRemoteAddr()).thenReturn(remote);
        return request;
    }
}
