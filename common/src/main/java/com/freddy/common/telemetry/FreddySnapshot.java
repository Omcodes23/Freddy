package com.freddy.common.telemetry;

public class FreddySnapshot {

    public final String npcName;
    public final String thought;
    public final String action;
    public final double x, y, z;

    public FreddySnapshot(
            String npcName,
            String thought,
            String action,
            double x,
            double y,
            double z
    ) {
        this.npcName = npcName;
        this.thought = thought;
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
