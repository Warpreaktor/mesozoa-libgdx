package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;

import java.util.ArrayList;
import java.util.List;

/**
 * Физический экземпляр тайла.
 *
 * Один объект Tile соответствует одной конкретной картонке из мешочка.
 * Пока тайл лежит в мешочке, он не имеет координат и не считается размещённым.
 * После выкладки на стол у него фиксируются позиция, поворот, направления переходов
 * и дороги в фактической ориентации.
 */
public final class Tile {

    /** Биом обычного тайла местности: лес, луг, река, озеро, болото, горы или пойма. */
    public final Biome biome;

    /** Вид динозавра, который появляется при первой выкладке этого тайла. */
    public final Species spawnSpecies;

    /** Путь к картинке тайла в assets. */
    public final String imagePath;

    /** Направления автодостройки в базовой ориентации физического тайла. */
    public final List<Direction> baseExpansionDirections;

    /** Направления дорог в базовой ориентации физического тайла. */
    public final List<Direction> baseRoadDirections;

    /** Фактические направления автодостройки после выкладки и поворота тайла. */
    public List<Direction> expansionDirections;

    /**
     * Флаг мостика на тайле.
     */
    public boolean hasBridge;

    /** Фактические направления дорог после выкладки и поворота тайла. */
    public List<Direction> roadDirections;

    /** Поворот физического тайла при выкладке. */
    public int rotationQuarterTurns;

    /** Координата тайла на столе. Пока тайл лежит в мешочке, значение равно null. */
    public Point position;

    /** Открыт ли тайл на игровом поле. */
    public boolean opened;

    /** Был ли уже использован спаун этого тайла. */
    public boolean spawnUsed;

    /**
     * Может ли обычный наземный рейнджер стоять на этом тайле.
     *
     * Это состояние конкретного тайла, а не свойство биома.
     * Например, тайл реки по умолчанию непроходим, но после постройки моста
     * становится проходимым, оставаясь при этом биомом RIVER.
     *
     * Разведчик это ограничение игнорирует.
     */
    private boolean groundPassable;

    public Tile(
            Biome biome,
            Species spawnSpecies,
            List<Direction> expansionDirections,
            boolean hasBridge,
            List<Direction> roadDirections,
            boolean groundPassable
    ) {
        this.biome = biome;
        this.spawnSpecies = spawnSpecies;
        this.imagePath = "tiles/" + biome.imagePath;
        this.baseExpansionDirections = List.copyOf(expansionDirections);
        this.baseRoadDirections = List.copyOf(roadDirections);
        this.expansionDirections = List.copyOf(expansionDirections);
        this.hasBridge = hasBridge;
        this.roadDirections = new ArrayList<>(roadDirections);
        this.rotationQuarterTurns = 0;
        this.position = null;
        this.opened = false;
        this.spawnUsed = false;
        this.groundPassable = groundPassable;
    }

    /**
     * Фиксирует размещение физического тайла на столе.
     *
     * @param position координата на игровой карте
     * @param rotationQuarterTurns количество поворотов по 90° по часовой стрелке
     */
    public void place(Point position, int rotationQuarterTurns) {
        this.position = position;
        this.opened = true;
        this.rotationQuarterTurns = Math.floorMod(rotationQuarterTurns, 4);
        this.expansionDirections = rotatedDirections(baseExpansionDirections, this.rotationQuarterTurns);
        this.roadDirections = new ArrayList<>(rotatedDirections(baseRoadDirections, this.rotationQuarterTurns));
    }

    public boolean hasSpawn() {
        return spawnSpecies != null;
    }

    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }

    public boolean hasRoadTo(Direction direction) {
        return roadDirections.contains(direction);
    }

    /**
     * Добавляет направление дороги на уже выложенный тайл.
     *
     * @param direction направление дороги к соседней клетке
     * @return true, если дорога была добавлена; false, если она уже существовала
     */
    public boolean addRoadTo(Direction direction) {
        if (roadDirections.contains(direction)) return false;
        roadDirections.add(direction);
        return true;
    }

    /**
     * Добавляет мост на тайл.
     *
     * Мост меняет состояние конкретного тайла: он остаётся своим биомом,
     * но становится проходимым для наземных рейнджеров и доступным для дорожной логики.
     */
    public boolean addBridge() {
        this.hasBridge = true;
        this.groundPassable = true;
        return true;
    }

    /**
     * Проверяет, может ли обычный наземный рейнджер стоять на этом тайле.
     *
     * Используется охотником, инженером и другими наземными специалистами.
     * Разведчик ходит по отдельным правилам.
     *
     * @return true, если тайл проходим для наземного рейнджера
     */
    public boolean isGroundPassable() {
        return groundPassable;
    }

    /**
     * LibGDX вращает SpriteBatch против часовой для положительных градусов.
     * А rotationQuarterTurns у нас задан по часовой, поэтому знак отрицательный.
     */
    public float rotationDegreesForRendering() {
        return -90f * rotationQuarterTurns;
    }

    private List<Direction> rotatedDirections(List<Direction> directions, int rotationQuarterTurns) {
        return directions.stream()
                .map(direction -> direction.rotateClockwiseQuarterTurns(rotationQuarterTurns))
                .toList();
    }
}
