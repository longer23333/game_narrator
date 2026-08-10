package cn.longer233.gamenarrator.event;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge-packs")
public class GameKnowledgePackController {
    private final GameKnowledgePackService service;
    public GameKnowledgePackController(GameKnowledgePackService service) { this.service = service; }
    @GetMapping public List<KnowledgePackView> list() { return service.list(); }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public BossBattleKnowledgePack importPack(@RequestBody JsonNode body) { return service.importPack(body, false); }
    @GetMapping(value = "/{code}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> export(@PathVariable String code) throws Exception {
        byte[] content = service.exportPack(code).toPrettyString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(code + ".json", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_JSON).body(content);
    }
}
