package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

final class FakeProjectStore implements ProjectStore {
    final List<TimelineClip> lastSavedClips = new ArrayList<>();
    final List<MobileExportStore.ExportJobInfo> jobs = new ArrayList<>();
    final List<TimelineClip> storedClips = new ArrayList<>();
    boolean imported;

    @Override public void saveClips(List<TimelineClip> clips, String reason) {
        lastSavedClips.clear();
        for (TimelineClip clip : clips) {
            lastSavedClips.add(new TimelineClip(clip));
        }
    }

    @Override public List<MobileExportStore.ExportJobInfo> listExports() {
        return jobs;
    }

    @Override public List<TimelineClip> loadClips() {
        List<TimelineClip> out = new ArrayList<>();
        for (TimelineClip clip : storedClips) out.add(new TimelineClip(clip));
        return out;
    }

    @Override public String exportActiveProject() throws Exception {
        return "{\"format\":\"GameNarratorAndroidProject\"}";
    }

    @Override public long importProject(String json) throws Exception {
        imported = true;
        return 1;
    }
}
