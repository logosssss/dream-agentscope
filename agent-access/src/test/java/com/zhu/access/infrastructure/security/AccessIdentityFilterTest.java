package com.zhu.access.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.cache.api.CacheFacade;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 身份过滤器只认 Bearer，请求号原样回显。不连接 Redis。
 */
class AccessIdentityFilterTest {

    /** 带了请求号就回显给调用方，并继续往下走。 */
    @Test
    void echoesRequestIdWhenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        request.addHeader(AccessIdentityFilter.REQUEST_ID_HEADER, "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean(false);

        filter().doFilter(request, response, (req, res) -> passed.set(true));

        assertThat(response.getHeader(AccessIdentityFilter.REQUEST_ID_HEADER)).isEqualTo("req-1");
        assertThat(passed).isTrue();
    }

    /** 没带请求号时不凭空生成，也不回显。 */
    @Test
    void staysSilentWithoutRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean(false);

        filter().doFilter(request, response, (req, res) -> passed.set(true));

        assertThat(response.getHeader(AccessIdentityFilter.REQUEST_ID_HEADER)).isNull();
        assertThat(passed).isTrue();
    }

    /** 空 BeanFactory 提供的 ObjectProvider 拿不到缓存，正合「没配 Redis」的装配。 */
    private static AccessIdentityFilter filter() {
        ObjectProvider<CacheFacade> caches =
                new DefaultListableBeanFactory().getBeanProvider(CacheFacade.class);
        return new AccessIdentityFilter(caches);
    }
}
