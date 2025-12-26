package com.freddy.plugin.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import net.citizensnpcs.api.npc.NPC;

import java.util.*;
import java.util.logging.Logger;

/**
 * Intelligent Chat System for Freddy AI
 * Responds to players contextually using LLM
 */
public class ChatSystem implements Listener {
    
    private final NPC freddy;
    private final Plugin plugin;
    private final Logger logger;
    private final com.freddy.llm.LLMClient llmClient;
    
    // Memory of conversations
    private Map<String, ConversationContext> conversations;
    private Queue<String> recentMessages;
    
    public ChatSystem(NPC freddy, Plugin plugin, com.freddy.llm.LLMClient llmClient) {
        this.freddy = freddy;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.llmClient = llmClient;
        this.conversations = new HashMap<>();
        this.recentMessages = new LinkedList<>();
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Listen to player chat
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // Only respond if nearby
        if (!isNearby(player)) return;
        
        // Check if message mentions Freddy or is a question
        if (shouldRespond(message)) {
            respondToChat(player, message);
        }
        
        // Add to memory
        recordMessage(player.getName(), message);
    }
    
    /**
     * Generate response using LLM
     */
    private void respondToChat(Player player, String message) {
        // Build context
        ConversationContext context = getOrCreateContext(player.getName());
        context.addMessage(message);
        
        // Create prompt for LLM
        String prompt = buildChatPrompt(player, message, context);
        
        // Get LLM response asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String rawResponse = llmClient.ask(prompt);
                
                // Clean response
                final String response = cleanResponse(rawResponse);
                
                // Send response back on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendFreddyChat(response);
                    context.addResponse(response);
                });
                
                logger.info("[FreddyAI Chat] " + player.getName() + " -> Freddy: " + message);
                logger.info("[FreddyAI Chat] Freddy -> " + player.getName() + ": " + response);
                
            } catch (Exception e) {
                logger.warning("Chat response error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Build context-aware prompt for chat
     */
    private String buildChatPrompt(Player player, String message, ConversationContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are Freddy, a friendly AI in Minecraft.\n");
        prompt.append("You are having a conversation with ").append(player.getName()).append(".\n\n");
        
        prompt.append("CURRENT STATUS:\n");
        prompt.append("- Your Name: Freddy\n");
        prompt.append("- Target Player: ").append(player.getName()).append("\n");
        prompt.append("- Player Health: ").append((int)player.getHealth()).append("/20\n");
        prompt.append("- Player Location: ").append(formatLocation(player.getLocation())).append("\n");
        prompt.append("- Your Mood: Friendly and helpful\n\n");
        
        prompt.append("CONVERSATION HISTORY:\n");
        for (String msg : context.getRecentMessages(3)) {
            prompt.append("- ").append(msg).append("\n");
        }
        
        prompt.append("\nPLAYER JUST SAID: \"").append(message).append("\"\n\n");
        
        prompt.append("RESPOND NATURALLY:\n");
        prompt.append("- Be friendly and conversational\n");
        prompt.append("- Keep response SHORT (1-2 sentences max)\n");
        prompt.append("- Be helpful if they ask for assistance\n");
        prompt.append("- Show personality\n");
        prompt.append("- Don't break character\n\n");
        
        prompt.append("Your response (no quotes): ");
        
        return prompt.toString();
    }
    
    /**
     * Check if should respond to message
     */
    private boolean shouldRespond(String message) {
        String lower = message.toLowerCase();
        
        // Always respond if mentioned
        if (lower.contains("freddy") || lower.contains("ai") || lower.contains("npc")) {
            return true;
        }
        
        // Respond to questions (contains ? or common question words)
        if (lower.contains("?") || 
            lower.startsWith("what ") || 
            lower.startsWith("where ") ||
            lower.startsWith("how ") ||
            lower.startsWith("why ") ||
            lower.startsWith("who ")) {
            return true;
        }
        
        // Respond to greetings
        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey") ||
            lower.equals("hey freddy") || lower.equals("hello freddy")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Send chat message as Freddy
     */
    private void sendFreddyChat(String message) {
        if (freddy.getEntity() instanceof Player) {
            ((Player) freddy.getEntity()).chat(message);
        }
    }
    
    /**
     * Clean LLM response
     */
    private String cleanResponse(String response) {
        return response
            .trim()
            .replaceAll("^['\"]|['\"]$", "")  // Remove quotes
            .replaceAll("Freddy: ", "")       // Remove Freddy prefix
            .replaceAll("\\*.*?\\*", "")      // Remove actions
            .trim();
    }
    
    /**
     * Check if player is nearby
     */
    private boolean isNearby(Player player) {
        return freddy.getEntity().getLocation().distance(player.getLocation()) < 50;
    }
    
    /**
     * Record message in memory
     */
    private void recordMessage(String playerName, String message) {
        recentMessages.add(playerName + ": " + message);
        if (recentMessages.size() > 20) {
            recentMessages.poll();
        }
    }
    
    /**
     * Get or create conversation context
     */
    private ConversationContext getOrCreateContext(String playerName) {
        return conversations.computeIfAbsent(
            playerName, 
            k -> new ConversationContext(playerName)
        );
    }
    
    /**
     * Format location nicely
     */
    private String formatLocation(org.bukkit.Location loc) {
        return String.format("[%.0f, %.0f, %.0f]", loc.getX(), loc.getY(), loc.getZ());
    }
    
    // ===== CONVERSATION CONTEXT =====
    
    public static class ConversationContext {
        private String playerName;
        private List<String> messages = new ArrayList<>();
        private List<String> responses = new ArrayList<>();
        private long lastInteraction = System.currentTimeMillis();
        
        public ConversationContext(String playerName) {
            this.playerName = playerName;
        }
        
        public void addMessage(String message) {
            messages.add(message);
            lastInteraction = System.currentTimeMillis();
        }
        
        public void addResponse(String response) {
            responses.add(response);
        }
        
        public List<String> getRecentMessages(int count) {
            int start = Math.max(0, messages.size() - count);
            return messages.subList(start, messages.size());
        }
        
        public boolean isActive() {
            // Consider inactive if no message in 5 minutes
            return System.currentTimeMillis() - lastInteraction < 300000;
        }
    }
}
