package ru.mesozoa.sim.model;

import ru.mesozoa.sim.config.GameConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Мешочки тайлов:
 * - mainTiles — основная колода/мешочек исследования;
 * - extraTiles — дополнительная колода для автоматической достройки биомов.
 *
 * TileBag не придумывает состав тайлов. Он разворачивает статический TileCatalog
 * в конкретные TileDefinition и перемешивает их перед партией.
 */
public final class TileBag {
    private final ArrayList<TileDefinition> mainTiles = new ArrayList<>();
    private final ArrayList<TileDefinition> extraTiles = new ArrayList<>();
    private final Random random;

    private TileBag(Random random) {
        this.random = random;
    }

    public static TileBag createDefault(GameConfig config, Random random) {
        TileBag bag = new TileBag(random);

        bag.addMain(TileCatalog.mainTileBlueprints());
        bag.addMain(TileCatalog.spawnTileBlueprints(config.spawnTiles));
        bag.addExtra(TileCatalog.extraTileBlueprints());

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
