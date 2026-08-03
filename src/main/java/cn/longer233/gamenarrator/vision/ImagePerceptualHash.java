package cn.longer233.gamenarrator.vision;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class ImagePerceptualHash {
    private ImagePerceptualHash() {}

    static long differenceHash(BufferedImage source) {
        if (source == null) throw new IllegalArgumentException("图片内容无效");
        BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, 9, 8, null);
        } finally {
            graphics.dispose();
        }
        long hash = 0L;
        int bit = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = scaled.getRaster().getSample(x, y, 0);
                int right = scaled.getRaster().getSample(x + 1, y, 0);
                if (left > right) hash |= 1L << bit;
                bit++;
            }
        }
        return hash;
    }

    static double similarity(long left, long right) {
        return 1.0 - Long.bitCount(left ^ right) / 64.0;
    }
}
