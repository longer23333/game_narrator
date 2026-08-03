package cn.longer233.gamenarrator.vision;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePerceptualHashTest {
    @Test
    void identicalAndDifferentCompositionsHaveExpectedSimilarity() {
        BufferedImage leftBright = splitImage(true);
        BufferedImage same = splitImage(true);
        BufferedImage rightBright = splitImage(false);

        long first = ImagePerceptualHash.differenceHash(leftBright);
        assertThat(ImagePerceptualHash.similarity(first, ImagePerceptualHash.differenceHash(same))).isEqualTo(1.0);
        assertThat(ImagePerceptualHash.similarity(first, ImagePerceptualHash.differenceHash(rightBright))).isLessThan(1.0);
    }

    private BufferedImage splitImage(boolean leftBright) {
        BufferedImage image = new BufferedImage(180, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(leftBright ? Color.WHITE : Color.BLACK);
        graphics.fillRect(0, 0, 90, 100);
        graphics.setColor(leftBright ? Color.BLACK : Color.WHITE);
        graphics.fillRect(90, 0, 90, 100);
        graphics.dispose();
        return image;
    }
}
