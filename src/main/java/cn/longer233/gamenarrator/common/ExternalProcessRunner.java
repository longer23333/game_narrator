package cn.longer233.gamenarrator.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs tools with concurrent output draining, a real timeout, and process-tree termination. */
public final class ExternalProcessRunner {
    private static final int MAX_OUTPUT_CHARS = 64 * 1024;
    private static final ExecutorService OUTPUT_DRAINER = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("external-output-", 0).factory());
    private static final Map<ProcessType, Semaphore> LIMITS = new ConcurrentHashMap<>();
    private static final Map<ProcessType, AtomicInteger> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<ProcessType, Integer> CONFIGURED_LIMITS = new ConcurrentHashMap<>();
    static { configureLimits(1, 1, 2); }
    private ExternalProcessRunner() { }

    public static Result run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        return run(command, timeout, null);
    }

    public static Result run(List<String> command, Duration timeout, String standardInput)
            throws IOException, InterruptedException {
        return run(command, timeout, standardInput, line -> { });
    }

    public static Result run(List<String> command, Duration timeout, String standardInput,
                             Consumer<String> outputLine)
            throws IOException, InterruptedException {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("Command must not be empty");
        ProcessType type = classify(command.getFirst());
        Semaphore limit = LIMITS.get(type);
        acquire(limit, type);
        ACTIVE.get(type).incrementAndGet();
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            return runStarted(process, timeout, standardInput, outputLine);
        } finally {
            ACTIVE.get(type).decrementAndGet();
            limit.release();
        }
    }

    private static Result runStarted(Process process, Duration timeout, String standardInput,
                                     Consumer<String> outputLine)
            throws IOException, InterruptedException {
        TaskProcessRegistry.register(process);
        CompletableFuture<String> output = drain(process, outputLine);
        try {
            if (standardInput == null) process.getOutputStream().close();
            else try (var input = process.getOutputStream()) {
                input.write(standardInput.getBytes(StandardCharsets.UTF_8));
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            while (!process.waitFor(Math.min(500, Math.max(1,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))), TimeUnit.MILLISECONDS)) {
                if (TaskProcessRegistry.currentTaskCancelled()) {
                    terminateTree(process);
                    throw new java.util.concurrent.CancellationException("任务已取消");
                }
                if (System.nanoTime() >= deadline) {
                    terminateTree(process);
                    throw new ProcessTimeoutException(timeout);
                }
            }
            return new Result(process.exitValue(), join(output));
        } finally {
            if (process.isAlive()) terminateTree(process);
            if (!output.isDone()) output.cancel(true);
            TaskProcessRegistry.unregister(process);
        }
    }

    public static synchronized void configureLimits(int ffmpeg, int whisper, int other) {
        if (ffmpeg < 1 || whisper < 1 || other < 1) {
            throw new IllegalArgumentException("External process limits must be at least 1");
        }
        if (CONFIGURED_LIMITS.getOrDefault(ProcessType.FFMPEG, 0) == ffmpeg
                && CONFIGURED_LIMITS.getOrDefault(ProcessType.WHISPER, 0) == whisper
                && CONFIGURED_LIMITS.getOrDefault(ProcessType.OTHER, 0) == other) {
            return;
        }
        if (ACTIVE.values().stream().anyMatch(value -> value.get() > 0)) {
            throw new IllegalStateException("Cannot reconfigure external process limits while processes are active");
        }
        LIMITS.put(ProcessType.FFMPEG, new Semaphore(ffmpeg, true));
        LIMITS.put(ProcessType.WHISPER, new Semaphore(whisper, true));
        LIMITS.put(ProcessType.OTHER, new Semaphore(other, true));
        CONFIGURED_LIMITS.put(ProcessType.FFMPEG, ffmpeg);
        CONFIGURED_LIMITS.put(ProcessType.WHISPER, whisper);
        CONFIGURED_LIMITS.put(ProcessType.OTHER, other);
        for (ProcessType type : ProcessType.values()) ACTIVE.putIfAbsent(type, new AtomicInteger());
    }

    public static Map<String, Integer> activeCounts() {
        return Map.of("ffmpeg", ACTIVE.get(ProcessType.FFMPEG).get(),
                "whisper", ACTIVE.get(ProcessType.WHISPER).get(),
                "other", ACTIVE.get(ProcessType.OTHER).get());
    }

    private static void acquire(Semaphore limit, ProcessType type) throws InterruptedException {
        while (!limit.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            if (TaskProcessRegistry.currentTaskCancelled()) {
                throw new java.util.concurrent.CancellationException(
                        "Task cancelled while waiting for " + type.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static ProcessType classify(String executable) {
        String name;
        try { name = Path.of(executable).getFileName().toString().toLowerCase(Locale.ROOT); }
        catch (Exception ignored) { name = executable.toLowerCase(Locale.ROOT); }
        if (name.contains("ffmpeg") || name.contains("ffprobe")) return ProcessType.FFMPEG;
        if (name.contains("whisper")) return ProcessType.WHISPER;
        return ProcessType.OTHER;
    }

    private enum ProcessType { FFMPEG, WHISPER, OTHER }

    public static void terminateTree(Process process) {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        process.destroy();
        descendants.forEach(ProcessHandle::destroy);
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
        process.toHandle().descendants().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    private static CompletableFuture<String> drain(Process process, Consumer<String> outputLine) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder tail = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLine.accept(line);
                    tail.append(line).append('\n');
                    if (tail.length() > MAX_OUTPUT_CHARS) tail.delete(0, tail.length() - MAX_OUTPUT_CHARS);
                }
                return tail.toString();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, OUTPUT_DRAINER);
    }

    private static String join(CompletableFuture<String> output) throws IOException {
        try { return output.join(); }
        catch (CompletionException exception) {
            if (exception.getCause() instanceof IOException ioException) throw ioException;
            throw exception;
        }
    }

    public record Result(int exitCode, String output) { }

    public static final class ProcessTimeoutException extends IOException {
        public ProcessTimeoutException(Duration timeout) { super("External process timed out after " + timeout); }
    }
}
