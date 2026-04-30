package ru.mesozoa.sim.model;

import java.util.List;

/**
 * Тайл в мешочке до размещения на столе.
 *
 * expansionDirections описывает направления на физическом тайле
 * в его базовой ориентации. Когда игрок выкладывает тайл случайно
 * повёрнутым, направления поворачиваются вместе с тайлом.
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
        return toPlacedTile(0);
    }

    public Tile toPlacedTile(int rotationQuarterTurns) {
        return new Tile(
                biome,
                spawnSpecies,
                true,
                rotatedDirections(rotationQuarterTurns),
                rotationQuarterTurns
        );
    }

    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }

    private List<Direction> rotatedDirections(int rotationQuarterTurns) {
        return expansionDirections.stream()
                .map(direction -> direction.rotateClockwiseQuarterTurns(rotationQuarterTurns))
                .toList();
    }
}
