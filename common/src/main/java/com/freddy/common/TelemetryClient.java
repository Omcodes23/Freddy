package com.freddy.common;

import java.io.*;
import java.net.Socket;

/**
 * Client for sending telemetry data to the dashboard
 */
public class TelemetryClient {
    
    private Socket socket;
    private PrintWriter out;
    private final String host;
    private final int port;
    private boolean connected = false;
    
    public TelemetryClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Connect to the dashboard
     */
    public boolean connect() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            System.out.println("[Telemetry] Connected to dashboard at " + host + ":" + port);
            return true;
        } catch (IOException e) {
            connected = false;
            return false;
        }
    }
    
    /**
     * Send a message to the dashboard
     */
    public void send(String message) {
        // Lazy reconnect if dashboard started after plugin
        if (!connected || out == null) {
            if (!connect()) {
                return;
            }
        }
        
        try {
            out.println(message);
            if (out.checkError()) {
                connected = false;
            }
        } catch (Exception e) {
            connected = false;
        }
    }
    
    /**
     * Send tick number
     */
    public void sendTick(int tick) {
        send("TICK:" + tick);
    }
    
    /**
     * Send observation data
     */
    public void sendObservation(String observation) {
        send("OBSERVATION:" + observation);
    }
    
    /**
     * Send position
     */
    public void sendPosition(double x, double y, double z) {
        send("POSITION:" + String.format("%.1f, %.1f, %.1f", x, y, z));
    }
    
    /**
     * Send player count/names
     */
    public void sendPlayers(String players) {
        send("PLAYERS:" + players);
    }
    
    /**
     * Send thinking/prompt
     */
    public void sendThinking(String prompt) {
        send("THINKING:" + prompt);
    }
    
    /**
     * Send LLM response
     */
    public void sendLLMResponse(String response) {
        send("LLM_RESPONSE:" + response);
    }
    
    /**
     * Send action
     */
    public void sendAction(String action) {
        send("ACTION:" + action);
    }
    
    /**
     * Send error
     */
    public void sendError(String error) {
        send("ERROR:" + error);
    }
    
    /**
     * Send response time in milliseconds
     */
    public void sendResponseTime(long milliseconds) {
        send("RESPONSE_TIME:" + milliseconds);
    }
    
    /**
     * Send AI POV (what Freddy sees)
     * Encodes multi-line visual data into a single line using escape sequences
     */
    public void sendPOV(String povData) {
        if (povData == null || povData.isEmpty()) {
            send("POV:");
            return;
        }
        
        // Replace newlines with \n escape sequences so the entire POV fits in one line
        String encoded = povData.replace("\n", "\\n");
        send("POV:" + encoded);
    }
    
    /**
     * Send chat message
     */
    public void sendChat(String playerName, String message) {
        send("CHAT:" + playerName + "|" + message);
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Disconnect from dashboard
     */
    public void disconnect() {
        try {
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        connected = false;
    }
}
