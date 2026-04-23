package com.freddy.plugin.ai;

public class FreddyCraftRequest {
    public String item;
    public int amount = 1;

    public FreddyCraftRequest() {
    }

    public FreddyCraftRequest(String item, int amount) {
        this.item = item;
        this.amount = amount;
    }
}