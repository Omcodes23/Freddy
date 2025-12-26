package com.freddy.videostream;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class FrameCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("FrameCapture");
    private static final int TARGET_WIDTH = 480;
    private static final int TARGET_HEIGHT = 360;

    public byte[] captureFrame(MinecraftClient client) {
        try {
            Framebuffer framebuffer = client.getFramebuffer();
            if (framebuffer == null) {
                LOGGER.warn("Framebuffer is null");
                return null;
            }
            
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;
            if (width <= 0 || height <= 0) {
                LOGGER.warn("Invalid framebuffer size: {}x{}", width, height);
                return null;
            }

            // Read pixels directly from the screen using OpenGL
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            
            // Convert ByteBuffer to BufferedImage
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (x + y * width) * 4;
                    int r = buffer.get(i) & 0xFF;
                    int g = buffer.get(i + 1) & 0xFF;
                    int b = buffer.get(i + 2) & 0xFF;
                    // Flip Y coordinate (OpenGL bottom-left origin)
                    image.setRGB(x, height - y - 1, (r << 16) | (g << 8) | b);
                }
            }

            // Resize to target dimensions
            BufferedImage resized = resizeImage(image, TARGET_WIDTH, TARGET_HEIGHT);

            // Encode as JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            return baos.toByteArray();
        } catch (Throwable e) {
            LOGGER.error("Frame capture error: {}", e.getMessage(), e);
            return null;
        }
    }

    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        resized.createGraphics().drawImage(
            original.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH),
            0, 0, null
        );
        return resized;
    }
}
