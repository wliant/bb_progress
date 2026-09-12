package com.bb.progress.photo;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;

/**
 * Phone cameras record which way up the sensor was rather than rotating the pixels, so a
 * portrait photo re-encoded without applying that tag comes out sideways. ImageIO does not
 * read EXIF, so the orientation tag is pulled straight out of the JPEG APP1 segment.
 */
public final class ExifOrientation {

    public static final int NORMAL = 1;

    private ExifOrientation() {
    }

    /** Returns the EXIF orientation (1–8), or {@link #NORMAL} when absent or unreadable. */
    public static int read(byte[] jpeg) {
        // JPEG SOI
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return NORMAL;
        }
        int offset = 2;
        while (offset + 4 <= jpeg.length) {
            if ((jpeg[offset] & 0xFF) != 0xFF) {
                return NORMAL;
            }
            int marker = jpeg[offset + 1] & 0xFF;
            // Standalone markers carry no length.
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                offset += 2;
                continue;
            }
            // Start of scan: image data follows, no more metadata worth scanning.
            if (marker == 0xDA || marker == 0xD9) {
                return NORMAL;
            }
            int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            if (length < 2 || offset + 2 + length > jpeg.length) {
                return NORMAL;
            }
            if (marker == 0xE1 && isExifHeader(jpeg, offset + 4)) {
                return readFromTiff(jpeg, offset + 10, offset + 2 + length);
            }
            offset += 2 + length;
        }
        return NORMAL;
    }

    private static boolean isExifHeader(byte[] data, int at) {
        return at + 6 <= data.length
                && data[at] == 'E' && data[at + 1] == 'x' && data[at + 2] == 'i' && data[at + 3] == 'f'
                && data[at + 4] == 0 && data[at + 5] == 0;
    }

    private static int readFromTiff(byte[] data, int tiffStart, int limit) {
        if (tiffStart + 8 > limit) {
            return NORMAL;
        }
        boolean bigEndian;
        if (data[tiffStart] == 'M' && data[tiffStart + 1] == 'M') {
            bigEndian = true;
        } else if (data[tiffStart] == 'I' && data[tiffStart + 1] == 'I') {
            bigEndian = false;
        } else {
            return NORMAL;
        }
        if (readShort(data, tiffStart + 2, bigEndian) != 0x2A) {
            return NORMAL;
        }
        int ifdOffset = (int) readInt(data, tiffStart + 4, bigEndian);
        int ifd = tiffStart + ifdOffset;
        if (ifd + 2 > limit) {
            return NORMAL;
        }
        int entries = readShort(data, ifd, bigEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + (i * 12);
            if (entry + 12 > limit) {
                return NORMAL;
            }
            if (readShort(data, entry, bigEndian) == 0x0112) {
                int value = readShort(data, entry + 8, bigEndian);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int readShort(byte[] data, int at, boolean bigEndian) {
        int a = data[at] & 0xFF;
        int b = data[at + 1] & 0xFF;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    private static long readInt(byte[] data, int at, boolean bigEndian) {
        long a = data[at] & 0xFFL;
        long b = data[at + 1] & 0xFFL;
        long c = data[at + 2] & 0xFFL;
        long d = data[at + 3] & 0xFFL;
        return bigEndian ? (a << 24) | (b << 16) | (c << 8) | d : (d << 24) | (c << 16) | (b << 8) | a;
    }

    /** Returns the image rotated/flipped so that it displays upright without the tag. */
    public static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation <= NORMAL || orientation > 8) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean swapsAxes = orientation >= 5;
        int targetWidth = swapsAxes ? height : width;
        int targetHeight = swapsAxes ? width : height;

        AffineTransform transform = switch (orientation) {
            case 2 -> flipHorizontal(width);
            case 3 -> rotate(Math.PI, width, height);
            case 4 -> flipVertical(height);
            case 5 -> transpose();
            case 6 -> rotate90(height);
            case 7 -> transverse(width, height);
            case 8 -> rotate270(width);
            default -> new AffineTransform();
        };

        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(image, transform, null);
        g.dispose();
        return result;
    }

    private static AffineTransform flipHorizontal(int width) {
        AffineTransform t = AffineTransform.getScaleInstance(-1, 1);
        t.translate(-width, 0);
        return t;
    }

    private static AffineTransform flipVertical(int height) {
        AffineTransform t = AffineTransform.getScaleInstance(1, -1);
        t.translate(0, -height);
        return t;
    }

    private static AffineTransform rotate(double radians, int width, int height) {
        AffineTransform t = AffineTransform.getTranslateInstance(width, height);
        t.rotate(radians);
        return t;
    }

    private static AffineTransform transpose() {
        AffineTransform t = AffineTransform.getRotateInstance(Math.PI / 2);
        t.scale(1, -1);
        return t;
    }

    private static AffineTransform rotate90(int height) {
        AffineTransform t = AffineTransform.getTranslateInstance(height, 0);
        t.rotate(Math.PI / 2);
        return t;
    }

    private static AffineTransform transverse(int width, int height) {
        AffineTransform t = new AffineTransform(0, -1, -1, 0, height, width);
        return t;
    }

    private static AffineTransform rotate270(int width) {
        AffineTransform t = AffineTransform.getTranslateInstance(0, width);
        t.rotate(3 * Math.PI / 2);
        return t;
    }
}
