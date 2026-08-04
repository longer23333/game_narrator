package cn.longer233.gamenarrator.task.web;

import cn.longer233.gamenarrator.task.application.VideoTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TaskEventStreamService {
    private final VideoTaskService tasks;
    private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private volatile int lastStateHash;
    private volatile long lastHeartbeat;

    public TaskEventStreamService(VideoTaskService tasks) {
        this.tasks = tasks;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(error -> clients.remove(emitter));
        send(emitter, tasks.findAll());
        return emitter;
    }

    @Scheduled(fixedDelayString = "${game-narrator.task-stream.refresh-ms:1000}")
    public void publishChanges() {
        if (clients.isEmpty()) return;
        var snapshot = tasks.findAll();
        int stateHash = snapshot.hashCode();
        long now = System.currentTimeMillis();
        if (stateHash == lastStateHash && now - lastHeartbeat < 10_000) return;
        lastStateHash = stateHash;
        lastHeartbeat = now;
        clients.forEach(emitter -> send(emitter, snapshot));
    }

    private void send(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(data).reconnectTime(1000));
        } catch (IOException | IllegalStateException exception) {
            clients.remove(emitter);
            emitter.complete();
        }
    }
}
