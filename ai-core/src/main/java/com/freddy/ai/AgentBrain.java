package com.freddy.ai;

import com.freddy.llm.LLMClient;

public class AgentBrain {

    private final String npcName;

    public AgentBrain(String npcName) {
        this.npcName = npcName;
    }

    public String respond(String playerName, String message) {

        String prompt = """
        You are Freddy, a friendly AI character inside Minecraft.
        You speak casually like a human player.
        Player %s says: "%s"
        Reply briefly, friendly, and in character.
        """.formatted(playerName, message);

        return LLMClient.ask(prompt);
    }
}
