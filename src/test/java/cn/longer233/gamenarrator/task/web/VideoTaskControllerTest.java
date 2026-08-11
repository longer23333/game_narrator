package cn.longer233.gamenarrator.task.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.longer233.gamenarrator.pipeline.VideoTaskEngine;
import cn.longer233.gamenarrator.pipeline.PipelineRunTracker;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:controller-test",
        "game-narrator.storage-root=./target/test-storage",
        "game-narrator.media-import.yt-dlp=./mvnw.cmd"
})
@AutoConfigureMockMvc
class VideoTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PipelineRunTracker pipelineRunTracker;

    @MockBean
    private VideoTaskEngine videoTaskEngine;

    @Test
    void emptyAssetCatalogCanBeListed() throws Exception {
        mockMvc.perform(get("/api/assets").param("query", "悬疑"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void publicAssetDiscoveryRejectsInvalidPageBeforeCallingProviders() throws Exception {
        mockMvc.perform(post("/api/assets/discover")
                        .contentType("application/json")
                        .content("""
                                {"query":"battle","assetType":"VIDEO","pageSize":12,"page":0,
                                 "commercialUse":true,"allowModification":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicAssetDiscoveryRejectsUnknownProviderBeforeCallingProviders() throws Exception {
        mockMvc.perform(post("/api/assets/discover")
                        .contentType("application/json")
                        .content("""
                                {"query":"battle","assetType":"VIDEO","pageSize":12,"page":1,
                                 "commercialUse":true,"allowModification":true,"provider":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicAssetDiscoveryRejectsUnknownSortBeforeCallingProviders() throws Exception {
        mockMvc.perform(post("/api/assets/discover")
                        .contentType("application/json")
                        .content("""
                                {"query":"battle","assetType":"VIDEO","pageSize":12,"page":1,
                                 "commercialUse":true,"allowModification":true,"provider":"BILIBILI",
                                 "sort":"RANDOM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mediaImporterReportsInstalledRuntime() throws Exception {
        mockMvc.perform(get("/api/media-import/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void oversizedSegmentSearchImageIsRejectedBeforeHeapCopy() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "huge.png", "image/png",
                new byte[10 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/video-segments/search-image").file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void browserStyleMultipartFormCreatesTask() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "boss-fight.mp4",
                "video/mp4",
                "fake-video-for-controller-test".getBytes()
        );

        var result = mockMvc.perform(multipart("/api/tasks")
                        .file(video)
                        .characterEncoding("UTF-8")
                        .param("name", "Boss 战高光")
                        .param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90")
                        .param("taskBrief", "突出闪避、反击和阶段转换"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.name").value("Boss 战高光"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.cloudVisionEnabled").value(false))
                .andExpect(jsonPath("$.aiScriptEnabled").value(false))
                .andExpect(jsonPath("$.aiVoiceEnabled").value(false))
                .andExpect(jsonPath("$.autoAssetsEnabled").value(false))
                .andExpect(jsonPath("$.stages.length()").value(9))
                .andReturn();

        String taskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video_project WHERE id=?",
                Integer.class, UUID.fromString(taskId)));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_revision revision
                JOIN video_project project ON project.current_revision_id=revision.id
                WHERE project.id=? AND revision.project_id=project.id
                """, Integer.class, UUID.fromString(taskId)));
        mockMvc.perform(get("/api/tasks/{id}/source", taskId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=source-" + taskId))
                .andExpect(content().bytes("fake-video-for-controller-test".getBytes()));
    }

    @Test
    void invalidVideoReturnsReadableErrorAndTraceId() throws Exception {
        MockMultipartFile invalidVideo = new MockMultipartFile(
                "video",
                "notes.txt",
                "text/plain",
                "not-a-video".getBytes()
        );

        mockMvc.perform(multipart("/api/tasks")
                        .file(invalidVideo)
                        .characterEncoding("UTF-8")
                        .param("name", "错误样例")
                        .param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90")
                        .param("taskBrief", "验证错误输出"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("仅支持 mp4、mov、mkv、webm 视频"))
                .andExpect(jsonPath("$.suggestion").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void taskCanBeMovedToTrashRestoredAndPermanentlyDeleted() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "delete-me.mp4", "video/mp4",
                "temporary-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "待删除任务").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "删除测试"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());
        Path taskDirectory = Path.of("target/test-storage/tasks").resolve(id.toString());
        Files.createDirectories(taskDirectory.resolve("render-work"));
        Files.writeString(taskDirectory.resolve("render-work/temporary.mp4"), "temporary");

        mockMvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video_tasks WHERE id=? AND deleted_at IS NOT NULL",
                Integer.class, id));
        assertEquals(true, Files.exists(taskDirectory));
        mockMvc.perform(get("/api/tasks/trash")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));

        mockMvc.perform(post("/api/tasks/trash/{id}/restore",id)).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM video_tasks WHERE id=? AND deleted_at IS NOT NULL",Integer.class,id));

        mockMvc.perform(delete("/api/tasks/{id}",id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/tasks/trash/{id}",id)).andExpect(status().isNoContent());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM video_tasks WHERE id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM processing_stages WHERE task_id=?",Integer.class,id));
        assertEquals(false,Files.exists(taskDirectory));
    }

    @Test
    void taskRenameAlsoUpdatesItsProjectName() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "rename.mp4", "video/mp4",
                "temporary-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "旧任务名").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "重命名测试"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());

        mockMvc.perform(patch("/api/tasks/{id}/name", id)
                        .contentType("application/json").content("{\"name\":\" 新任务名 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新任务名"));

        assertEquals("新任务名", jdbc.queryForObject("SELECT name FROM video_project WHERE id=?",
                String.class, id));
    }

    @Test
    void pipelineStagesAreMirroredIntoVersionedRunTables() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "tracked.mp4", "video/mp4",
                "tracked-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "运行留痕").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "阶段记录测试"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());

        pipelineRunTracker.running(id, "VIDEO_INGESTION");
        pipelineRunTracker.completed(id, "VIDEO_INGESTION", java.util.Map.of("durationSeconds", 12));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM generation_run WHERE project_id=?",
                Integer.class, id));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM stage_run WHERE generation_run_id=(SELECT latest_run_id FROM video_project WHERE id=?)",
                String.class, id));
    }

    @Test
    void taskStatusConstraintAllowsStoryboardReviewWaitingState() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "review.mp4", "video/mp4",
                "review-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "Storyboard review").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "Review generated storyboard"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());

        assertEquals(1, jdbc.update("UPDATE video_tasks SET status='WAITING_REVIEW' WHERE id=?", id));
        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT status FROM video_tasks WHERE id=?", String.class, id));
    }

    @Test
    void editorHistoryKeepsMoreThanFiftyPersistentUndoSteps() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "history.mp4", "video/mp4", "history-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "五十步历史").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "验证持久化撤销树"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());

        for (int index = 0; index < 55; index++) {
            mockMvc.perform(post("/api/tasks/{id}/editor/commands", id).contentType("application/json")
                            .content("{\"type\":\"TRACK_STATE\",\"payload\":{\"trackId\":\"audio-1\",\"muted\":" + (index % 2 == 0) + ",\"solo\":false}}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/tasks/{id}/editor", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.history.revisionCount").value(56))
                .andExpect(jsonPath("$.history.canUndo").value(true));
        mockMvc.perform(post("/api/tasks/{id}/editor/commands", id).contentType("application/json")
                        .content("{\"type\":\"UNDO\",\"payload\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.canRedo").value(true));
    }

    @Test
    void projectRevisionTreeCanBeListedNamedAndCheckedOut() throws Exception {
        var created = mockMvc.perform(multipart("/api/tasks").file(new MockMultipartFile(
                        "video", "versions.mp4", "video/mp4", "version-video".getBytes()))
                        .param("name", "版本树").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER").param("targetDurationSeconds", "90")
                        .param("taskBrief", "版本树测试"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
        String revisions = mockMvc.perform(get("/api/tasks/{id}/editor/revisions", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].current").value(true)).andReturn()
                .getResponse().getContentAsString();
        UUID revisionId = UUID.fromString(objectMapper.readTree(revisions).get(0).path("id").asText());
        Long versionBeforeCheckout = jdbc.queryForObject(
                "SELECT version FROM video_project WHERE id=?", Long.class, id);

        mockMvc.perform(patch("/api/tasks/{id}/editor/revisions/{revisionId}", id, revisionId)
                        .contentType("application/json").content("{\"label\":\"初始剪辑方案\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("初始剪辑方案"));
        mockMvc.perform(post("/api/tasks/{id}/editor/revisions/{revisionId}/checkout", id, revisionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.revisionCount").value(1));
        assertEquals(versionBeforeCheckout + 1, jdbc.queryForObject(
                "SELECT version FROM video_project WHERE id=?", Long.class, id));
    }

}
