package cn.longer233.gamenarrator.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class GameKnowledgePackService {
    public static final int FORMAT_VERSION = 1;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public GameKnowledgePackService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @PostConstruct
    void registerBuiltIn() {
        try (InputStream input = new ClassPathResource("knowledge-packs/boss-battle-v1.json").getInputStream()) {
            importPack(mapper.readTree(input), true);
        } catch (Exception exception) {
            throw new IllegalStateException("无法注册内置游戏知识包", exception);
        }
    }

    public List<KnowledgePackView> list() {
        return jdbc.query("""
                SELECT code,name,description,format_version,built_in,active,imported_at
                FROM game_knowledge_packs ORDER BY built_in DESC,name,code
                """, (rs, row) -> new KnowledgePackView(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getBoolean(5), rs.getBoolean(6), rs.getObject(7, OffsetDateTime.class)));
    }

    public BossBattleKnowledgePack importPack(JsonNode source, boolean builtIn) {
        validate(source);
        try {
            ObjectNode normalizedSource = ((ObjectNode) source.deepCopy());
            normalizedSource.put("formatVersion", FORMAT_VERSION);
            BossBattleKnowledgePack pack = mapper.treeToValue(normalizedSource, BossBattleKnowledgePack.class);
            String normalized = mapper.writeValueAsString(pack);
            String code = pack.code().trim();
            String description = pack.description() == null ? "" : pack.description().trim();
            OffsetDateTime importedAt = OffsetDateTime.now();
            int changed = jdbc.update("""
                    UPDATE game_knowledge_packs SET name=?,description=?,format_version=?,pack_json=?,built_in=?,
                    active=TRUE,imported_at=? WHERE code=?
                    """, pack.name().trim(), description, FORMAT_VERSION, normalized, builtIn, importedAt, code);
            if (changed == 0) jdbc.update("""
                    INSERT INTO game_knowledge_packs(code,name,description,format_version,pack_json,built_in,active,imported_at)
                    VALUES(?,?,?,?,?,?,TRUE,?)
                    """, code, pack.name().trim(), description, FORMAT_VERSION, normalized, builtIn, importedAt);
            return pack;
        } catch (Exception exception) {
            throw new IllegalArgumentException("知识包 JSON 无法解析", exception);
        }
    }

    public JsonNode exportPack(String code) {
        String json = jdbc.query("SELECT pack_json FROM game_knowledge_packs WHERE code=?",
                rs -> rs.next() ? rs.getString(1) : null, code);
        if (json == null) throw new IllegalArgumentException("知识包不存在");
        try { return mapper.readTree(json); }
        catch (Exception exception) { throw new IllegalStateException("知识包内容已损坏", exception); }
    }

    public List<BossBattleKnowledgePack> activePacks() {
        return jdbc.query("SELECT pack_json FROM game_knowledge_packs WHERE active=TRUE ORDER BY built_in,imported_at DESC",
                (rs, row) -> {
                    try { return mapper.readValue(rs.getString(1), BossBattleKnowledgePack.class); }
                    catch (Exception exception) { throw new IllegalStateException("知识包内容已损坏", exception); }
                });
    }

    public List<BossBattleKnowledgePack.TerminologyEntry> activeTerminology() {
        return activePacks().stream()
                .flatMap(pack -> pack.terminology() == null ? java.util.stream.Stream.empty()
                        : pack.terminology().stream())
                .toList();
    }

    private void validate(JsonNode value) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("知识包必须是 JSON 对象");
        String code = value.path("code").asText("").trim();
        String name = value.path("name").asText("").trim();
        JsonNode rules = value.path("eventRules");
        if (!code.matches("[a-z0-9][a-z0-9._-]{2,79}")) throw new IllegalArgumentException("知识包 code 格式无效");
        if (name.isEmpty() || name.length() > 160) throw new IllegalArgumentException("知识包名称长度无效");
        if (!rules.isArray() || rules.isEmpty() || rules.size() > 200) throw new IllegalArgumentException("知识包规则必须包含 1 到 200 项");
        rules.forEach(rule -> {
            if (rule.path("code").asText("").isBlank() || !rule.path("keywords").isArray())
                throw new IllegalArgumentException("知识包事件规则缺少 code 或 keywords");
            if (rule.path("keywords").size() > 100) throw new IllegalArgumentException("单条规则关键词不能超过 100 个");
            double confidence = rule.path("baseConfidence").asDouble(-1);
            int importance = rule.path("importance").asInt(-1);
            if (confidence < 0 || confidence > 1 || importance < 0 || importance > 100)
                throw new IllegalArgumentException("规则置信度或重要度超出范围");
        });
        JsonNode terminology = value.path("terminology");
        if (!terminology.isMissingNode() && !terminology.isNull()) {
            if (!terminology.isArray() || terminology.size() > 500)
                throw new IllegalArgumentException("知识包术语必须是最多 500 项的数组");
            terminology.forEach(entry -> {
                String canonical = entry.path("canonical").asText("").trim();
                JsonNode aliases = entry.path("aliases");
                if (canonical.isBlank() || canonical.length() > 120 || !aliases.isArray()
                        || aliases.isEmpty() || aliases.size() > 30)
                    throw new IllegalArgumentException("知识包术语必须包含规范词和 1 至 30 个别名");
                aliases.forEach(alias -> {
                    if (alias.asText("").isBlank() || alias.asText().length() > 120)
                        throw new IllegalArgumentException("知识包术语别名无效");
                });
            });
        }
    }
}
