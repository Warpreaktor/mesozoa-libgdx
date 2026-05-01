package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.model.Biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Мешочки тайлов:
 * - mainTiles — основная колода/мешочек исследования;
 * - extraTiles — дополнительная колода для автоматической достройки биомов.
 *
 * TileBag не придумывает состав тайлов. Он берёт уже подготовленные списки из TileCatalog,
 * разворачивает TileBlueprint в конкретные TileDefinition и перемешивает их перед партией.
 */
public final class TileBag {

    /**
     * Основной мешочек тайлов исследования.
     *
     * Из него игроки вслепую вытягивают тайлы во время разведки новых участков острова.
     */
    private final ArrayList<TileDefinition> mainTiles = new ArrayList<>();

    /**
     * Дополнительный мешочек тайлов.
     *
     * Используется для автоматической достройки биомов по переходам на уже выложенных тайлах.
     */
    private final ArrayList<TileDefinition> extraTiles = new ArrayList<>();

    /**
     * Источник случайности для перемешивания мешочков и вытягивания тайлов.
     */
    private final Random random;

    private TileBag(Random random) {
        this.random = random;
    }

    /**
     * Создаёт стандартный набор мешочков для новой партии.
     *
     * TileCatalog сначала собирает основной набор тайлов,
     * случайно маркирует часть основных тайлов спаунами,
     * затем вычисляет количество дополнительных тайлов по количеству переходов.
     */
    public static TileBag createDefault(GameConfig config, Random random) {
        TileCatalog catalog = new TileCatalog(config, random);

        TileBag bag = new TileBag(random);

        bag.addMain(catalog.getMainTileBlueprints());
        bag.addExtra(catalog.getExtraTileBlueprints());

        Collections.shuffle(bag.mainTiles, random);
        Collections.shuffle(bag.extraTiles, random);

        return bag;
    }

    private void addMain(Iterable<TileBlueprint> blueprints) {
        for (TileBlueprint blueprint : blueprints) {
            addTo(mainTiles, blueprint);
        }
    }

    private void addExtra(Iterable<TileBlueprint> blueprints) {
        for (TileBlueprint blueprint : blueprints) {
            addTo(extraTiles, blueprint);
        }
    }

    private void addTo(ArrayList<TileDefinition> target, TileBlueprint blueprint) {
        if (blueprint.isEmpty()) return;

        for (int i = 0; i < blueprint.count; i++) {
            target.add(new TileDefinition(
                    blueprint.biome,
                    blueprint.spawnSpecies,
                    blueprint.expansionDirections
            ));
        }
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
