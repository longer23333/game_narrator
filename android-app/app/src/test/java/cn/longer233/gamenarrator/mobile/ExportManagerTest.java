package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.transformer.Composition;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class ExportManagerTest {
    @Test public void reportsProgressFromRealRunner() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeRunner runner = new FakeRunner();
        RecordingListener listener = new RecordingListener();
        ExportManager manager = new ExportManager(runner, scheduler, listener);
        File output = new File("out.mp4");

        manager.start(runner, null, output, 7);
        runner.progressState = Transformer.PROGRESS_STATE_AVAILABLE;
        runner.progress = 42;
        scheduler.runOne();

        assertEquals(42, listener.progress.intValue());
        assertEquals(ExportStateMachine.State.RUNNING, manager.state());
        assertEquals(output, manager.output());
        assertEquals(7, manager.jobId());
    }

    @Test public void cancelInvokesRunnerAndReachesCancelled() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeRunner runner = new FakeRunner();
        RecordingListener listener = new RecordingListener();
        ExportManager manager = new ExportManager(runner, scheduler, listener);
        File output = new File("out.mp4");

        manager.start(runner, null, output, 7);
        manager.cancel();

        assertTrue(runner.cancelled);
        assertEquals(ExportStateMachine.State.CANCELLED, manager.state());
        assertEquals(output, listener.cancelled);
        assertTrue(manager.isTerminal());
    }

    @Test public void runnerErrorFailsExport() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeRunner runner = new FakeRunner();
        RecordingListener listener = new RecordingListener();
        ExportManager manager = new ExportManager(runner, scheduler, listener);

        manager.start(runner, null, new File("out.mp4"), 7);
        runner.emitError();

        assertEquals(ExportStateMachine.State.FAILED, manager.state());
        assertNotNull(listener.error);
        assertTrue(manager.isTerminal());
    }

    @Test public void runnerCompletionCompletesExport() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeRunner runner = new FakeRunner();
        RecordingListener listener = new RecordingListener();
        ExportManager manager = new ExportManager(runner, scheduler, listener);
        File output = new File("out.mp4");

        manager.start(runner, null, output, 7);
        runner.emitCompleted();

        assertEquals(ExportStateMachine.State.COMPLETED, manager.state());
        assertEquals(output, listener.completed);
        assertTrue(manager.isTerminal());
    }

    @Test public void abortMarksInterruptedFailure() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeRunner runner = new FakeRunner();
        RecordingListener listener = new RecordingListener();
        ExportManager manager = new ExportManager(runner, scheduler, listener);

        manager.start(runner, null, new File("out.mp4"), 7);
        manager.abort(ExportRecoveryPolicy.INTERRUPTED_ERROR);

        assertEquals(ExportStateMachine.State.FAILED, manager.state());
        assertEquals(ExportRecoveryPolicy.INTERRUPTED_ERROR, listener.error);
        assertTrue(manager.isTerminal());
    }

    @Test public void terminalFailureCanResetAndRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeRunner runner = new FakeRunner();
        RecordingListener listener = new RecordingListener();
        ExportManager manager = new ExportManager(runner, scheduler, listener);

        manager.start(runner, null, new File("out.mp4"), 7);
        runner.emitError();
        assertTrue(manager.canRetry());
        manager.reset();

        assertEquals(ExportStateMachine.State.IDLE, manager.state());
        assertNull(manager.output());
        assertTrue(manager.canRetry() == false);
    }

    @Test public void cancelledManagerCanResetAndRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeRunner runner = new FakeRunner();
        RecordingListener listener = new RecordingListener();
        ExportManager manager = new ExportManager(runner, scheduler, listener);
        File output = new File("out.mp4");

        manager.start(runner, null, output, 7);
        manager.cancel();

        assertEquals(ExportStateMachine.State.CANCELLED, manager.state());
        assertEquals(output, listener.cancelled);
        manager.reset();
        assertEquals(ExportStateMachine.State.IDLE, manager.state());
        manager.start(runner, null, new File("out-2.mp4"), 8);
        assertEquals(ExportStateMachine.State.RUNNING, manager.state());
    }

    private static final class FakeScheduler implements ExportManager.Scheduler {
        private final List<Runnable> pending = new ArrayList<>();

        @Override public void post(Runnable runnable) { pending.add(runnable); }
        @Override public void postDelayed(Runnable runnable, long delayMs) { pending.add(runnable); }

        void runOne() {
            if (pending.isEmpty()) return;
            pending.remove(0).run();
        }
    }

    private static final class FakeRunner implements ExportManager.ExportRunner {
        private final List<Callbacks> callbacks = new ArrayList<>();
        private boolean cancelled;
        private int progressState = Transformer.PROGRESS_STATE_UNAVAILABLE;
        private int progress;

        @Override public void start(Composition composition, String outputPath) { }
        @Override public void cancel() { cancelled = true; }
        @Override public int progressState() { return progressState; }
        @Override public int progress() { return progress; }
        @Override public void addListener(Callbacks listener) { callbacks.add(listener); }

        void emitCompleted() {
            for (Callbacks callback : callbacks) callback.onCompleted(null, null);
        }

        void emitError() {
            for (Callbacks callback : callbacks) callback.onError(null, null, (ExportException) null);
        }
    }

    private static final class RecordingListener implements ExportManager.Listener {
        private Integer progress;
        private File completed;
        private String error;
        private File cancelled;

        @Override public void onStateChanged(ExportStateMachine.State state) { }
        @Override public void onProgress(int percent) { progress = percent; }
        @Override public void onCompleted(File output) { completed = output; }
        @Override public void onError(String message) { error = message; }
        @Override public void onCancelled(File output) { cancelled = output; }
    }
}
