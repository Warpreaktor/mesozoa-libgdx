package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;

import java.util.List;

/**
 * Основной тайл исследования.
 *
 * Именно MainTile игрок вытягивает из основного мешочка, когда разведчик входит
 * в неизвестную клетку. Основной тайл может иметь переходы автодостройки, но
 * не имеет спаун-маркера динозавра.
 */
public final class MainTile extends Tile {

    /** Направления автодостройки в базовой ориентации физического тайла. */
    public final List<Direction> baseExpansionDirections;

    /** Фактические направления автодостройки после выкладки и поворота тайла. */
    private List<Direction> expansionDirections;

    /**
     * Создаёт физический экземпляр основного тайла.
     *
     * @param biome биом тайла
     * @param expansionDirections направления автодостройки в базовой ориентации
     * @param hasBridge есть ли мост на тайле при генерации
     * @param roadDirections направления дорог в базовой ориентации
     * @param groundPassable стартовая проходимость тайла для наземных рейнджеров
     */
    public MainTile(
            Biome biome,
            List<Direction> expansionDirections,
            boolean hasBridge,
            List<Direction> roadDirections,
            boolean groundPassable
    ) {
        super(biome, hasBridge, roadDirections, groundPassable);
        this.baseExpansionDirections = List.copyOf(expansionDirections);
        this.expansionDirections = List.copyOf(expansionDirections);
    }

    @Override
    protected void onPlaced(int rotationQuarterTurns) {
        this.expansionDirections = rotatedDirections(baseExpansionDirections, rotationQuarterTurns);
    }

    @Override
    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }

    @Override
    public List<Direction> expansionDirections() {
        return expansionDirections;
    }
}
