package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.model.Biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Мешочки физических тайлов:
 * - mainTiles — основная колода/мешочек исследования;
 * - extraTiles — дополнительная колода для автоматической достройки биомов.
 *
 * TileBag не хранит описания тайлов. Внутри лежат уже конкретные экземпляры Tile,
 * как реальные картонки в мешочке. Пока тайл в мешочке, у него нет координат.
 */
public final class TileBag {

    /**
     * Основной мешочек тайлов исследования.
     *
     * Из него разведчик вслепую вытягивает физический тайл и сразу выкладывает его
     * в неизвестную клетку, куда вошёл.
     */
    private final ArrayList<MainTile> mainTiles = new ArrayList<>();

    /**
     * Дополнительный мешочек тайлов автодостройки.
     *
     * Эти тайлы используются для закрытия переходов, нарисованных на основных
     * тайлах. Часть дополнительных тайлов может быть помечена силуэтом
     * динозавра: при автоматической выкладке такого тайла динозавр появляется
     * на карте.
     */
    private final ArrayList<ExtraTile> extraTiles = new ArrayList<>();

    /**
     * Источник случайности для перемешивания мешочков и вытягивания тайлов.
     */
    private final Random random;

    private TileBag(Random random) {
        this.random = random;
    }

    /**
     * Создаёт стандартный набор мешочков для новой партии.
     */
    public static TileBag createDefault(GameConfig config, Random random) {
        TileCatalog catalog = new TileCatalog(config, random);

        TileBag bag = new TileBag(random);
        bag.mainTiles.addAll(catalog.getMainTiles());
        bag.extraTiles.addAll(catalog.getExtraTiles());

        Collections.shuffle(bag.mainTiles, random);
        Collections.shuffle(bag.extraTiles, random);

        return bag;
    }

    /**
     * Вытягивает случайный основной тайл.
     */
    public MainTile draw() {
        if (mainTiles.isEmpty()) return null;
        return mainTiles.remove(random.nextInt(mainTiles.size()));
    }

    /**
     * Вытягивает дополнительный тайл указанного биома.
     */
    public ExtraTile drawExtraBiome(Biome biome) {
        for (int i = 0; i < extraTiles.size(); i++) {
            ExtraTile tile = extraTiles.get(i);
            if (tile.biome == biome) {
                return extraTiles.remove(i);
            }
        }

        return null;
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
