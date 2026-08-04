package cn.longer233.gamenarrator.importer;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteThumbnailFetchPolicyTest {

    @Test
    void rejectsRepeatedFailureWithoutStartingAnotherRemoteRequest() {
        var policy = policy(2, 100);
        policy.failed("cover", new IllegalStateException("HTTP connect timed out"));

        assertThatThrownBy(() -> policy.acquire("cover"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("失败缓存")
                .hasMessageContaining("connect timed out");
    }

    @Test
    void limitsConcurrentRemoteRequestsAndReleasesPermit() throws Exception {
        var policy = policy(1, 50);
        var first = policy.acquire("first");
        assertThatThrownBy(() -> policy.acquire("second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("封面请求较多");

        first.close();
        try (var ignored = policy.acquire("second")) {
            // The released permit can be reused.
        }
    }

    private RemoteThumbnailFetchPolicy policy(int concurrency, long timeoutMs) {
        return new RemoteThumbnailFetchPolicy(concurrency, timeoutMs, 8, Duration.ofSeconds(90),
                Clock.fixed(Instant.parse("2026-08-04T08:00:00Z"), ZoneOffset.UTC));
    }
}
