package com.bb.progress.photo;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExifOrientationTest {

    /** A baseline JPEG with an APP1 Exif segment declaring the given orientation. */
    private static byte[] jpegWithOrientation(int orientation) throws IOException {
        ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", jpegOut);
        byte[] jpeg = jpegOut.toByteArray();

        // Little-endian TIFF header + one IFD entry (tag 0x0112, SHORT, orientation).
        byte[] exif = new byte[] {
            'E', 'x', 'i', 'f', 0, 0,
            'I', 'I', 0x2A, 0x00,
            0x08, 0x00, 0x00, 0x00,          // IFD0 at offset 8
            0x01, 0x00,                      // one entry
            0x12, 0x01,                      // tag 0x0112
            0x03, 0x00,                      // type SHORT
            0x01, 0x00, 0x00, 0x00,          // count 1
            (byte) orientation, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,          // next IFD: none
        };
        int segmentLength = exif.length + 2;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write((segmentLength >> 8) & 0xFF);
        out.write(segmentLength & 0xFF);
        out.write(exif);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 6, 8})
    void readsTheOrientationTag(int orientation) throws IOException {
        assertThat(ExifOrientation.read(jpegWithOrientation(orientation))).isEqualTo(orientation);
    }

    @Test
    void defaultsToNormalWhenThereIsNoExifSegment() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "jpeg", out);

        assertThat(ExifOrientation.read(out.toByteArray())).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(new byte[] {1, 2, 3})).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void rotatingByNinetyDegreesSwapsTheAxesAndMovesThePixel() {
        // A landscape image with one red pixel at the top-left.
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());

        BufferedImage rotated = ExifOrientation.apply(image, 6);

        assertThat(rotated.getWidth()).isEqualTo(2);
        assertThat(rotated.getHeight()).isEqualTo(4);
        // Rotating clockwise sends the top-left pixel to the top-right.
        assertThat(new Color(rotated.getRGB(1, 0))).isEqualTo(Color.RED);
    }

    @Test
    void rotatingByOneHundredEightyKeepsTheShapeAndMovesThePixelOpposite() {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());

        BufferedImage rotated = ExifOrientation.apply(image, 3);

        assertThat(rotated.getWidth()).isEqualTo(4);
        assertThat(rotated.getHeight()).isEqualTo(2);
        assertThat(new Color(rotated.getRGB(3, 1))).isEqualTo(Color.RED);
    }

    @Test
    void leavesAnUprightImageUntouched() {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        assertThat(ExifOrientation.apply(image, ExifOrientation.NORMAL)).isSameAs(image);
    }
}
