package com.zhu.access.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 响应头兜底的单元测试。不起 Spring 上下文：这个过滤器不依赖任何 Bean，
 * 直接喂「过滤器的三个参数」能完整覆盖它的行为。
 */
class HttpSecurityHeadersFilterTest {

    private final HttpSecurityHeadersFilter filter = new HttpSecurityHeadersFilter();

    @Test
    void apiResponseCarriesSecurityHeadersAndIsNotCached() throws Exception {
        MockHttpServletResponse response = run("/api/chat");

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'");
        // 接口里会出现令牌回执与个人数据，任何一层都不许缓存。
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void healthResponseKeepsHeadersButStaysCacheable() throws Exception {
        MockHttpServletResponse response = run("/health/live");

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        // 探活结果没有隐私内容，不强制 no-store，避免影响探针自己的缓存策略。
        assertThat(response.getHeader("Cache-Control")).isNull();
    }

    @Test
    void chainStillRunsAfterHeadersAreSet() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/me"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void runsBeforeTokenFiltersSoRejectionsAlsoCarryHeaders() {
        assertThat(order(new HttpSecurityHeadersFilter())).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(order(new CallerTokenFilter("some-token")))
                .isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
        assertThat(order(new WakeupTokenFilter("some-token")))
                .isGreaterThan(order(new CallerTokenFilter("some-token")));
    }

    private MockHttpServletResponse run(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response;
    }

    /**
     * 读 {@code @Order} 的值。没标注时按 Spring 的默认值返回，避免测试里出现 NPE。
     */
    private static int order(Object bean) {
        Order annotation = bean.getClass().getAnnotation(Order.class);
        return annotation == null ? Ordered.LOWEST_PRECEDENCE : annotation.value();
    }
}
