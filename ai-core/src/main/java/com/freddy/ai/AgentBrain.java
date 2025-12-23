//package com.freddy.ai;
//
//import com.freddy.llm.LLMClient;
//
//public class AgentBrain {
//
//    private final String npcName;
//
//    public AgentBrain(String npcName) {
//        this.npcName = npcName;
//    }
//
//    public String respond(String playerName, String message) {
//
//        String prompt = """
//        You are Freddy, a friendly AI character inside Minecraft.
//        You speak casually like a human player.
//        Player %s says: "%s"
//        Reply briefly, friendly, and in character.
//        """.formatted(playerName, message);
//
//        return LLMClient.ask(prompt);
//    }
//}

package com.freddy.ai;

import com.freddy.common.telemetry.FreddySnapshot;

public class AgentBrain {

    private String currentThought = "Idle";
    private String currentAction = "Waiting";

    public void setThought(String thought) {
        this.currentThought = thought;
    }

    public void setAction(String action) {
        this.currentAction = action;
    }

    public FreddySnapshot snapshot(
            String npcName,
            double x,
            double y,
            double z
    ) {
        return new FreddySnapshot(
                npcName,
                currentThought,
                currentAction,
                x, y, z
        );
    }
}

