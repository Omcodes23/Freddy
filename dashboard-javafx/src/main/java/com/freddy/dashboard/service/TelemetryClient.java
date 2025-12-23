//package com.freddy.dashboard.service;
//
//import com.freddy.common.telemetry.FreddySnapshot;
//
//public class TelemetryClient {
//
//    public void render(FreddySnapshot snapshot) {
//
//        System.out.println("NPC: " + snapshot.npcName);
//        System.out.println("Thought: " + snapshot.thought);
//        System.out.println("Action: " + snapshot.action);
//        System.out.println("Position: "
//                + snapshot.x + ", "
//                + snapshot.y + ", "
//                + snapshot.z);
//    }
//}
package com.freddy.dashboard.service;

import com.freddy.common.telemetry.FreddySnapshot;

public class TelemetryClient {

    // TEMP: mocked data (replace with HTTP later)
    public FreddySnapshot fetch() {
        return new FreddySnapshot(
                "Freddy",
                "Analyzing surroundings",
                "Following player",
                120.5, 64, -22.8
        );
    }
}
