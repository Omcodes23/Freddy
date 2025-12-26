package com.freddy.videostream;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Freddy Video Streamer - Tick-Based Capture
 * NO background threads, NO RenderSystem.recordRenderCall()
 * ClientTickEvents runs on render thread already - safe for OpenGL
 */
public class VideoStreamerMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Freddy Video Streamer");
    private static final int TICKS_PER_CAPTURE = 10; // Capture every 10 ticks = 2 FPS at 20 TPS (smooth gameplay)
    
    private TelemetrySender telemetrySender;
    private FrameCapture frameCapture;
    private int tickCounter = 0;
    
    // Track if we've locked camera to Freddy
    private boolean cameraLockedToFreddy = false;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("[INIT] VideoStreamerMod starting initialization...");
        
        telemetrySender = new TelemetrySender("localhost", 25566);
        telemetrySender.ensureConnected();
        frameCapture = new FrameCapture();
        
        // Register tick-based capture
        // Tick handler runs on render thread already - safe for OpenGL
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        
        LOGGER.info("[INIT] VideoStreamerMod initialization complete with tick-based capture");
    }
    
    /**
     * Called every client tick (20 TPS) - already on render thread
     */
    private void onClientTick(MinecraftClient client) {
        // Check if world is ready
        if (client.world == null) {
            return;
        }
        
        // Find NPC "Freddy" in the world
        net.minecraft.entity.Entity freddyNPC = null;
        for (net.minecraft.entity.Entity entity : client.world.getEntities()) {
            if (entity.getName().getString().equals("Freddy")) {
                freddyNPC = entity;
                break;
            }
        }
        
        if (freddyNPC == null) {
            // No Freddy NPC found
            cameraLockedToFreddy = false;
            return;
        }
        
        // LOCK CAMERA TO FREDDY (if not already locked)
        if (!cameraLockedToFreddy) {
            LOGGER.info("[INIT] Locking camera to Freddy's POV permanently");
            client.setCameraEntity(freddyNPC);
            cameraLockedToFreddy = true;
        }
        
        // Capture frames periodically
        tickCounter++;
        if (tickCounter < TICKS_PER_CAPTURE) {
            return;
        }
        tickCounter = 0;
        
        LOGGER.info("[TICK] Capturing frame from Freddy POV");
        
        // Capture from Freddy's perspective (camera already on him)
        byte[] frame = frameCapture.captureFrame(client);
        if (frame != null && frame.length > 0) {
            telemetrySender.sendVideoFrame("VIDEO_FP:Freddy:", frame);
            telemetrySender.sendVideoFrame("VIDEO_3P:Freddy:", frame);
            telemetrySender.sendVideoFrame("VIDEO_MAP:Freddy:", frame);
            telemetrySender.sendVideoFrame("VIDEO_REPLAY:Freddy:", frame);
            LOGGER.info("[TICK] Sent 4 frames from Freddy POV, {} bytes each", frame.length);
        } else {
            LOGGER.warn("[TICK] Frame capture returned null or empty");
        }
    }
}
