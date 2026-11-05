package com.zhu.bootstrap;

import com.zhu.access.infrastructure.security.CallerTokenFilter;
import com.zhu.common.security.CallerTokens;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调用方令牌对不上时不进入后面的过滤器。存活检查不带令牌也能过。
 */
class CallerTokenFilterTest {

    /** 没配期望值时，带了同样的空头也不放行。 */
    @Test
    void blankConfigurationRejects() throws Exception {
        CallerTokenFilter filter = new CallerTokenFilter(" ");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        request.setServletPath("/api/sessions");
        request.addHeader(CallerTokens.HEADER, " ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    /** 样例占位串即使请求头对上也拒收，避免复制部署后裸奔。 */
    @Test
    void placeholderConfigurationRejects() throws Exception {
        CallerTokenFilter filter = new CallerTokenFilter("change-me-caller");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        request.setServletPath("/api/sessions");
        request.addHeader(CallerTokens.HEADER, "change-me-caller");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /** 存活检查不看令牌。 */
    @Test
    void healthSkipsToken() throws Exception {
        CallerTokenFilter filter = new CallerTokenFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/live");
        request.setServletPath("/health/live");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
    }

    /** 小程序确认不带调用方令牌也能进入后面的处理。 */
    @Test
    void wxConfirmSkipsToken() throws Exception {
        CallerTokenFilter filter = new CallerTokenFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/auth/wx/confirm");
        request.setServletPath("/api/public/auth/wx/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = new boolean[1];

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
    }
}
