package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Species;

import java.util.List;

public final class Tile {
    public final Biome biome;
    public final Species spawnSpecies;
    public final String imagePath;
    public final List<Direction> expansionDirections;

    /**
     * Поворот физического тайла при выкладке.
     * 0 — 0°, 1 — 90° по часовой, 2 — 180°, 3 — 270° по часовой.
     */
    public final int rotationQuarterTurns;

    public boolean opened;
    public boolean spawnUsed;

    public Tile(Biome biome, Species spawnSpecies, boolean opened) {
        this(biome, spawnSpecies, opened, List.of(), 0);
    }

    public Tile(
            Biome biome,
            Species spawnSpecies,
            boolean opened,
            List<Direction> expansionDirections,
            int rotationQuarterTurns
    ) {
        this.biome = biome;
        this.spawnSpecies = spawnSpecies;
        this.opened = opened;
        this.imagePath = "tiles/" + biome.imagePath;
        this.expansionDirections = List.copyOf(expansionDirections);
        this.rotationQuarterTurns = Math.floorMod(rotationQuarterTurns, 4);
    }

    public boolean hasSpawn() {
        return spawnSpecies != null;
    }

    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }

    /**
     * LibGDX вращает SpriteBatch против часовой для положительных градусов.
     * А rotationQuarterTurns у нас задан по часовой, поэтому знак отрицательный.
     */
    public float rotationDegreesForRendering() {
        return -90f * rotationQuarterTurns;
    }
}
