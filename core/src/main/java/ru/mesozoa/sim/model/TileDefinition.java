package ru.mesozoa.sim.model;

import java.util.List;

/**
 * Тайл в мешочке до размещения на столе.
 *
 * Важно: это не клетка заранее созданной карты.
 * Игрок вытягивает TileDefinition из мешочка, выбирает место на границе уже
 * выложенного поля, и только после этого тайл становится Tile на Board.
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
        return new Tile(biome, spawnSpecies, true);
    }

    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }
}
