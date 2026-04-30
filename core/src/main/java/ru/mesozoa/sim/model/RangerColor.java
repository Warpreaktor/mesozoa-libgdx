package ru.mesozoa.sim.model;

public enum RangerColor {
    RED("red"),
    BLUE("blue"),
    GREEN("green"),
    YELLOW("yellow");

    public final String assetSuffix;

    RangerColor(String assetSuffix) {
        this.assetSuffix = assetSuffix;
    }

    public static RangerColor forPlayerId(int playerId) {
        RangerColor[] order = {RED, BLUE, GREEN, YELLOW};
        return order[Math.floorMod(playerId - 1, order.length)];
    }
}
