package cn.longer233.gamenarrator.mobile;

public final class CrossfadeCurve {
    private CrossfadeCurve() { }

    public static float alpha(long presentationTimeUs, long fadeStartUs, long fadeDurationUs) {
        if (fadeDurationUs <= 0) return 1f;
        float progress = Math.max(0f, Math.min(1f, (presentationTimeUs - fadeStartUs) / (float) fadeDurationUs));
        return progress * progress * (3f - 2f * progress);
    }
}
