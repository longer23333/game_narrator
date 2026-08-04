package cn.longer233.gamenarrator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;

@Component
public class PhaseRetryExecutor {
    private static final Logger log = LoggerFactory.getLogger(PhaseRetryExecutor.class);
    private final int downloadAttempts;
    private final int analysisAttempts;
    private final int generationAttempts;
    private final long initialBackoffMs;

    public PhaseRetryExecutor(
            @Value("${game-narrator.retry.download-attempts:3}") int downloadAttempts,
            @Value("${game-narrator.retry.analysis-attempts:2}") int analysisAttempts,
            @Value("${game-narrator.retry.generation-attempts:3}") int generationAttempts,
            @Value("${game-narrator.retry.initial-backoff-ms:500}") long initialBackoffMs) {
        this.downloadAttempts = Math.max(1, downloadAttempts);
        this.analysisAttempts = Math.max(1, analysisAttempts);
        this.generationAttempts = Math.max(1, generationAttempts);
        this.initialBackoffMs = Math.max(0, initialBackoffMs);
    }

    public <T> T download(Callable<T> action) { return execute("download", downloadAttempts, action); }
    public <T> T analysis(Callable<T> action) { return execute("analysis", analysisAttempts, action); }
    public <T> T generation(Callable<T> action) { return execute("generation", generationAttempts, action); }

    private <T> T execute(String phase, int attempts, Callable<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.call();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(phase + " interrupted", exception);
            } catch (Exception exception) {
                last = exception instanceof RuntimeException runtime
                        ? runtime : new IllegalStateException(exception.getMessage(), exception);
                if (attempt == attempts || !isTransient(exception)) throw last;
                long delay = Math.min(10_000, initialBackoffMs * (1L << Math.min(8, attempt - 1)));
                log.warn("PHASE_RETRY phase={} attempt={} delayMs={} reason={}",
                        phase, attempt, delay, exception.getClass().getSimpleName());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(phase + " retry interrupted", interrupted);
                }
            }
        }
        throw last == null ? new IllegalStateException(phase + " failed") : last;
    }

    static boolean isTransient(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof ConnectException
                    || current instanceof UnknownHostException) return true;
            if (current instanceof IOException) return true;
            String message = String.valueOf(current.getMessage()).toLowerCase();
            if (message.contains("timeout") || message.contains("timed out")
                    || message.contains("connection reset") || message.contains("temporarily unavailable")
                    || message.contains("http error 408") || message.contains("http error 429")
                    || message.matches(".*http error 5\\d\\d.*")) return true;
        }
        return false;
    }
}
