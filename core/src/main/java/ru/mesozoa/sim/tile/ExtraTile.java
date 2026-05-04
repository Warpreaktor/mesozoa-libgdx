package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/**
 * Дополнительный тайл автодостройки.
 *
 * ExtraTile не имеет переходов. Он выкладывается только автоматически как
 * продолжение перехода с MainTile. Зато именно ExtraTile может иметь силуэт
 * динозавра, то есть спаун-маркер.
 */
public final class ExtraTile extends Tile {

    /** Вид динозавра, отмеченный силуэтом на тайле. null означает отсутствие спауна. */
    private final Species spawnSpecies;

    /** Был ли уже использован спаун этого тайла. */
    private boolean spawnUsed;

    /**
     * Создаёт физический экземпляр дополнительного тайла.
     *
     * @param biome биом тайла
     * @param spawnSpecies вид динозавра для спауна или null
     * @param hasBridge есть ли мост на тайле при генерации
     * @param roadDirections направления дорог в базовой ориентации
     * @param groundPassable стартовая проходимость тайла для наземных рейнджеров
     */
    public ExtraTile(
            Biome biome,
            Species spawnSpecies,
            boolean hasBridge,
            List<Direction> roadDirections,
            boolean groundPassable
    ) {
        super(biome, hasBridge, roadDirections, groundPassable);
        this.spawnSpecies = spawnSpecies;
        this.spawnUsed = false;
    }

    @Override
    public boolean hasSpawn() {
        return spawnSpecies != null;
    }

    @Override
    public Species spawnSpecies() {
        return spawnSpecies;
    }

    @Override
    public boolean isSpawnUsed() {
        return spawnUsed;
    }

    @Override
    public void markSpawnUsed() {
        this.spawnUsed = true;
    }
}
