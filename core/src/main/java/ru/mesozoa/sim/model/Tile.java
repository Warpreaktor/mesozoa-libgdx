package ru.mesozoa.sim.model;

import java.util.List;

public final class Tile {
    public final Biome biome;
    public final Species spawnSpecies;
    public final String imagePath;
    public final List<Direction> expansionDirections;

    public boolean opened;
    public boolean spawnUsed;

    public Tile(Biome biome, Species spawnSpecies, boolean opened) {
        this(biome, spawnSpecies, opened, List.of());
    }

    public Tile(Biome biome, Species spawnSpecies, boolean opened, List<Direction> expansionDirections) {
        this.biome = biome;
        this.spawnSpecies = spawnSpecies;
        this.opened = opened;
        this.imagePath = "tiles/" + biome.imagePath;
        this.expansionDirections = List.copyOf(expansionDirections);
    }

    public boolean hasSpawn() {
        return spawnSpecies != null;
    }

    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }
}
