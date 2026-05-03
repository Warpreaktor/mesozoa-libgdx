package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;

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

    /**
     * Биом обычного тайла местности: лес, луг, река, озеро, болото, горы или пойма.
     */
    public final Biome biome;

    /**
     * Вид динозавра, который появляется при первой выкладке этого тайла.
     *
     * Если null, тайл не является спаун-тайлом.
     */
    public final Species spawnSpecies;

    /**
     * Путь к картинке тайла в assets.
     */
    public final String imagePath;

    /**
     * Направления автодостройки в базовой ориентации физического тайла.
     *
     * Эти значения не меняются после создания тайла и нужны, чтобы корректно
     * пересчитать фактические направления при выкладке с поворотом.
     */
    public final List<Direction> baseExpansionDirections;

    /**
     * Направления дорог в базовой ориентации физического тайла.
     */
    public final List<Direction> baseRoadDirections;

    /**
     * Фактические направления автодостройки после выкладки и поворота тайла.
     */
    public List<Direction> expansionDirections;

    /**
     * Флаг мостика на тайле.
     *
     * Если на тайле лежит мостик, водитель может проехать с этого тайла
     * на любой соседний выложенный тайл и заехать на этот тайл с любого
     * соседнего выложенного тайла.
     */
    public final boolean hasBridge;

    /**
     * Фактические направления дорог после выкладки и поворота тайла.
     *
     * Дорога физически лежит между двумя тайлами, поэтому она моделируется
     * как направление от текущего тайла к соседнему. Соединение считается
     * проезжим, если дорога указана хотя бы с одной стороны пары тайлов.
     */
    public List<Direction> roadDirections;

    /**
     * Поворот физического тайла при выкладке.
     * 0 — 0°, 1 — 90° по часовой, 2 — 180°, 3 — 270° по часовой.
     */
    public int rotationQuarterTurns;

    /**
     * Координата тайла на столе.
     *
     * Пока тайл лежит в мешочке, значение равно null.
     */
    public Point position;

    /**
     * Открыт ли тайл на игровом поле.
     */
    public boolean opened;

    /**
     * Был ли уже использован спаун этого тайла.
     */
    public boolean spawnUsed;

    public Tile(Biome biome, Species spawnSpecies) {
        this(biome, spawnSpecies, List.of(), false, List.of());
    }

    public Tile(Biome biome, Species spawnSpecies, List<Direction> expansionDirections) {
        this(biome, spawnSpecies, expansionDirections, false, List.of());
    }

    public Tile(
            Biome biome,
            Species spawnSpecies,
            List<Direction> expansionDirections,
            boolean hasBridge,
            List<Direction> roadDirections
    ) {
        this.biome = biome;
        this.spawnSpecies = spawnSpecies;
        this.imagePath = "tiles/" + biome.imagePath;
        this.baseExpansionDirections = List.copyOf(expansionDirections);
        this.baseRoadDirections = List.copyOf(roadDirections);
        this.expansionDirections = List.copyOf(expansionDirections);
        this.hasBridge = hasBridge;
        this.roadDirections = List.copyOf(roadDirections);
        this.rotationQuarterTurns = 0;
        this.position = null;
        this.opened = false;
        this.spawnUsed = false;
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
        this.roadDirections = rotatedDirections(baseRoadDirections, this.rotationQuarterTurns);
    }

    /**
     * Проверяет, размещён ли тайл на столе.
     */
    public boolean isPlaced() {
        return position != null;
    }

    public boolean hasSpawn() {
        return spawnSpecies != null;
    }

    public boolean hasExpansion() {
        return !expansionDirections.isEmpty();
    }

    public boolean hasRoad() {
        return !roadDirections.isEmpty();
    }

    public boolean hasRoadTo(Direction direction) {
        return roadDirections.contains(direction);
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
