package com.freddy.plugin.bridge;

import com.freddy.ai.AgentBrain;
import com.freddy.common.telemetry.FreddySnapshot;
import net.citizensnpcs.api.npc.NPC;

public class FreddyTelemetryBridge {

    private final AgentBrain brain;
    private final NPC npc;

    public FreddyTelemetryBridge(AgentBrain brain, NPC npc) {
        this.brain = brain;
        this.npc = npc;
    }

    public FreddySnapshot capture() {
        var loc = npc.getEntity().getLocation();
        return brain.snapshot(
                npc.getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ()
        );
    }
}
