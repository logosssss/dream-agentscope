package com.zhu.bootstrap;

import com.zhu.access.infrastructure.security.CallerTokenFilter;
import com.zhu.access.infrastructure.security.WakeupTokenFilter;
import com.zhu.common.security.WakeupTokens;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部唤醒路径只验 X-Wakeup-Token；Caller 过滤器对该路径放行。
 */
class WakeupTokenFilterTest {

    /** 唤醒令牌对得上才进入后续链。 */
    @Test
    void acceptsMatchingWakeupToken() throws Exception {
        WakeupTokenFilter filter = new WakeupTokenFilter("test-wakeup-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/wakeups");
        request.setServletPath("/api/internal/wakeups");
        request.addHeader(WakeupTokens.HEADER, "test-wakeup-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
    }

    /** 错令牌或空令牌拒收。 */
    @Test
    void rejectsInvalidWakeupToken() throws Exception {
        WakeupTokenFilter filter = new WakeupTokenFilter("test-wakeup-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/wakeups");
        request.setServletPath("/api/internal/wakeups");
        request.addHeader(WakeupTokens.HEADER, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    /** 非唤醒路径不看唤醒令牌。 */
    @Test
    void nonWakeupPathSkipsFilter() throws Exception {
        WakeupTokenFilter filter = new WakeupTokenFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        request.setServletPath("/api/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
    }

    /** Caller 过滤器对内部唤醒路径放行，由 WakeupTokenFilter 单独校验。 */
    @Test
    void callerFilterSkipsInternalWakeup() throws Exception {
        CallerTokenFilter filter = new CallerTokenFilter("caller-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/wakeups");
        request.setServletPath("/api/internal/wakeups");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
    }
}
