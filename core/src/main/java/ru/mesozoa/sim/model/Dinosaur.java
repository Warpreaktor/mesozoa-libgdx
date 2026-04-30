package ru.mesozoa.sim.model;

public final class Dinosaur {
    public final int id;
    public final Species species;
    public Point position;
    public int trailIndex;
    public boolean captured;
    public boolean removed;
    public Point lastPosition;

    public Dinosaur(int id, Species species, Point position) {
        this.id = id;
        this.species = species;
        this.position = position;
        this.lastPosition = position;
        this.trailIndex = 0;
    }
}
