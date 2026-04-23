package com.freddy.plugin.commands;

import com.freddy.plugin.FreddyPlugin;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Command Server - listens on TCP 25567 for dashboard commands.
 * Supported formats:
 * GOAL:<goal_name>
 * ACTION:<name>[:target[:extra]]
 * CRAFT:<item>[:amount]
 * OBSERVE
 * RETURN_HOME
 */
public class CommandServer extends Thread {
    private static final int PORT = 25567;
    private static final Logger logger = Logger.getLogger("CommandServer");
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORT);
            logger.info("🌐 Command Server listening on port " + PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("✅ Dashboard connected to command server");

                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String line;
                    while ((line = in.readLine()) != null) {
                        processCommand(line);
                    }

                    clientSocket.close();
                } catch (Exception e) {
                    if (running) {
                        logger.warning("⚠️ Command socket error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("❌ Command server failed: " + e.getMessage());
        }
    }

    private void processCommand(String command) {
        logger.info("📨 Received command: " + command);

        Bukkit.getScheduler().runTask(
            Bukkit.getPluginManager().getPlugin("FreddyAI"),
            () -> FreddyPlugin.handleDashboardCommand(command)
        );
    }

    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            logger.warning("⚠️ Error closing command server: " + e.getMessage());
        }
        logger.info("🛑 Command server stopped");
    }
}
