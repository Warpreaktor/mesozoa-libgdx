package ru.mesozoa.sim.model;

import ru.mesozoa.sim.rules.SimulationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Мешочки тайлов:
 * - mainTiles — основная колода/мешочек исследования;
 * - extraTiles — дополнительная колода для автоматической достройки биомов.
 *
 * Переходы лежат в TileDefinition.expansionDirections и не меняют картинку тайла.
 */
public final class TileBag {
    private static final Direction[] CARDINALS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private final ArrayList<TileDefinition> mainTiles = new ArrayList<>();
    private final ArrayList<TileDefinition> extraTiles = new ArrayList<>();
    private final Random random;

    private TileBag(Random random) {
        this.random = random;
    }

    public static TileBag createDefault(SimulationConfig config, Random random) {
        TileBag bag = new TileBag(random);

        /*
         * Основная колода генератора карты.
         * Числа соответствуют текущей таблице:
         *   Биом: обычные, +1, +2, +3
         *
         * Лес из правил делим между лиственным и хвойным,
         * потому что био-тропы уже различают эти два биома.
         */
        bag.addMainWithExpansion(Biome.BROADLEAF_FOREST, 5, 4, 3, 1);
        bag.addMainWithExpansion(Biome.CONIFEROUS_FOREST, 4, 4, 2, 1);
        bag.addMainWithExpansion(Biome.MEADOW, 8, 6, 4, 2);
        bag.addMainWithExpansion(Biome.MOUNTAIN, 6, 4, 2, 1);
        bag.addMainWithExpansion(Biome.RIVER, 7, 3, 1, 0);
        bag.addMainWithExpansion(Biome.SWAMP, 7, 3, 1, 0);
        bag.addMainWithExpansion(Biome.LAKE, 6, 2, 1, 0);
        bag.addMainWithExpansion(Biome.FLOODPLAIN, 0, 0, 3, 2);

        /*
         * Спаун-тайлы лежат в основной колоде.
         * Визуально это та же картинка биома, а пиктограмма/буква динозавра
         * рисуется отдельным слоем поверх.
         */
        for (var entry : config.spawnTiles.entrySet()) {
            Species species = entry.getKey();
            int count = entry.getValue();

            for (int i = 0; i < count; i++) {
                bag.mainTiles.add(new TileDefinition(species.spawnBiome, species));
            }
        }

        /*
         * Доп. колода тайлов. Эти тайлы нужны только для автодостройки.
         * Они не содержат спаунов, чтобы переходы не плодили динозавров из воздуха.
         */
        bag.addExtra(Biome.BROADLEAF_FOREST, 12);
        bag.addExtra(Biome.CONIFEROUS_FOREST, 12);
        bag.addExtra(Biome.MEADOW, 20);
        bag.addExtra(Biome.MOUNTAIN, 11);
        bag.addExtra(Biome.RIVER, 5);
        bag.addExtra(Biome.SWAMP, 5);
        bag.addExtra(Biome.LAKE, 4);
        bag.addExtra(Biome.FLOODPLAIN, 12);

        Collections.shuffle(bag.mainTiles, random);
        Collections.shuffle(bag.extraTiles, random);

        return bag;
    }

    private void addMainWithExpansion(Biome biome, int baseCount, int plusOneCount, int plusTwoCount, int plusThreeCount) {
        addMain(biome, baseCount, 0);
        addMain(biome, plusOneCount, 1);
        addMain(biome, plusTwoCount, 2);
        addMain(biome, plusThreeCount, 3);
    }

    private void addMain(Biome biome, int count, int expansionCount) {
        for (int i = 0; i < count; i++) {
            mainTiles.add(new TileDefinition(biome, null, randomDirections(expansionCount)));
        }
    }

    private void addExtra(Biome biome, int count) {
        for (int i = 0; i < count; i++) {
            extraTiles.add(new TileDefinition(biome, null));
        }
    }

    private List<Direction> randomDirections(int count) {
        if (count <= 0) return List.of();

        ArrayList<Direction> directions = new ArrayList<>(List.of(CARDINALS));
        Collections.shuffle(directions, random);

        return directions.subList(0, Math.min(count, directions.size())).stream()
                .sorted()
                .toList();
    }

    public TileDefinition draw() {
        if (mainTiles.isEmpty()) return null;
        return mainTiles.remove(random.nextInt(mainTiles.size()));
    }

    public TileDefinition drawExtraBiome(Biome biome) {
        for (int i = 0; i < extraTiles.size(); i++) {
            TileDefinition def = extraTiles.get(i);
            if (def.biome == biome) {
                return extraTiles.remove(i);
            }
        }
        return null;
    }

    /**
     * Старое имя оставлено как алиас, чтобы соседний код не падал лицом в луг.
     */
    public TileDefinition drawBiome(Biome biome) {
        return drawExtraBiome(biome);
    }

    public int remaining() {
        return remainingMain();
    }

    public int remainingMain() {
        return mainTiles.size();
    }

    public int remainingExtra() {
        return extraTiles.size();
    }

    public boolean isEmpty() {
        return mainTiles.isEmpty();
    }
}
