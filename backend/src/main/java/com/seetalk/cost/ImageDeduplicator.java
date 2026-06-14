package com.seetalk.cost;

import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

@Component
public class ImageDeduplicator {

    public String computeHash(byte[] imageBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                return "";
            }
            BufferedImage gray = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = gray.createGraphics();
            g.drawImage(img.getScaledInstance(8, 8, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();

            int[] pixels = new int[64];
            gray.getRaster().getPixels(0, 0, 8, 8, pixels);

            long sum = 0;
            for (int pixel : pixels) {
                sum += pixel;
            }
            double avg = sum / 64.0;

            StringBuilder bits = new StringBuilder(64);
            for (int pixel : pixels) {
                bits.append(pixel >= avg ? '1' : '0');
            }
            return bits.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isDuplicate(String previousHash, String currentHash) {
        if (previousHash == null || previousHash.isEmpty() || currentHash.isEmpty()) {
            return false;
        }
        return previousHash.equals(currentHash);
    }
}
