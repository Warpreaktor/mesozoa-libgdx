package ru.mesozoa.sim.model;

public final class Tile {
    public final Biome biome;
    public final Species spawnSpecies;
    public final String imagePath;
    public boolean opened;
    public boolean spawnUsed;

    public Tile(Biome biome, Species spawnSpecies, boolean opened) {
        this.biome = biome;
        this.spawnSpecies = spawnSpecies;
        this.opened = opened;
        this.imagePath = "tiles/" + biome.imagePath;
    }

    public boolean hasSpawn() {
        return spawnSpecies != null;
    }
}
