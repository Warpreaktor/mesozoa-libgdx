package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/**
 * Статическое описание типа тайла.
 *
 * Это не конкретный вытянутый тайл, а запись вида:
 *   "Луг с переходами NORTH_EAST и SOUTH_WEST × 2".
 *
 * TileCatalog/TileBag на старте партии разворачивают эти записи в реальные Tile
 * и перемешивает мешочек.
 */
public final class TileBlueprint {

    public final Biome biome;

    public final Species spawnSpecies;

    public final List<Direction> expansionDirections;

    public final boolean hasBridge;

    public final List<Direction> roadDirections;

    public final int count;

    public TileBlueprint(
            Biome biome,
            Species spawnSpecies,
            List<Direction> expansionDirections,
            int count
    ) {
        this(biome, spawnSpecies, expansionDirections, false, List.of(), count);
    }

    public TileBlueprint(
            Biome biome,
            Species spawnSpecies,
            List<Direction> expansionDirections,
            boolean hasBridge,
            List<Direction> roadDirections,
            int count
    ) {
        if (count < 0) {
            throw new IllegalArgumentException("Tile blueprint count must not be negative");
        }

        this.biome = biome;
        this.spawnSpecies = spawnSpecies;
        this.expansionDirections = List.copyOf(expansionDirections);
        this.hasBridge = hasBridge;
        this.roadDirections = List.copyOf(roadDirections);
        this.count = count;
    }

    public static TileBlueprint tile(Biome biome, int count, Direction... directions) {
        return new TileBlueprint(biome, null, List.of(directions), count);
    }

    public static TileBlueprint spawn(Biome biome, Species species, int count) {
        return new TileBlueprint(biome, species, List.of(), count);
    }

    public TileBlueprint withSpawn(Species species) {
        return new TileBlueprint(
                biome,
                species,
                expansionDirections,
                hasBridge,
                roadDirections,
                count
        );
    }

    public TileBlueprint withBridge() {
        return new TileBlueprint(
                biome,
                spawnSpecies,
                expansionDirections,
                true,
                roadDirections,
                count
        );
    }

    public TileBlueprint withRoad(Direction... directions) {
        return new TileBlueprint(
                biome,
                spawnSpecies,
                expansionDirections,
                hasBridge,
                List.of(directions),
                count
        );
    }

    public boolean isEmpty() {
        return count == 0;
    }
}
