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

/** Runs tools with concurrent output draining, a real timeout, and process-tree termination. */
public final class ExternalProcessRunner {
    private static final int MAX_OUTPUT_CHARS = 64 * 1024;
    private static final ExecutorService OUTPUT_DRAINER = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("external-output-", 0).factory());
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
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
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

    public static void terminateTree(Process process) {
        process.toHandle().descendants().forEach(child -> {
            child.destroy();
            if (child.isAlive()) child.destroyForcibly();
        });
        process.destroy();
        if (process.isAlive()) process.destroyForcibly();
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
