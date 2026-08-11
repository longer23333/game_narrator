package cn.longer233.gamenarrator.task.web;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.application.VideoTaskService;
import cn.longer233.gamenarrator.task.application.VideoTaskView;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TaskEventStreamService {
    private static final long HEARTBEAT_MILLIS = 10_000;
    private final VideoTaskService tasks;
    private final CurrentUserContext currentUser;
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();

    public TaskEventStreamService(VideoTaskService tasks, CurrentUserContext currentUser) {
        this.tasks = tasks;
        this.currentUser = currentUser;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        UUID ownerId = currentUser.userId();
        List<VideoTaskView> snapshot = tasks.findAllForOwner(ownerId);
        Client client = new Client(ownerId, emitter, index(snapshot), System.currentTimeMillis());
        clients.add(client);
        emitter.onCompletion(() -> clients.remove(client));
        emitter.onTimeout(() -> clients.remove(client));
        emitter.onError(error -> clients.remove(client));
        if (!send(client, TaskStreamMessage.snapshot(snapshot, client.lastSentAt))) clients.remove(client);
        return emitter;
    }

    @Scheduled(fixedDelayString = "${game-narrator.task-stream.refresh-ms:1000}")
    public void publishChanges() {
        if (clients.isEmpty()) return;
        Map<UUID, List<Client>> byOwner = new LinkedHashMap<>();
        clients.forEach(client -> byOwner.computeIfAbsent(client.ownerId, ignored -> new ArrayList<>()).add(client));
        long now = System.currentTimeMillis();
        byOwner.forEach((ownerId, ownerClients) -> {
            List<VideoTaskView> snapshot = tasks.findAllForOwner(ownerId);
            Map<UUID, VideoTaskView> current = index(snapshot);
            for (Client client : ownerClients) {
                TaskStreamMessage delta = diff(client.tasks, current, now);
                if (delta.tasks().isEmpty() && delta.removedIds().isEmpty()) {
                    if (now - client.lastSentAt >= HEARTBEAT_MILLIS
                            && send(client, TaskStreamMessage.heartbeat(now))) client.lastSentAt = now;
                    continue;
                }
                if (send(client, delta)) {
                    client.tasks = current;
                    client.lastSentAt = now;
                }
            }
        });
    }

    static TaskStreamMessage diff(Map<UUID, VideoTaskView> previous,
                                  Map<UUID, VideoTaskView> current, long now) {
        List<VideoTaskView> changed = current.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(previous.get(entry.getKey())))
                .map(Map.Entry::getValue).toList();
        List<UUID> removed = previous.keySet().stream().filter(id -> !current.containsKey(id)).toList();
        return new TaskStreamMessage("delta", changed, removed, now);
    }

    private static Map<UUID, VideoTaskView> index(List<VideoTaskView> values) {
        Map<UUID, VideoTaskView> result = new LinkedHashMap<>();
        values.forEach(task -> result.put(task.id(), task));
        return Map.copyOf(result);
    }

    private boolean send(Client client, TaskStreamMessage data) {
        try {
            client.emitter.send(SseEmitter.event().data(data).reconnectTime(1000));
            return true;
        } catch (IOException | IllegalStateException exception) {
            clients.remove(client);
            client.emitter.complete();
            return false;
        }
    }

    private static final class Client {
        private final UUID ownerId;
        private final SseEmitter emitter;
        private volatile Map<UUID, VideoTaskView> tasks;
        private volatile long lastSentAt;

        private Client(UUID ownerId, SseEmitter emitter, Map<UUID, VideoTaskView> tasks, long lastSentAt) {
            this.ownerId = ownerId;
            this.emitter = emitter;
            this.tasks = tasks;
            this.lastSentAt = lastSentAt;
        }
    }
}
