package com.bb.progress.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bb.progress.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageCompressorTest {

    private final ImageCompressor compressor = new ImageCompressor();

    /** Noise does not compress well, so this is close to a worst case for the size budget. */
    private static byte[] noisyPhoto(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void bringsAPhoneSizedPhotoUnderOneMegabyte() throws IOException {
        // 4032x3024 is a full-resolution phone capture.
        byte[] source = noisyPhoto(4032, 3024, "png");
        assertThat(source.length).isGreaterThan((int) ImageCompressor.ONE_MEGABYTE);

        byte[] compressed = compressor.compress(source, ImageCompressor.ONE_MEGABYTE);

        assertThat(compressed.length).isLessThanOrEqualTo((int) ImageCompressor.ONE_MEGABYTE);
        assertThat(decode(compressed)).isNotNull();
    }

    @Test
    void keepsTheAspectRatioWhileScalingDown() throws IOException {
        byte[] compressed = compressor.compress(noisyPhoto(4000, 2000, "png"), ImageCompressor.ONE_MEGABYTE);

        BufferedImage image = decode(compressed);
        assertThat((double) image.getWidth() / image.getHeight()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.02));
        assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(2560);
    }

    @Test
    void leavesASmallImageAtItsOriginalDimensions() throws IOException {
        byte[] compressed = compressor.compress(noisyPhoto(320, 240, "png"), ImageCompressor.ONE_MEGABYTE);

        BufferedImage image = decode(compressed);
        assertThat(image.getWidth()).isEqualTo(320);
        assertThat(image.getHeight()).isEqualTo(240);
    }

    @Test
    void flattensTransparencyInsteadOfProducingBlackAreas() throws IOException {
        BufferedImage withAlpha = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = withAlpha.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, 50, 50);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(withAlpha, "png", out);

        BufferedImage result = decode(compressor.compress(out.toByteArray(), ImageCompressor.ONE_MEGABYTE));

        assertThat(new Color(result.getRGB(25, 25))).isEqualTo(Color.WHITE);
    }

    @Test
    void rejectsBytesThatAreNotAnImage() {
        assertThatThrownBy(() -> compressor.compress("not an image".getBytes(), ImageCompressor.ONE_MEGABYTE))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_UNREADABLE");
    }
}
