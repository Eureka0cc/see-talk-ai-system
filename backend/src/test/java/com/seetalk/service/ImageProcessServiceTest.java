package com.seetalk.service;

import com.seetalk.config.SeeTalkProperties;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessServiceTest {

    @Test
    void compressScalesDownLargeImage() throws Exception {
        SeeTalkProperties properties = new SeeTalkProperties();
        properties.setMaxImageWidth(640);
        properties.setMaxImageHeight(480);
        ImageProcessService service = new ImageProcessService(properties);

        BufferedImage image = new BufferedImage(1280, 960, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 1280, 960);
        g.dispose();

        ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
        ImageIO.write(image, "png", rawOut);
        byte[] raw = rawOut.toByteArray();

        byte[] compressed = service.compress(raw);
        BufferedImage result = ImageIO.read(new java.io.ByteArrayInputStream(compressed));
        assertTrue(result.getWidth() <= 640);
        assertTrue(result.getHeight() <= 480);
    }
}
