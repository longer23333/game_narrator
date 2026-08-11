package cn.longer233.gamenarrator.ai;

import java.util.Map;

/** Common identity and health contract for every pluggable inference engine. */
public interface ModelAdapter {
    String engineId();
    boolean available();
    default Map<String, Object> diagnostics() { return Map.of("engine", engineId(), "available", available()); }
}
