package ru.mesozoa.sim.model;

import java.util.*;

public final class PlayerState {
    public final int id;
    public final RangerColor color;

    public final Set<Species> task = EnumSet.noneOf(Species.class);
    public final Set<Species> captured = EnumSet.noneOf(Species.class);

    public Point scout;
    public Point driver;
    public Point engineer;
    public Point hunter;

    public final List<Trap> traps = new ArrayList<>();
    public int hunterBait = 3;
    public int turnsSkipped = 0;

    public PlayerState(int id, Point base) {
        this(id, RangerColor.forPlayerId(id), base);
    }

    public PlayerState(int id, RangerColor color, Point base) {
        this.id = id;
        this.color = color;
        this.scout = base;
        this.driver = base;
        this.engineer = base;
        this.hunter = base;
    }

    public boolean isComplete() {
        return captured.containsAll(task);
    }

    public boolean needs(Species species) {
        return task.contains(species) && !captured.contains(species);
    }

    public Point positionOf(RangerRole role) {
        return switch (role) {
            case SCOUT -> scout;
            case DRIVER -> driver;
            case ENGINEER -> engineer;
            case HUNTER -> hunter;
        };
    }

    public void setPosition(RangerRole role, Point position) {
        switch (role) {
            case SCOUT -> scout = position;
            case DRIVER -> driver = position;
            case ENGINEER -> engineer = position;
            case HUNTER -> hunter = position;
        }
    }

    public void returnTeamToBase(Point base) {
        scout = base;
        driver = base;
        engineer = base;
        hunter = base;
    }
}
