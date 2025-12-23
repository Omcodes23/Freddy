package com.freddy.dashboard.controller;

import com.freddy.common.telemetry.FreddySnapshot;
import javafx.scene.control.Label;

public class DashboardController {

    public Label npcName;
    public Label thought;
    public Label action;
    public Label position;

    public void update(FreddySnapshot snap) {
        npcName.setText(snap.npcName);
        thought.setText(snap.thought);
        action.setText(snap.action);
        position.setText(
                snap.x + ", " + snap.y + ", " + snap.z
        );
    }
}
