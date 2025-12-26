package com.freddy.videostream;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static helper class for video capture
 * Completely independent - no instance references
 */
public class VideoCaptureHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("Freddy Video Streamer");
    private static FrameCapture frameCapture;
    private static TelemetrySender telemetrySender;
    
    public static void initialize(FrameCapture fc, TelemetrySender ts) {
        frameCapture = fc;
        telemetrySender = ts;
        LOGGER.info("[HELPER] VideoCaptureHelper initialized");
    }
    
    /**
     * Schedule a capture on the render thread
     * Call this from background thread
     */
    public static void scheduleCaptureOnRenderThread(MinecraftClient client) {
        // Pass client as effectively final variable
        final MinecraftClient captureClient = client;
        
        // This lambda is in a static context - no 'this' to capture!
        RenderSystem.recordRenderCall(() -> {
            performCapture(captureClient);
        });
    }
    
    /**
     * Perform the actual capture - runs on render thread
     */
    private static void performCapture(MinecraftClient client) {
        if (frameCapture == null || telemetrySender == null) {
            LOGGER.error("[HELPER] Not initialized!");
            return;
        }
        
        try {
            LOGGER.info("[CAPTURE] Starting frame capture on render thread");
            byte[] frame = frameCapture.captureFrame(client);
            if (frame != null && frame.length > 0) {
                LOGGER.info("[CAPTURE] Frame captured: {} bytes", frame.length);
                telemetrySender.sendVideoFrame("VIDEO_FP:Player:", frame);
                telemetrySender.sendVideoFrame("VIDEO_3P:Player:", frame);
                telemetrySender.sendVideoFrame("VIDEO_MAP:Player:", frame);
                telemetrySender.sendVideoFrame("VIDEO_REPLAY:Player:", frame);
                LOGGER.info("[SEND] Sent all 4 views");
            } else {
                LOGGER.warn("[CAPTURE] Frame is null or empty!");
            }
        } catch (Exception e) {
            LOGGER.error("[CAPTURE] Failed: {}", e.getMessage(), e);
        }
    }
}
