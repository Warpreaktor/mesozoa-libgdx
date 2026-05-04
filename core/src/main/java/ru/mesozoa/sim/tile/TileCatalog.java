package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static ru.mesozoa.sim.model.Direction.EAST;
import static ru.mesozoa.sim.model.Direction.NORTH;
import static ru.mesozoa.sim.model.Direction.NORTH_EAST;
import static ru.mesozoa.sim.model.Direction.NORTH_WEST;
import static ru.mesozoa.sim.model.Direction.SOUTH;
import static ru.mesozoa.sim.model.Direction.SOUTH_EAST;
import static ru.mesozoa.sim.model.Direction.SOUTH_WEST;
import static ru.mesozoa.sim.model.Direction.WEST;

/**
 * Каталог физических тайлов, из которого перед началом партии собираются мешочки.
 *
 * Важно:
 * - класс не должен добавлять "сверху" отдельную пачку спаун-тайлов;
 * - основные тайлы отвечают только за исследование и переходы биомов;
 * - спаун — это маркировка уже существующего дополнительного тайла;
 * - дополнительные тайлы считаются автоматически по количеству переходов на основных тайлах.
 *
 * После создания каталога списки считаются неизменяемыми.
 */
public final class TileCatalog {
    private final List<MainTile> mainTiles;
    private final List<ExtraTile> extraTiles;

    public TileCatalog(GameConfig config, Random random) {
        ArrayList<TileBlueprint> main = expandToSingleTileBlueprints(baseMainTileBlueprints());
        ArrayList<TileBlueprint> extra = expandToSingleTileBlueprints(generateExtraTileBlueprints(main));

        markSpawnTiles(extra, config.spawnTiles, random);

        this.mainTiles = List.copyOf(toMainTiles(main));
        this.extraTiles = List.copyOf(toExtraTiles(extra));
    }

    /**
     * Возвращает копию списка физических основных тайлов.
     *
     * Основные тайлы не содержат спаун-маркеров. Они отвечают за ручное
     * исследование карты и за переходы, которые вытягивают дополнительные тайлы.
     * Пока эти тайлы лежат в мешочке, их position == null.
     */
    public List<MainTile> getMainTiles() {
        return List.copyOf(mainTiles);
    }

    /**
     * Возвращает копию списка физических дополнительных тайлов.
     *
     * Количество дополнительных тайлов каждого биома вычисляется из основных тайлов:
     * один переход на основном тайле требует один дополнительный тайл того же биома.
     * Именно среди этих дополнительных тайлов случайно расставляются спаун-маркеры
     * динозавров.
     */
    public List<ExtraTile> getExtraTiles() {
        return List.copyOf(extraTiles);
    }

    /**
     * Базовый физический состав основной колоды/мешочка до спаун-маркировки.
     *
     * Тут должны лежать только реальные основные тайлы игры:
     * обычные тайлы, тайлы с переходами, тайлы с диагональными переходами.
     *
     * Спаунов здесь быть не должно: основные тайлы не рождают динозавров.
     */
    private static List<TileBlueprint> baseMainTileBlueprints() {
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

                // Хвойный лес
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
                TileBlueprint.tile(Biome.MEADOW, 8),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH),
                TileBlueprint.tile(Biome.MEADOW, 1, EAST),
                TileBlueprint.tile(Biome.MEADOW, 1, SOUTH),
                TileBlueprint.tile(Biome.MEADOW, 1, WEST),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH_EAST),
                TileBlueprint.tile(Biome.MEADOW, 1, SOUTH_WEST),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH, SOUTH),
                TileBlueprint.tile(Biome.MEADOW, 1, EAST, WEST),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH_EAST, SOUTH_WEST),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH_WEST, SOUTH_EAST),
                TileBlueprint.tile(Biome.MEADOW, 1, NORTH, EAST, SOUTH),
                TileBlueprint.tile(Biome.MEADOW, 1, WEST, NORTH_WEST, SOUTH_WEST),

                // Горы
                TileBlueprint.tile(Biome.MOUNTAIN, 6),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, NORTH),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, EAST),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, SOUTH_WEST),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, NORTH_WEST),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, NORTH, WEST),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, SOUTH_EAST, EAST),
                TileBlueprint.tile(Biome.MOUNTAIN, 1, NORTH, NORTH_EAST, EAST),

                // Река
                TileBlueprint.tile(Biome.RIVER, 7),
                TileBlueprint.tile(Biome.RIVER, 1, NORTH),
                TileBlueprint.tile(Biome.RIVER, 1, SOUTH),
                TileBlueprint.tile(Biome.RIVER, 1, NORTH_EAST),
                TileBlueprint.tile(Biome.RIVER, 1, NORTH, SOUTH),

                // Болото
                TileBlueprint.tile(Biome.SWAMP, 7),
                TileBlueprint.tile(Biome.SWAMP, 1, WEST),
                TileBlueprint.tile(Biome.SWAMP, 1, EAST),
                TileBlueprint.tile(Biome.SWAMP, 1, SOUTH_WEST),
                TileBlueprint.tile(Biome.SWAMP, 1, NORTH_WEST, SOUTH_EAST),

                // Озеро
                TileBlueprint.tile(Biome.LAKE, 6),
                TileBlueprint.tile(Biome.LAKE, 1, NORTH),
                TileBlueprint.tile(Biome.LAKE, 1, EAST),
                TileBlueprint.tile(Biome.LAKE, 1, SOUTH_WEST, NORTH_EAST),

                // Пойма
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, NORTH, SOUTH),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, EAST, WEST),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, NORTH_WEST, SOUTH_EAST),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, NORTH, EAST, SOUTH),
                TileBlueprint.tile(Biome.FLOODPLAIN, 1, WEST, NORTH_WEST, SOUTH_WEST)
        );
    }

    /**
     * Разворачивает сгруппированные blueprint'ы в список отдельных тайлов.
     *
     * Было:
     *   Луг без переходов × 8
     *
     * Станет:
     *   8 отдельных TileBlueprint с count = 1.
     *
     * Это нужно, чтобы потом можно было случайно пометить спауном конкретный
     * дополнительный экземпляр тайла, а не всю группу целиком. Вот так и рождается
     * бюрократия, но хотя бы полезная.
     */
    private static ArrayList<TileBlueprint> expandToSingleTileBlueprints(List<TileBlueprint> blueprints) {
        ArrayList<TileBlueprint> result = new ArrayList<>();

        for (TileBlueprint blueprint : blueprints) {
            if (blueprint.isEmpty()) continue;

            for (int i = 0; i < blueprint.count; i++) {
                result.add(new TileBlueprint(
                        blueprint.biome,
                        blueprint.spawnSpecies,
                        blueprint.expansionDirections,
                        blueprint.hasBridge,
                        blueprint.roadDirections,
                        1
                ));
            }
        }

        return result;
    }

    /**
     * Случайно маркирует уже существующие дополнительные тайлы спаунами динозавров.
     *
     * Этот метод НЕ создаёт новые тайлы. Он заменяет часть уже существующих
     * дополнительных blueprint'ов на такие же blueprint'ы, но с заполненным
     * spawnSpecies. Основные тайлы сюда не передаются: динозавр должен появляться
     * только на дополнительном тайле с силуэтом.
     */
    private static void markSpawnTiles(
            List<TileBlueprint> tileBlueprints,
            Map<Species, Integer> spawnTiles,
            Random random
    ) {
        for (var entry : spawnTiles.entrySet()) {
            Species species = entry.getKey();
            int requestedCount = entry.getValue();

            if (requestedCount <= 0) continue;

            ArrayList<Integer> candidates = new ArrayList<>();

            for (int i = 0; i < tileBlueprints.size(); i++) {
                TileBlueprint blueprint = tileBlueprints.get(i);

                if (blueprint.spawnSpecies == null && blueprint.biome == species.spawnBiome) {
                    candidates.add(i);
                }
            }

            if (candidates.size() < requestedCount) {
                throw new IllegalStateException(
                        "Not enough spawn tile candidates for " + species
                                + ". Requested: " + requestedCount
                                + ", available: " + candidates.size()
                );
            }

            Collections.shuffle(candidates, random);

            for (int i = 0; i < requestedCount; i++) {
                int tileIndex = candidates.get(i);
                TileBlueprint source = tileBlueprints.get(tileIndex);

                tileBlueprints.set(tileIndex, new TileBlueprint(
                        source.biome,
                        species,
                        source.expansionDirections,
                        source.hasBridge,
                        source.roadDirections,
                        1
                ));
            }
        }
    }


    /**
     * Превращает blueprint-описания в конкретные основные тайлы.
     *
     * MainTile никогда не получает спаун-маркер. Он отвечает только за ручное
     * исследование карты и переходы, которые достраиваются ExtraTile.
     */
    private static List<MainTile> toMainTiles(List<TileBlueprint> blueprints) {
        ArrayList<MainTile> result = new ArrayList<>();

        for (TileBlueprint blueprint : blueprints) {
            if (blueprint.isEmpty()) continue;

            for (int i = 0; i < blueprint.count; i++) {
                result.add(new MainTile(
                        blueprint.biome,
                        blueprint.expansionDirections,
                        blueprint.hasBridge,
                        blueprint.roadDirections,
                        isGroundPassableByDefault(blueprint.biome, blueprint.hasBridge)
                ));
            }
        }

        return result;
    }

    /**
     * Превращает blueprint-описания в конкретные дополнительные тайлы.
     *
     * ExtraTile не имеет переходов, но может иметь спаун-маркер. Именно эти
     * тайлы автоматически выкладываются по переходам MainTile.
     */
    private static List<ExtraTile> toExtraTiles(List<TileBlueprint> blueprints) {
        ArrayList<ExtraTile> result = new ArrayList<>();

        for (TileBlueprint blueprint : blueprints) {
            if (blueprint.isEmpty()) continue;

            for (int i = 0; i < blueprint.count; i++) {
                result.add(new ExtraTile(
                        blueprint.biome,
                        blueprint.spawnSpecies,
                        blueprint.hasBridge,
                        blueprint.roadDirections,
                        isGroundPassableByDefault(blueprint.biome, blueprint.hasBridge)
                ));
            }
        }

        return result;
    }

    /**
     * Определяет стартовую проходимость физического тайла для наземных рейнджеров.
     *
     * Это не свойство биома как такового, а правило начальной генерации тайла.
     * После начала игры состояние конкретного тайла может измениться:
     * например, инженер построит мост на реке, и тайл станет проходимым.
     *
     * @param biome биом создаваемого тайла
     * @param hasBridge есть ли мост на тайле уже при создании
     * @return true, если созданный тайл должен быть проходимым для наземных рейнджеров
     */
    private static boolean isGroundPassableByDefault(Biome biome, boolean hasBridge) {
        if (hasBridge) {
            return true;
        }

        return switch (biome) {
            case LANDING,
                    BROADLEAF_FOREST,
                    CONIFEROUS_FOREST,
                    MEADOW,
                    FLOODPLAIN,
                    RIVER,
                    SWAMP -> true;

            case LAKE,
                    MOUNTAIN -> false;
        };
    }

    /**
     * Генерирует дополнительные тайлы на основе переходов основных тайлов.
     *
     * Если основной тайл биома "Река" имеет 2 перехода,
     * значит для него в дополнительный мешочек понадобится 2 тайла "Река".
     *
     * На этом шаге спаун-маркировка ещё не учитывается. Сначала считается набор
     * физических дополнительных тайлов, а потом часть этих тайлов помечается
     * силуэтами динозавров.
     */
    private static List<TileBlueprint> generateExtraTileBlueprints(List<TileBlueprint> mainTileBlueprints) {
        EnumMap<Biome, Integer> extraCountByBiome = new EnumMap<>(Biome.class);

        for (TileBlueprint blueprint : mainTileBlueprints) {
            int expansionCount = blueprint.expansionDirections.size() * blueprint.count;

            if (expansionCount <= 0) continue;

            extraCountByBiome.merge(blueprint.biome, expansionCount, Integer::sum);
        }

        ArrayList<TileBlueprint> result = new ArrayList<>();

        for (Biome biome : Biome.values()) {
            int count = extraCountByBiome.getOrDefault(biome, 0);
            if (count > 0) {
                result.add(TileBlueprint.tile(biome, count));
            }
        }

        return result;
    }
}
