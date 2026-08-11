package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.AccessDeniedException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhaseRetryExecutorTest {
    @Test
    void retriesNetworkFailuresButNotPermanentFileFailures() {
        assertThat(PhaseRetryExecutor.isTransient(new ConnectException("refused"))).isTrue();
        assertThat(PhaseRetryExecutor.isTransient(new FileNotFoundException("missing"))).isFalse();
        assertThat(PhaseRetryExecutor.isTransient(new AccessDeniedException("private"))).isFalse();
    }

    @Test
    void permanentFailureIsAttemptedOnlyOnce() {
        PhaseRetryExecutor retry = new PhaseRetryExecutor(3, 3, 3, 0);
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.download(() -> {
            calls.incrementAndGet();
            throw new FileNotFoundException("missing");
        })).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(FileNotFoundException.class);
        assertThat(calls).hasValue(1);
    }
}
