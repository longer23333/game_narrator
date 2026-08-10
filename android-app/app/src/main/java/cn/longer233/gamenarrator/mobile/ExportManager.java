package cn.longer233.gamenarrator.mobile;

import android.os.Handler;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import java.io.File;

@UnstableApi
public final class ExportManager {
    public interface Listener {
        void onStateChanged(ExportStateMachine.State state);
        void onProgress(int percent);
        void onCompleted(File output);
        void onError(String message);
        void onCancelled(File output);
    }

    public interface Scheduler {
        void post(Runnable runnable);
        void postDelayed(Runnable runnable, long delayMs);
    }

    public interface ExportRunner {
        interface Callbacks {
            void onCompleted(Composition composition, ExportResult result);
            void onError(Composition composition, ExportResult result, ExportException error);
        }
        void start(Composition composition, String outputPath);
        void cancel();
        int progressState();
        int progress();
        void addListener(Callbacks callbacks);
    }

    private final ExportStateMachine machine = new ExportStateMachine();
    private final Scheduler scheduler;
    private final Listener listener;
    private ExportRunner runner;
    private Composition composition;
    private File output;
    private long jobId = -1;
    private boolean polling;
    private boolean cancelledByUser;

    public ExportManager(Handler handler, Listener listener) {
        this(new HandlerScheduler(handler), listener);
    }

    ExportManager(Scheduler scheduler, Listener listener) {
        this.scheduler = scheduler;
        this.listener = listener;
    }

    ExportManager(ExportRunner runner, Scheduler scheduler, Listener listener) {
        this.runner = runner;
        this.scheduler = scheduler;
        this.listener = listener;
    }

    public ExportStateMachine.State state() { return machine.state(); }
    public File output() { return output; }
    public long jobId() { return jobId; }
    public String error() { return machine.error(); }
    public boolean isTerminal() { return machine.isTerminal(); }
    public boolean isActive() { return machine.state() == ExportStateMachine.State.RUNNING || machine.state() == ExportStateMachine.State.PAUSED; }

    public void start(Transformer transformer, Composition composition, File output, long jobId) {
        start(new Media3Runner(transformer), composition, output, jobId);
    }

    void start(ExportRunner runner, Composition composition, File output, long jobId) {
        if (machine.state() != ExportStateMachine.State.IDLE) throw new IllegalStateException("导出管理器非空闲：" + machine.state());
        this.runner = runner;
        this.composition = composition;
        this.output = output;
        this.jobId = jobId;
        this.cancelledByUser = false;
        machine.prepare();
        notifyState();
        machine.run();
        notifyState();
        polling = true;
        Runnable poll = new Runnable() {
            @Override public void run() {
                if (!polling || machine.isTerminal()) return;
                int progressState = ExportManager.this.runner.progressState();
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    listener.onProgress(ExportManager.this.runner.progress());
                }
                scheduler.postDelayed(this, 400);
            }
        };
        runner.addListener(new ExportRunner.Callbacks() {
            @Override public void onCompleted(Composition ignored, ExportResult result) {
                polling = false;
                if (machine.isTerminal()) return;
                if (cancelledByUser && machine.state() == ExportStateMachine.State.CANCELING) {
                    machine.cancelled();
                    notifyState();
                    listener.onCancelled(output);
                    return;
                }
                machine.complete();
                notifyState();
                listener.onCompleted(output);
            }

            @Override public void onError(Composition ignored, ExportResult result, ExportException error) {
                polling = false;
                if (machine.isTerminal()) return;
                if (cancelledByUser && machine.state() == ExportStateMachine.State.CANCELING) {
                    machine.cancelled();
                    notifyState();
                    listener.onCancelled(output);
                    return;
                }
                String message = error == null ? "未知导出错误" : error.getMessage();
                machine.fail(message);
                notifyState();
                listener.onError(machine.error());
            }
        });
        scheduler.post(poll);
        runner.start(composition, output.getAbsolutePath());
    }

    public void cancel() {
        if (!isActive()) return;
        cancelledByUser = true;
        polling = false;
        machine.cancel();
        machine.cancelled();
        notifyState();
        listener.onCancelled(output);
        if (runner != null) runner.cancel();
    }

    public void abort(String message) {
        if (machine.isTerminal()) return;
        machine.fail(message);
        notifyState();
        listener.onError(machine.error());
    }

    public void markCancelled() {
        if (machine.state() != ExportStateMachine.State.CANCELING) return;
        machine.cancelled();
        notifyState();
        listener.onCancelled(output);
    }

    public boolean canRetry() {
        return machine.isTerminal() && machine.state() != ExportStateMachine.State.COMPLETED;
    }

    public void reset() {
        if (machine.isTerminal()) {
            machine.reset();
            runner = null;
            composition = null;
            output = null;
            jobId = -1;
            polling = false;
            cancelledByUser = false;
        }
    }

    private void notifyState() {
        listener.onStateChanged(machine.state());
    }

    private static final class Media3Runner implements ExportRunner {
        private final Transformer transformer;
        private final ProgressHolder holder = new ProgressHolder();
        private Callbacks callbacks;

        Media3Runner(Transformer transformer) {
            this.transformer = transformer;
        }

        @Override public void start(Composition composition, String outputPath) {
            transformer.addListener(new Transformer.Listener() {
                @Override public void onCompleted(Composition c, ExportResult result) {
                    if (callbacks != null) callbacks.onCompleted(c, result);
                }
                @Override public void onError(Composition c, ExportResult result, ExportException error) {
                    if (callbacks != null) callbacks.onError(c, result, error);
                }
            });
            transformer.start(composition, outputPath);
        }

        @Override public void cancel() { transformer.cancel(); }
        @Override public int progressState() { return transformer.getProgress(holder); }
        @Override public int progress() { return holder.progress; }
        @Override public void addListener(Callbacks callbacks) { this.callbacks = callbacks; }
    }

    private static final class HandlerScheduler implements Scheduler {
        private final Handler handler;

        HandlerScheduler(Handler handler) {
            this.handler = handler;
        }

        @Override public void post(Runnable runnable) { handler.post(runnable); }
        @Override public void postDelayed(Runnable runnable, long delayMs) { handler.postDelayed(runnable, delayMs); }
    }
}
