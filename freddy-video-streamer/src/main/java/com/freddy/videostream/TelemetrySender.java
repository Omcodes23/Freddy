package com.freddy.videostream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

public class TelemetrySender {
    private static final Logger LOGGER = LoggerFactory.getLogger("TelemetrySender");
    
    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter writer;
    private long lastAttemptMs = 0L;
    private static final long RETRY_COOLDOWN_MS = 2000;
    private final AtomicLong frameCounter = new AtomicLong(0);
    
    public TelemetrySender(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public void connect() throws IOException {
        socket = new Socket(host, port);
        writer = new PrintWriter(socket.getOutputStream(), true);
        LOGGER.info("Connected to {}:{}", host, port);
    }

    private boolean shouldAttemptNow() {
        long now = System.currentTimeMillis();
        if (now - lastAttemptMs >= RETRY_COOLDOWN_MS) {
            lastAttemptMs = now;
            return true;
        }
        return false;
    }

    public boolean ensureConnected() {
        try {
            if (writer != null && !writer.checkError()) {
                return true;
            }
            if (!shouldAttemptNow()) {
                return false;
            }
            LOGGER.warn("Attempting to (re)connect to dashboard...");
            connect();
            return true;
        } catch (IOException e) {
            LOGGER.warn("(Re)connect failed: {}", e.getMessage());
            return false;
        }
    }
    
    public void sendVideoFrame(byte[] frameData) {
        if (!ensureConnected()) return;
        
        try {
            // Encode frame as Base64
            String base64 = Base64.getEncoder().encodeToString(frameData);
            
            // Send with VIDEO: prefix (default first-person)
            writer.println("VIDEO:" + base64);
            writer.checkError(); // Clear error flag
            logEveryNFrames("VIDEO", base64.length());
        } catch (Exception e) {
            // Silent fail
        }
    }
    
    public void sendVideoFrame(String viewType, byte[] frameData) {
        if (!ensureConnected()) return;
        
        try {
            // Encode frame as Base64
            String base64 = Base64.getEncoder().encodeToString(frameData);
            
            // Send with viewType prefix (viewType already includes colon, e.g., "VIDEO_FP:Freddy:")
            String message = viewType + base64;
            writer.println(message);
            writer.checkError(); // Clear error flag
            
            // Extract just the prefix for logging (e.g., "VIDEO_FP" from "VIDEO_FP:Freddy:")
            String prefix = viewType.split(":")[0];
            logEveryNFrames(prefix, base64.length());
        } catch (Exception e) {
            // Silent fail
        }
    }

    private void logEveryNFrames(String viewType, int base64Length) {
        long count = frameCounter.incrementAndGet();
        // Log every 30th frame to avoid spam but confirm streaming.
        if (count % 30 == 0) {
            LOGGER.info("Sent {} frame #{} ({} chars)", viewType, count, base64Length);
        }
    }
    
    public void close() {
        try {
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            LOGGER.error("Error closing connection", e);
        }
    }
}
