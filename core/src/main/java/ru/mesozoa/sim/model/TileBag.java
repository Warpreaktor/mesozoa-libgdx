package ru.mesozoa.sim.model;

import ru.mesozoa.sim.rules.SimulationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Мешочек тайлов.
 *
 * Модель принципиально не знает карту заранее. Тайлы существуют только тут,
 * пока игрок не вытянул их и не положил на стол. Да, наконец-то настольная игра
 * перестала притворяться заранее сгенерированным прямоугольником.
 */
public final class TileBag {
    private final ArrayList<TileDefinition> tiles = new ArrayList<>();
    private final Random random;

    private TileBag(Random random) {
        this.random = random;
    }

    public static TileBag createDefault(SimulationConfig config, Random random) {
        TileBag bag = new TileBag(random);

        // Базовая колода по текущим пропорциям из правил.
        // Лес разделён на лиственный и хвойный, потому что био-тропы уже различают их.
        bag.add(Biome.BROADLEAF_FOREST, 24);
        bag.add(Biome.CONIFEROUS_FOREST, 24);
        bag.add(Biome.MEADOW, 40);
        bag.add(Biome.MOUNTAIN, 24);
        bag.add(Biome.RIVER, 16);
        bag.add(Biome.SWAMP, 16);
        bag.add(Biome.LAKE, 13);
        bag.add(Biome.FLOODPLAIN, 12);

        // Спаун-тайлы. Они лежат в том же мешочке и становятся видимыми только
        // когда реально вытянуты и размещены.
        for (var entry : config.spawnTiles.entrySet()) {
            Species species = entry.getKey();
            int count = entry.getValue();
            for (int i = 0; i < count; i++) {
                bag.tiles.add(new TileDefinition(species.spawnBiome, species));
            }
        }

        Collections.shuffle(bag.tiles, random);
        return bag;
    }

    private void add(Biome biome, int count) {
        for (int i = 0; i < count; i++) {
            tiles.add(new TileDefinition(biome, null));
        }
    }

    public TileDefinition draw() {
        if (tiles.isEmpty()) return null;
        return tiles.remove(random.nextInt(tiles.size()));
    }

    /**
     * Нужен для будущих автодостроек биомов: лес расползся, река продолжилась,
     * болото разлилось. Сейчас этот метод уже готов, даже если паттерны расширения
     * пока почти не используются.
     */
    public TileDefinition drawBiome(Biome biome) {
        for (int i = 0; i < tiles.size(); i++) {
            TileDefinition def = tiles.get(i);
            if (def.biome == biome && def.spawnSpecies == null) {
                return tiles.remove(i);
            }
        }
        return null;
    }

    public int remaining() {
        return tiles.size();
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }
}
