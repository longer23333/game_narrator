package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionAccessFilterTest {
    private static final String TOKEN = "123456789012345678901234";

    @Test void loopbackNeedsNoPairingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        MockFilterChain chain = new MockFilterChain();
        new CompanionAccessFilter("").doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test void lanRequestRequiresMatchingPairingToken() throws Exception {
        CompanionAccessFilter filter = new CompanionAccessFilter(TOKEN);
        MockHttpServletRequest rejected = new MockHttpServletRequest(); rejected.setRemoteAddr("192.168.1.20");
        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(rejected, denied, new MockFilterChain());
        assertThat(denied.getStatus()).isEqualTo(401);

        MockHttpServletRequest accepted = new MockHttpServletRequest(); accepted.setRemoteAddr("192.168.1.20");
        accepted.addHeader(CompanionAccessFilter.HEADER, TOKEN); MockFilterChain chain = new MockFilterChain();
        filter.doFilter(accepted, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
