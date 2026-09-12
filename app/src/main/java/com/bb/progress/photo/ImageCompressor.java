package com.bb.progress.photo;

import com.bb.progress.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;

/**
 * Re-encodes uploaded photos to a JPEG that fits a size budget, so a multi-megabyte phone
 * photo can be accepted and still stored small. Progressively drops quality, then resolution,
 * stopping at the first encoding that fits.
 */
@Service
public class ImageCompressor {

    public static final long ONE_MEGABYTE = 1_048_576L;

    /** Largest edge to try, in order. The first value keeps plenty of detail for viewing. */
    private static final int[] MAX_EDGES = {2560, 2048, 1600, 1280, 1024, 800};
    private static final float[] QUALITIES = {0.85f, 0.72f, 0.6f, 0.5f};

    public byte[] compress(byte[] source, long maxBytes) {
        BufferedImage image = decode(source);
        image = ExifOrientation.apply(image, ExifOrientation.read(source));

        byte[] smallest = null;
        for (int maxEdge : MAX_EDGES) {
            BufferedImage scaled = scaleToFit(image, maxEdge);
            for (float quality : QUALITIES) {
                byte[] encoded = encodeJpeg(scaled, quality);
                if (encoded.length <= maxBytes) {
                    return encoded;
                }
                if (smallest == null || encoded.length < smallest.length) {
                    smallest = encoded;
                }
            }
        }
        // Every attempt overshot (pathological input); keep the smallest rather than failing.
        return smallest;
    }

    private BufferedImage decode(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(source));
            if (image == null) {
                throw ApiException.badRequest("IMAGE_UNREADABLE",
                        "The image could not be read; please try a JPEG or PNG");
            }
            return image;
        } catch (IOException e) {
            throw ApiException.badRequest("IMAGE_UNREADABLE",
                    "The image could not be read; please try a JPEG or PNG");
        }
    }

    private BufferedImage scaleToFit(BufferedImage image, int maxEdge) {
        int width = image.getWidth();
        int height = image.getHeight();
        double factor = Math.min(1.0, (double) maxEdge / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));

        // JPEG has no alpha channel: flatten onto white so PNGs keep sane colours.
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetWidth, targetHeight);
        g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return result;
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), params);
            stream.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode JPEG", e);
        } finally {
            writer.dispose();
        }
    }
}
