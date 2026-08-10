package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

final class FakeProjectState implements ProjectState {
    final List<TimelineClip> clips;
    final List<SubtitleFileCodec.Cue> subtitles = new ArrayList<>();
    final List<ProjectSnapshot.Placement> placements = new ArrayList<>();
    final List<ProjectSnapshot.Track> tracks = new ArrayList<>();
    final List<ProjectSnapshot.Keyframe> keyframes = new ArrayList<>();
    final List<ProjectSnapshot.AudioConfig> audioConfigs = new ArrayList<>();
    final List<ProjectSnapshot.VisualConfig> visualConfigs = new ArrayList<>();
    final List<ProjectSnapshot.Review> reviews = new ArrayList<>();
    final List<ProjectSnapshot.Voice> voices = new ArrayList<>();

    FakeProjectState(List<TimelineClip> clips) {
        this.clips = clips;
    }

    @Override public ProjectSnapshot capture() {
        List<TimelineClip> copy = new ArrayList<>();
        for (TimelineClip clip : clips) copy.add(new TimelineClip(clip));
        return new ProjectSnapshot(copy, new ArrayList<>(subtitles), new ArrayList<>(placements),
                new ArrayList<>(tracks), new ArrayList<>(keyframes), new ArrayList<>(audioConfigs),
                new ArrayList<>(visualConfigs), new ArrayList<>(reviews), new ArrayList<>(voices));
    }

    @Override public void restore(ProjectSnapshot snapshot) {
        clips.clear();
        clips.addAll(snapshot.clips());
        subtitles.clear();
        subtitles.addAll(snapshot.subtitles());
        placements.clear();
        placements.addAll(snapshot.placements());
        tracks.clear();
        tracks.addAll(snapshot.tracks());
        keyframes.clear();
        keyframes.addAll(snapshot.keyframes());
        audioConfigs.clear();
        audioConfigs.addAll(snapshot.audioConfigs());
        visualConfigs.clear();
        visualConfigs.addAll(snapshot.visualConfigs());
        reviews.clear();
        reviews.addAll(snapshot.reviews());
        voices.clear();
        voices.addAll(snapshot.voices());
    }
}
