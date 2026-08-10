package cn.longer233.gamenarrator.mobile;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class TimelineViewController {
    public interface Host {
        int selected();
        void selectClip(int index);
        void renderTimeline();
        void pushHistory(String description);
        void persistProject(String reason);
        void setStatus(String text);
        void unavailable(String message);
        boolean editorVisible();
        long currentPosition();
        ExoPlayer player();
        int dp(int value);
    }

    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int BRAND = Color.rgb(255, 229, 72);
    private static final int ACCENT = Color.rgb(255, 79, 163);
    private static final int BLUE = Color.rgb(54, 201, 255);
    private static final int GREEN = Color.rgb(113, 230, 108);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);

    private final Context context;
    private final List<TimelineClip> clips;
    private final MobileProjectStore projectStore;
    private final Host host;
    private final ExecutorService thumbnails;
    private final Handler handler;
    private int timelineZoom = 54;
    private int waveformToken;

    public TimelineViewController(Context context, List<TimelineClip> clips, MobileProjectStore projectStore,
                                  Host host, ExecutorService thumbnails, Handler handler) {
        this.context = context;
        this.clips = clips;
        this.projectStore = projectStore;
        this.host = host;
        this.thumbnails = thumbnails;
        this.handler = handler;
    }

    public int timelineZoom() { return timelineZoom; }
    public void setTimelineZoom(int value) { this.timelineZoom = value; }

    public void render(LinearLayout timeline, TextView ruler, TextView projectSummary, TextView emptyTimeline,
                       WaveformView waveformView, LinearLayout subtitleTrack, LinearLayout narrationTrack,
                       LinearLayout effectTrack, LinearLayout assetTrack) {
        if (timeline == null) return;
        timeline.removeAllViews();
        if (emptyTimeline != null) emptyTimeline.setVisibility(clips.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < clips.size(); i++) {
            TimelineClip clip = clips.get(i);
            ProjectRepository.ClipReviewInfo clipReview = projectStore.clipReview(clip.key());
            final int index = i;
            FrameLayout card = new FrameLayout(context);
            card.setForegroundGravity(Gravity.CENTER);
            card.setBackground(shape(i == host.selected() ? BRAND : SURFACE_HIGH, 10));
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(SURFACE_HIGH);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66));
            imageParams.setMargins(dp(3), dp(3), dp(3), 0);
            card.addView(image, imageParams);
            String reviewMark = "APPROVED".equals(clipReview.status()) ? "✓ " : "NEEDS_CHANGES".equals(clipReview.status()) ? "! " : "";
            TextView caption = label(reviewMark + clip.track() + " " + (i + 1) + "  " + format(clip.durationMs()), 11, TEXT, true);
            caption.setGravity(Gravity.CENTER);
            caption.setBackgroundColor("APPROVED".equals(clipReview.status()) ? Color.argb(225, 30, 160, 90)
                    : "NEEDS_CHANGES".equals(clipReview.status()) ? Color.argb(225, 230, 65, 80) : Color.argb(205, 9, 11, 16));
            card.addView(caption, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(25), Gravity.BOTTOM));
            addTrimHandle(card, true, clip);
            addTrimHandle(card, false, clip);
            float[] touchX = {0};
            card.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) touchX[0] = event.getX();
                return false;
            });
            card.setOnClickListener(v -> {
                host.selectClip(index);
                float ratio = Math.max(0, Math.min(1, touchX[0] / Math.max(1f, v.getWidth())));
                host.player().seekTo(clip.startMs() + (long) (clip.durationMs() * ratio));
                if (waveformView != null) waveformView.setPlayheadRatio(ratio);
                host.setStatus("已选片段 " + (index + 1) + " · 播放头 " + format(host.player().getCurrentPosition()));
            });
            card.setOnLongClickListener(v -> v.startDragAndDrop(ClipData.newPlainText("clip", String.valueOf(index)),
                    new View.DragShadowBuilder(v), index, 0));
            int widthDp = Math.max(42, Math.min(12000, Math.round((clip.durationMs() / 1000f) * timelineZoom)));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(widthDp), dp(92));
            cardParams.setMargins(dp(3), 0, dp(3), 0);
            timeline.addView(card, cardParams);
            loadThumbnail(clip, image);
        }
        if (projectSummary != null) {
            int approved = 0, changes = 0;
            for (TimelineClip clip : clips) {
                String review = projectStore.clipReview(clip.key()).status();
                if ("APPROVED".equals(review)) approved++;
                else if ("NEEDS_CHANGES".equals(review)) changes++;
            }
            projectSummary.setText("本地项目 · " + clips.size() + " 个片段 · " + format(totalDuration())
                    + (approved + changes > 0 ? " · 审阅 ✓" + approved + " / !" + changes : ""));
        }
        if (ruler != null) ruler.setText(buildRuler(totalDuration()));
        renderCreativeTracks(subtitleTrack, narrationTrack, effectTrack, assetTrack);
    }

    private void addTrimHandle(FrameLayout card, final boolean left, final TimelineClip clip) {
        final int handleWidth = dp(20);
        View handle = new View(context);
        handle.setBackgroundColor(Color.argb(170, 9, 11, 16));
        handle.setContentDescription(left ? "拖动调整入点" : "拖动调整出点");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(handleWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = left ? Gravity.LEFT : Gravity.RIGHT;
        handle.setLayoutParams(params);
        final long[] down = new long[3];
        final long[] sourceDuration = {0};
        handle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                down[0] = (long) event.getRawX();
                down[1] = clip.startMs();
                down[2] = clip.endMs();
                sourceDuration[0] = readSourceDuration(clip.uri());
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float deltaPx = event.getRawX() - down[0];
                long deltaMs = Math.round(deltaPx / dp(1) * (1000f / timelineZoom));
                long minGap = 100;
                if (left) {
                    long start = clamp(down[1] + deltaMs, 0, down[2] - minGap);
                    clip.update(start, clip.endMs(), clip.muted(), clip.subtitle());
                } else {
                    long maxEnd = sourceDuration[0] > 0 ? sourceDuration[0] : Long.MAX_VALUE;
                    long end = clamp(down[2] + deltaMs, down[1] + minGap, maxEnd);
                    clip.update(clip.startMs(), end, clip.muted(), clip.subtitle());
                }
                host.setStatus("裁剪中：" + format(clip.startMs()) + " → " + format(clip.endMs()));
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
                if (clip.startMs() != down[1] || clip.endMs() != down[2]) {
                    host.pushHistory(left ? "拖动裁剪入点" : "拖动裁剪出点");
                    host.persistProject("拖动裁剪片段");
                    host.renderTimeline();
                    host.setStatus("已裁剪：" + format(clip.startMs()) + " → " + format(clip.endMs()));
                }
                return true;
            }
            return false;
        });
        card.addView(handle);
    }

    private long readSourceDuration(Uri uri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(context, uri);
                String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                return value == null ? 0 : Long.parseLong(value);
            } finally {
                try { retriever.release(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public void loadWaveform(TimelineClip clip, WaveformView waveformView) {
        if (waveformView == null) return;
        int token = ++waveformToken;
        waveformView.setPoints(new float[0]);
        thumbnails.execute(() -> {
            float[] points = AudioWaveformExtractor.extract(context, clip.uri(), clip.startMs(), clip.endMs(), 160);
            handler.post(() -> {
                if (token == waveformToken && waveformView != null) waveformView.setPoints(points);
            });
        });
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (token != waveformToken || waveformView == null || !host.editorVisible()) return;
                float ratio = (host.currentPosition() - clip.startMs()) / (float) Math.max(1, clip.durationMs());
                waveformView.setPlayheadRatio(ratio);
                handler.postDelayed(this, 100);
            }
        };
        handler.post(ticker);
    }

    public boolean handleTimelineDrag(DragEvent event, ViewGroup timeline) {
        if (!(event.getLocalState() instanceof Integer)) return false;
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) return true;
        if (event.getAction() == DragEvent.ACTION_DROP) {
            int from = (Integer) event.getLocalState();
            if (from < 0 || from >= clips.size()) return false;
            int target = clips.size() - 1;
            for (int i = 0; i < timeline.getChildCount(); i++) {
                View child = timeline.getChildAt(i);
                if (event.getX() < child.getRight()) { target = i; break; }
            }
            if (from != target) {
                host.pushHistory("拖拽移动片段");
                TimelineClip moved = clips.remove(from);
                clips.add(Math.max(0, Math.min(target, clips.size())), moved);
                host.selectClip(clips.indexOf(moved));
                host.persistProject("拖拽移动片段");
                host.setStatus("片段已移动到第 " + (clips.indexOf(moved) + 1) + " 位。");
            }
            return true;
        }
        return event.getAction() != DragEvent.ACTION_DRAG_ENDED || event.getResult();
    }

    private void renderCreativeTracks(LinearLayout subtitleTrack, LinearLayout narrationTrack,
                                      LinearLayout effectTrack, LinearLayout assetTrack) {
        if (subtitleTrack == null) return;
        subtitleTrack.removeAllViews();
        narrationTrack.removeAllViews();
        effectTrack.removeAllViews();
        assetTrack.removeAllViews();
        List<MobileAssetStore.PlacementInfo> placements = projectStore.listPlacements();
        List<ProjectRepository.SubtitleCueInfo> subtitleCues = projectStore.listSubtitleCues();
        long timelineOffsetMs = 0;
        for (TimelineClip clip : clips) {
            StringBuilder timedText = new StringBuilder();
            long clipEnd = timelineOffsetMs + clip.durationMs();
            for (ProjectRepository.SubtitleCueInfo cue : subtitleCues) {
                if (cue.startMs() < clipEnd && cue.endMs() > timelineOffsetMs) {
                    if (timedText.length() > 0) timedText.append(" / ");
                    timedText.append(cue.text().replace('\n', ' '));
                }
            }
            addCreativeBlock(subtitleTrack, timedText.length() > 0 ? timedText.toString() : clip.subtitle(), ACCENT, clip);
            addCreativeBlock(narrationTrack, clip.narration(), BRAND, clip);
            addCreativeBlock(effectTrack, clip.effectCue(), BLUE, clip);
            StringBuilder names = new StringBuilder();
            for (MobileAssetStore.PlacementInfo placement : placements) {
                if (placement.clipKey().equals(clip.key())) {
                    if (names.length() > 0) names.append(" + ");
                    names.append(placement.name());
                }
            }
            addCreativeBlock(assetTrack, names.toString(), GREEN, clip);
            timelineOffsetMs = clipEnd;
        }
    }

    private void addCreativeBlock(LinearLayout lane, String text, int color, TimelineClip clip) {
        TextView block = label(text == null || text.isBlank() ? "—" : text, 10, TEXT, false);
        block.setGravity(Gravity.CENTER_VERTICAL);
        block.setSingleLine(true);
        block.setEllipsize(android.text.TextUtils.TruncateAt.END);
        block.setPadding(dp(5), 0, dp(5), 0);
        block.setBackground(outlined(color, 0));
        int widthDp = Math.max(42, Math.min(12000, Math.round((clip.durationMs() / 1000f) * timelineZoom)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(widthDp), dp(28));
        p.setMargins(dp(3), 0, dp(3), 0);
        lane.addView(block, p);
    }

    private void loadThumbnail(TimelineClip clip, ImageView target) {
        thumbnails.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Bitmap bitmap = null;
            try {
                retriever.setDataSource(context, clip.uri());
                bitmap = retriever.getFrameAtTime((clip.startMs() + clip.durationMs() / 2) * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch (Exception ignored) { }
            finally { try { retriever.release(); } catch (java.io.IOException ignored) { } }
            Bitmap result = bitmap;
            if (result != null) handler.post(() -> target.setImageBitmap(result));
        });
    }

    private long totalDuration() {
        long total = 0;
        for (TimelineClip clip : clips) total += clip.durationMs();
        return total;
    }

    private static String format(long ms) {
        return String.format(Locale.CHINA, "%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
    }

    private static String buildRuler(long total) {
        long step = Math.max(5000, total / 4);
        return "00:00        " + format(step) + "        " + format(step * 2) + "        " + format(step * 3);
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable outlined(int color, int radius) {
        GradientDrawable drawable = shape(color, radius);
        drawable.setStroke(dp(3), TEXT);
        return drawable;
    }

    private int dp(int value) { return host.dp(value); }
}
