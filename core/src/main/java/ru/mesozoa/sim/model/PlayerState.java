package ru.mesozoa.sim.model;

import java.util.*;

public final class PlayerState {
    public final int id;
    public final Set<Species> task = EnumSet.noneOf(Species.class);
    public final Set<Species> captured = EnumSet.noneOf(Species.class);

    public Point scout;
    public Point hunter;
    public Point engineer;

    public final List<Trap> traps = new ArrayList<>();
    public int hunterBait = 3;
    public int turnsSkipped = 0;

    public PlayerState(int id, Point base) {
        this.id = id;
        this.scout = base;
        this.hunter = base;
        this.engineer = base;
    }

    public boolean isComplete() {
        return captured.containsAll(task);
    }

    public boolean needs(Species species) {
        return task.contains(species) && !captured.contains(species);
    }
}
