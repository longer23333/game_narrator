package cn.longer233.gamenarrator.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderWorkLifecycleTest {
    @TempDir Path storage;

    @Test
    void successfulRenderRemovesOnlyOwnedWorkDirectory() throws Exception {
        Path task = Files.createDirectories(storage.resolve("tasks/task-a"));
        Path work = Files.createDirectories(task.resolve("render-work/nested"));
        Files.writeString(work.resolve("clip.mp4"), "temporary");
        Path finalVideo = Files.writeString(task.resolve("final-video.mp4"), "keep");

        new RenderWorkLifecycle(storage.toString(), 2, 0).completed(task);

        assertThat(task.resolve("render-work")).doesNotExist();
        assertThat(finalVideo).exists();
    }

    @Test
    void failedRenderRetainsDiagnostics() throws Exception {
        Path task = Files.createDirectories(storage.resolve("tasks/task-b"));
        Path diagnostic = Files.createDirectories(task.resolve("render-work")).resolve("ffmpeg.log");
        Files.writeString(diagnostic, "failure detail");

        new RenderWorkLifecycle(storage.toString(), 2, 0).failed(task, new IllegalStateException("encoder failed"));

        assertThat(diagnostic).hasContent("failure detail");
    }

    @Test
    void refusesPathOutsideStorageAndTraversal() throws Exception {
        Path outside = Files.createDirectories(storage.getParent().resolve("outside-render-task"));
        RenderWorkLifecycle lifecycle = new RenderWorkLifecycle(storage.toString(), 1, 0);
        assertThatThrownBy(() -> lifecycle.completed(outside)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retriesBusyWorkAndEventuallyCleansIt() throws Exception {
        Path task = Files.createDirectories(storage.resolve("tasks/task-c"));
        Files.createDirectories(task.resolve("render-work"));
        AtomicInteger calls = new AtomicInteger();
        RenderWorkLifecycle lifecycle = new RenderWorkLifecycle(storage.toString(), 3, 0) {
            @Override void deleteTree(Path work) throws Exception {
                if (calls.incrementAndGet() < 3) throw new IOException("file is in use");
                super.deleteTree(work);
            }
        };

        assertThat(lifecycle.completed(task)).isTrue();
        assertThat(calls).hasValue(3);
        assertThat(task.resolve("render-work")).doesNotExist();
    }

    @Test
    void defersCleanupAfterBusyFileExhaustsRetriesWithoutDeletingDiagnostics() throws Exception {
        Path task = Files.createDirectories(storage.resolve("tasks/task-d"));
        Path diagnostic = Files.writeString(Files.createDirectories(task.resolve("render-work")).resolve("ffmpeg.log"), "keep");
        RenderWorkLifecycle lifecycle = new RenderWorkLifecycle(storage.toString(), 2, 0) {
            @Override void deleteTree(Path work) throws Exception { throw new IOException("file is in use"); }
        };

        assertThat(lifecycle.completed(task)).isFalse();
        assertThat(diagnostic).exists();
    }
}
