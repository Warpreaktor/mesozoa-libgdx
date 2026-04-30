package ru.mesozoa.sim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.mesozoa.sim.model.Direction.EAST;
import static ru.mesozoa.sim.model.Direction.NORTH;
import static ru.mesozoa.sim.model.Direction.NORTH_EAST;
import static ru.mesozoa.sim.model.Direction.NORTH_WEST;
import static ru.mesozoa.sim.model.Direction.SOUTH;
import static ru.mesozoa.sim.model.Direction.SOUTH_EAST;
import static ru.mesozoa.sim.model.Direction.SOUTH_WEST;
import static ru.mesozoa.sim.model.Direction.WEST;

/**
 * Статический каталог тайлов.
 *
 * Здесь описывается "физический" состав мешочка:
 * какие биомы есть, сколько экземпляров каждого варианта,
 * и в какие стороны эти варианты достраивают доп. тайлы.
 */
public final class TileCatalog {
    private TileCatalog() {
    }

    public static List<TileBlueprint> mainTileBlueprints() {
        return List.of(
                // Лиственный лес
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 7),

                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, WEST),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, EAST),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, NORTH),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, SOUTH),

                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, SOUTH, NORTH),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, WEST, EAST),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, WEST, SOUTH),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, SOUTH, EAST),

                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, NORTH_WEST, EAST, SOUTH_EAST),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, SOUTH_WEST, SOUTH, SOUTH_EAST),
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 1, NORTH_EAST, EAST, SOUTH),

                // Хвойный лес.
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 7),

                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, WEST),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, EAST),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, NORTH),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, SOUTH),

                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, SOUTH, NORTH),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, WEST, EAST),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, WEST, SOUTH),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, SOUTH, EAST),

                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, NORTH_WEST, EAST, SOUTH_EAST),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, SOUTH_WEST, SOUTH, SOUTH_EAST),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 1, NORTH_EAST, EAST, SOUTH),

                // Луг
                TileBlueprint.tile(Biome.MEADOW, 7),

                TileBlueprint.tile(Biome.MEADOW, 1, SOUTH),
                TileBlueprint.tile(Biome.MEADOW, 1, WEST),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH),
                TileBlueprint.tile(Biome.MEADOW, 1, EAST),
                TileBlueprint.tile(Biome.MEADOW, 1, SOUTH_EAST),
                TileBlueprint.tile(Biome.MEADOW, 1, SOUTH_WEST),


                TileBlueprint.tile(Biome.MEADOW, 1, NORTH, SOUTH),
                TileBlueprint.tile(Biome.MEADOW, 1, SOUTH_WEST, SOUTH),
                TileBlueprint.tile(Biome.MEADOW, 1, WEST, NORTH_WEST),
                TileBlueprint.tile(Biome.MEADOW, 1, EAST, SOUTH_EAST),

                TileBlueprint.tile(Biome.MEADOW, 1, WEST, NORTH_WEST, NORTH),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH, NORTH_EAST, EAST),
                TileBlueprint.tile(Biome.MEADOW, 1, SOUTH, SOUTH_WEST, WEST),

                // Горы
                TileBlueprint.tile(Biome.MOUNTAIN, 3),

                TileBlueprint.tile(Biome.MOUNTAIN, 1, NORTH),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, EAST),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, SOUTH),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, WEST),

                TileBlueprint.tile(Biome.MOUNTAIN, 1, WEST, NORTH),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, NORTH, SOUTH),

                TileBlueprint.tile(Biome.MOUNTAIN, 1, SOUTH_WEST, WEST, NORTH),

                // Река
                TileBlueprint.tile(Biome.RIVER, 1, NORTH),
                TileBlueprint.tile(Biome.RIVER, 1, WEST),
                TileBlueprint.tile(Biome.RIVER, 2, EAST),
                TileBlueprint.tile(Biome.RIVER, 1, SOUTH_WEST),

                TileBlueprint.tile(Biome.RIVER, 1, SOUTH_WEST, NORTH_WEST),
                TileBlueprint.tile(Biome.RIVER, 1, SOUTH_WEST, NORTH_EAST),
                TileBlueprint.tile(Biome.RIVER, 1, NORTH, SOUTH),
                TileBlueprint.tile(Biome.RIVER, 1, SOUTH, WEST),

                TileBlueprint.tile(Biome.RIVER, 1, EAST, SOUTH, WEST),
                TileBlueprint.tile(Biome.RIVER, 1, NORTH, SOUTH_EAST, WEST),
                TileBlueprint.tile(Biome.RIVER, 1, WEST, NORTH_WEST, SOUTH_EAST),
                TileBlueprint.tile(Biome.RIVER, 1, NORTH, SOUTH_EAST, SOUTH_WEST),

                // Болото
                TileBlueprint.tile(Biome.SWAMP, 6),
                TileBlueprint.tile(Biome.SWAMP, 1, EAST),
                TileBlueprint.tile(Biome.SWAMP, 1, SOUTH),
                TileBlueprint.tile(Biome.SWAMP, 1, SOUTH_WEST),

                TileBlueprint.tile(Biome.SWAMP, 1, NORTH, SOUTH),

                // Озеро
                TileBlueprint.tile(Biome.LAKE, 4),
                TileBlueprint.tile(Biome.LAKE, 1, SOUTH),
                TileBlueprint.tile(Biome.LAKE, 1, EAST),
                TileBlueprint.tile(Biome.LAKE, 1, NORTH_EAST, EAST),

                // Пойма
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, WEST, NORTH_EAST),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, SOUTH_WEST, NORTH_EAST),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, NORTH_WEST, SOUTH_EAST),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, NORTH_WEST, SOUTH_EAST, WEST),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, NORTH, SOUTH_EAST, SOUTH_WEST)
        );
    }

    public static List<TileBlueprint> spawnTileBlueprints(Map<Species, Integer> spawnTiles) {
        ArrayList<TileBlueprint> blueprints = new ArrayList<>();

        for (var entry : spawnTiles.entrySet()) {
            Species species = entry.getKey();
            int count = entry.getValue();

            if (count > 0) {
                blueprints.add(TileBlueprint.spawn(species.spawnBiome, species, count));
            }
        }

        return blueprints;
    }

    public static List<TileBlueprint> extraTileBlueprints() {
        return List.of(
                TileBlueprint.tile(Biome.BROADLEAF_FOREST, 12),
                TileBlueprint.tile(Biome.CONIFEROUS_FOREST, 12),
                TileBlueprint.tile(Biome.MEADOW, 20),
                TileBlueprint.tile(Biome.MOUNTAIN, 11),
                TileBlueprint.tile(Biome.RIVER, 5),
                TileBlueprint.tile(Biome.SWAMP, 5),
                TileBlueprint.tile(Biome.LAKE, 4),
                TileBlueprint.tile(Biome.FLOODPLAIN, 12)
        );
    }
}
