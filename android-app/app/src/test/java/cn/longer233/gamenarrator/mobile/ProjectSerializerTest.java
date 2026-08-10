package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class ProjectSerializerTest {
    @Test public void parsesClipJsonIntoPlainRow() throws Exception {
        JSONObject json = new JSONObject();
        json.put("key", "k1");
        json.put("uri", "content://media/1");
        json.put("name", "片段一");
        json.put("startMs", 0);
        json.put("endMs", 1000);
        json.put("muted", false);
        json.put("subtitle", "字幕");
        json.put("narration", "解说");
        json.put("effectCue", "特效");
        json.put("track", "A1");

        ProjectSerializer.ClipRow row = ProjectSerializer.clipRow(json, 3);
        assertEquals("k1", row.key());
        assertEquals("content://media/1", row.uri());
        assertEquals("片段一", row.name());
        assertEquals(3, row.position());
        assertEquals(0L, row.startMs());
        assertEquals(1000L, row.endMs());
        assertEquals("字幕", row.subtitle());
        assertEquals("解说", row.narration());
        assertEquals("特效", row.effectCue());
        assertEquals("A1", row.track());
    }

    @Test public void rejectsInvalidClipDuration() throws Exception {
        JSONObject invalid = new JSONObject();
        invalid.put("startMs", 500);
        invalid.put("endMs", 500);
        invalid.put("uri", "content://media/1");
        assertThrows(IllegalArgumentException.class, () -> ProjectSerializer.clipRow(invalid, 0));
    }

    @Test public void parsesArchiveAndRejectsBadFormat() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "GameNarratorAndroidProject");
        root.put("schemaVersion", 1);
        root.put("timeline", new JSONArray());
        JSONObject parsed = ProjectSerializer.parse(root.toString());
        assertEquals("GameNarratorAndroidProject", parsed.getString("format"));

        JSONObject bad = new JSONObject();
        bad.put("format", "Other");
        bad.put("timeline", new JSONArray());
        assertThrows(IllegalArgumentException.class, () -> ProjectSerializer.parse(bad.toString()));
    }

    @Test public void projectJsonContainsAllSections() throws Exception {
        List<TimelineClip> timeline = new ArrayList<>();
        List<ProjectRepository.SubtitleCueInfo> subtitles = new ArrayList<>();
        subtitles.add(new ProjectRepository.SubtitleCueInfo(1, 0, 500, "你好"));
        List<ProjectRepository.ClipReviewInfo> reviews = new ArrayList<>();
        reviews.add(new ProjectRepository.ClipReviewInfo("k1", "APPROVED", "", 123));
        List<ProjectSerializer.RevisionArchive> revisions = new ArrayList<>();
        revisions.add(new ProjectSerializer.RevisionArchive(456, "起点", timeline));
        Map<String, ProjectRepository.TrackState> tracks = new LinkedHashMap<>();
        tracks.put("ORIGINAL", new ProjectRepository.TrackState(false, false));

        JSONObject json = ProjectSerializer.projectJson("项目", timeline, subtitles, reviews, revisions, tracks);
        assertEquals("GameNarratorAndroidProject", json.getString("format"));
        assertEquals(0, json.getJSONArray("timeline").length());
        assertEquals(1, json.getJSONArray("subtitleCues").length());
        assertEquals(1, json.getJSONArray("clipReviews").length());
        assertEquals(1, json.getJSONArray("revisions").length());
        assertEquals(0, json.getJSONArray("revisions").getJSONObject(0).getJSONArray("clips").length());
        assertEquals(1, json.getJSONObject("trackStates").length());
    }

    @Test public void effectTemplatesRoundTripJson() throws Exception {
        List<ProjectRepository.EffectTemplateInfo> templates = new ArrayList<>();
        templates.add(new ProjectRepository.EffectTemplateInfo(1, "高光", "FOCUS,FLASH", 123));
        String json = ProjectSerializer.templatesToJson(templates);
        List<ProjectSerializer.TemplateDraft> drafts = ProjectSerializer.templatesFromJson(json);
        assertEquals(1, drafts.size());
        assertEquals("高光", drafts.get(0).name());
        assertEquals("FOCUS,FLASH", drafts.get(0).cue());
    }
}
