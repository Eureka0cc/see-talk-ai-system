package com.seetalk.service;

import com.seetalk.config.SeeTalkProperties;
import com.seetalk.model.constants.ImageConstants;
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

@Service
public class ImageProcessService {

    private final SeeTalkProperties properties;

    public ImageProcessService(SeeTalkProperties properties) {
        this.properties = properties;
    }

    public byte[] compressBase64Image(String base64Image) {
        byte[] raw = Base64.getDecoder().decode(base64Image);
        return compress(raw);
    }

    public byte[] compress(byte[] raw) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) {
                return raw;
            }

            int maxW = properties.getMaxImageWidth();
            int maxH = properties.getMaxImageHeight();
            int w = img.getWidth();
            int h = img.getHeight();
            double scale = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));

            BufferedImage scaled = img;
            if (scale < 1.0) {
                int newW = (int) (w * scale);
                int newH = (int) (h * scale);
                scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.drawImage(img.getScaledInstance(newW, newH, Image.SCALE_FAST), 0, 0, null);
                g.dispose();
            } else if (scaled.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(scaled, 0, 0, null);
                g.dispose();
                scaled = rgb;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(ImageConstants.JPEG_COMPRESSION_QUALITY);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(scaled, null, null), param);
            }
            writer.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            return raw;
        }
    }
}
