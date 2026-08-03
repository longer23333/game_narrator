package cn.longer233.gamenarrator.task.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "game_events")
public class GameEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private double startSeconds;

    @Column(nullable = false)
    private double endSeconds;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private double highlightScore;

    @Column(nullable = false, length = 500)
    private String description;

    protected GameEvent() {
    }
}
