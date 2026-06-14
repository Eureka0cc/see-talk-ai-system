package com.seetalk.rate;

import com.seetalk.model.constants.ImageConstants;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

@Component
public class ImageDeduplicator {

    private static final int DEFAULT_SCENE_CHANGE_DISTANCE = 8;

    public String computeHash(byte[] imageBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                return "";
            }
            int size = ImageConstants.PHASH_GRID_SIZE;
            BufferedImage gray = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = gray.createGraphics();
            g.drawImage(img.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();

            int[] pixels = new int[ImageConstants.PHASH_PIXEL_COUNT];
            gray.getRaster().getPixels(0, 0, size, size, pixels);

            long sum = 0;
            for (int pixel : pixels) {
                sum += pixel;
            }
            double avg = sum / (double) ImageConstants.PHASH_PIXEL_COUNT;

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

    public int hammingDistance(String previousHash, String currentHash) {
        if (previousHash == null || currentHash == null || previousHash.isEmpty() || currentHash.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        if (previousHash.length() != currentHash.length()) {
            return Integer.MAX_VALUE;
        }
        int distance = 0;
        for (int i = 0; i < previousHash.length(); i++) {
            if (previousHash.charAt(i) != currentHash.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }

    public boolean isNewScene(String previousHash, String currentHash) {
        return hammingDistance(previousHash, currentHash) > DEFAULT_SCENE_CHANGE_DISTANCE;
    }
}
