package ru.mesozoa.sim.model;

public final class Trap {
    public final int playerId;
    public Point position;
    public boolean active = true;

    public Trap(int playerId, Point position) {
        this.playerId = playerId;
        this.position = position;
    }
}
