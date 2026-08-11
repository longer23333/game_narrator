package cn.longer233.gamenarrator.community;

import cn.longer233.gamenarrator.effect.EffectPreset;
import cn.longer233.gamenarrator.effect.EffectPresetCatalog;
import cn.longer233.gamenarrator.event.GameKnowledgePackService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CommunityResourceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final GameKnowledgePackService knowledgePacks;
    private final EffectPresetCatalog styles;

    public CommunityResourceService(JdbcTemplate jdbc, ObjectMapper mapper,
                                    GameKnowledgePackService knowledgePacks, EffectPresetCatalog styles) {
        this.jdbc = jdbc; this.mapper = mapper; this.knowledgePacks = knowledgePacks; this.styles = styles;
    }

    public List<CommunityResourceView> list(String type) {
        String normalized = normalizeType(type, false);
        String sql = """
                SELECT id,resource_type,code,name,description,author_name,license_code,tags_json,
                       format_version,install_count,published_at,updated_at
                FROM community_resource
                """ + (normalized == null ? "" : " WHERE resource_type=?") + " ORDER BY published_at DESC";
        Object[] parameters = normalized == null ? new Object[0] : new Object[]{normalized};
        return jdbc.query(sql, (rs, row) -> {
            try {
                List<String> tags = mapper.readValue(rs.getString("tags_json"), new TypeReference<>() { });
                return new CommunityResourceView(rs.getObject("id", UUID.class), rs.getString("resource_type"),
                        rs.getString("code"), rs.getString("name"), rs.getString("description"),
                        rs.getString("author_name"), rs.getString("license_code"), List.copyOf(tags),
                        rs.getInt("format_version"), rs.getInt("install_count"),
                        rs.getObject("published_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
            } catch (Exception exception) { throw new IllegalStateException("社区资源标签损坏", exception); }
        }, parameters);
    }

    public CommunityResourceView publishKnowledgePack(String code, PublishCommunityResourceRequest request) {
        JsonNode payload = knowledgePacks.exportPack(code);
        return publish("KNOWLEDGE_PACK", code, payload.path("name").asText(code),
                payload.path("description").asText(""), payload, GameKnowledgePackService.FORMAT_VERSION, request);
    }

    public CommunityResourceView publishStyle(String code, PublishCommunityResourceRequest request) {
        EffectPreset preset = styles.require(code);
        return publish("EDITING_STYLE", preset.code(), preset.name(), preset.description(),
                mapper.valueToTree(preset), 1, request);
    }

    @Transactional
    public JsonNode install(UUID id) {
        var row = jdbc.queryForMap("SELECT resource_type,payload_json FROM community_resource WHERE id=?", id);
        try {
            JsonNode payload = mapper.readTree(row.get("PAYLOAD_JSON").toString());
            if ("KNOWLEDGE_PACK".equals(row.get("RESOURCE_TYPE"))) knowledgePacks.importPack(payload, false);
            else {
                EffectPreset preset = mapper.treeToValue(payload, EffectPreset.class);
                try { styles.require(preset.code()); }
                catch (IllegalArgumentException missing) { styles.importTemplate(preset); }
            }
            jdbc.update("UPDATE community_resource SET install_count=install_count+1,updated_at=? WHERE id=?",
                    OffsetDateTime.now(), id);
            return payload;
        } catch (Exception exception) { throw new IllegalArgumentException("无法安装社区资源", exception); }
    }

    public JsonNode payload(UUID id) {
        String json = jdbc.query("SELECT payload_json FROM community_resource WHERE id=?",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (json == null) throw new IllegalArgumentException("社区资源不存在");
        try { return mapper.readTree(json); }
        catch (Exception exception) { throw new IllegalStateException("社区资源内容损坏", exception); }
    }

    private CommunityResourceView publish(String type, String code, String name, String description,
                                          JsonNode payload, int formatVersion, PublishCommunityResourceRequest request) {
        try {
            String author = request == null || request.authorName() == null ? "本地创作者" : request.authorName().trim();
            String license = request == null || request.licenseCode() == null ? "CC-BY-4.0" : request.licenseCode().trim();
            List<String> tags = request == null || request.tags() == null ? List.of() : request.tags().stream()
                    .map(String::trim).filter(value -> !value.isBlank()).distinct().limit(12).toList();
            if (author.isBlank() || author.length() > 100) throw new IllegalArgumentException("作者名称长度无效");
            if (!license.matches("[A-Za-z0-9._+-]{2,40}")) throw new IllegalArgumentException("许可协议格式无效");
            UUID id = jdbc.query("SELECT id FROM community_resource WHERE resource_type=? AND code=?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : UUID.randomUUID(), type, code);
            OffsetDateTime now = OffsetDateTime.now();
            cn.longer233.gamenarrator.common.PortableUpsert.update(jdbc, """
                    MERGE INTO community_resource(id,resource_type,code,name,description,author_name,license_code,
                    tags_json,payload_json,format_version,install_count,published_at,updated_at) KEY(resource_type,code)
                    VALUES(?,?,?,?,?,?,?,?,?,?,COALESCE((SELECT install_count FROM community_resource WHERE resource_type=? AND code=?),0),
                    COALESCE((SELECT published_at FROM community_resource WHERE resource_type=? AND code=?),?),?)
                    """, "resource_type,code", id, type, code, name, description, author, license, mapper.writeValueAsString(tags),
                    mapper.writeValueAsString(payload), formatVersion, type, code, type, code, now, now);
            return list(type).stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
        } catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("无法发布社区资源", exception); }
    }

    private String normalizeType(String value, boolean required) {
        if (value == null || value.isBlank()) { if (required) throw new IllegalArgumentException("资源类型不能为空"); return null; }
        String type = value.trim().toUpperCase(Locale.ROOT);
        if (!type.equals("KNOWLEDGE_PACK") && !type.equals("EDITING_STYLE"))
            throw new IllegalArgumentException("不支持的社区资源类型");
        return type;
    }
}
