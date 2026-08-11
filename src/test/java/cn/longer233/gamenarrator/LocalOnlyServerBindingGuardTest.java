package cn.longer233.gamenarrator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalOnlyServerBindingGuardTest {

    @Test
    void acceptsIpv4Ipv6AndLocalhostLoopbackAddresses() {
        LocalOnlyServerBindingGuard.validate("127.0.0.1");
        LocalOnlyServerBindingGuard.validate("::1");
        LocalOnlyServerBindingGuard.validate("[::1]");
        LocalOnlyServerBindingGuard.validate("localhost");
    }

    @Test
    void rejectsWildcardLanAndMissingAddressesBeforeServerStarts() {
        assertRejected("0.0.0.0");
        assertRejected("::");
        assertRejected("192.168.1.20");
        assertRejected(" ");
    }

    @Test
    void acceptsExternalBindingWhenApiAuthenticationIsRequired() {
        LocalOnlyServerBindingGuard.validate("0.0.0.0", true);
    }

    private void assertRejected(String address) {
        assertThatThrownBy(() -> LocalOnlyServerBindingGuard.validate(address))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("当前版本没有 API 身份认证");
    }
}
