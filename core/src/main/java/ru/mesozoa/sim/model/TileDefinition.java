package ru.mesozoa.sim.model;

import java.util.List;

/**
 * Тайл в мешочке до размещения на столе.
 *
 * Картинка определяется только биомом: river.png, meadow.png, lake.png и т.д.
 * Переходы не требуют отдельных PNG. Они хранятся в expansionDirections
 * и рисуются поверх тайла кодом.
 */
public final class TileDefinition {
    public final Biome biome;
    public final Species spawnSpecies;
    public final List<Direction> expansionDirections;

    public TileDefinition(Biome biome, Species spawnSpecies) {
        this(biome, spawnSpecies, List.of());
    }

    public TileDefinition(Biome biome, Species spawnSpecies, List<Direction> expansionDirections) {
        this.biome = biome;
        this.spawnSpecies = spawnSpecies;
        this.expansionDirections = List.copyOf(expansionDirections);
    }

    public Tile toPlacedTile() {
        return new Tile(biome, spawnSpecies, true, expansionDirections);
    }

    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }
}
