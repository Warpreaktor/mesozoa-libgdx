package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый класс физического тайла местности.
 *
 * Tile хранит только свойства, общие для всех обычных тайлов на карте:
 * биом, картинку, состояние размещения, дороги, мосты и текущую проходимость.
 * Конкретные особенности вынесены в наследников:
 * - MainTile содержит переходы автодостройки;
 * - ExtraTile содержит спаун-маркер динозавра.
 *
 * Базовый тайл экспедиции по-прежнему живёт отдельно в BaseTile и не входит
 * в мешочки. Потому что база — это штаб, а не картонка с болотом. Почти шок.
 */
public abstract class Tile {

    /** Биом обычного тайла местности: лес, луг, река, озеро, болото, горы или пойма. */
    public final Biome biome;

    /** Путь к картинке тайла в assets. */
    public final String imagePath;

    /** Направления дорог в базовой ориентации физического тайла. */
    public final List<Direction> baseRoadDirections;

    /** Флаг мостика на тайле. */
    public boolean hasBridge;

    /** Фактические направления дорог после выкладки и поворота тайла. */
    public List<Direction> roadDirections;

    /** Поворот физического тайла при выкладке. */
    public int rotationQuarterTurns;

    /** Координата тайла на столе. Пока тайл лежит в мешочке, значение равно null. */
    public Point position;

    /** Открыт ли тайл на игровом поле. */
    public boolean opened;

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

    protected Tile(
            Biome biome,
            boolean hasBridge,
            List<Direction> roadDirections,
            boolean groundPassable
    ) {
        this.biome = biome;
        this.imagePath = "tiles/" + biome.imagePath;
        this.baseRoadDirections = List.copyOf(roadDirections);
        this.hasBridge = hasBridge;
        this.roadDirections = new ArrayList<>(roadDirections);
        this.rotationQuarterTurns = 0;
        this.position = null;
        this.opened = false;
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
        this.roadDirections = new ArrayList<>(rotatedDirections(baseRoadDirections, this.rotationQuarterTurns));
        onPlaced(this.rotationQuarterTurns);
    }

    /**
     * Hook для наследников, которым нужно повернуть свои собственные направления
     * при выкладке тайла.
     *
     * @param rotationQuarterTurns фактический поворот тайла
     */
    protected void onPlaced(int rotationQuarterTurns) {
        // По умолчанию у тайла нет дополнительных направлений для поворота.
    }

    /**
     * Проверяет, содержит ли тайл переходы автодостройки.
     *
     * @return true только для MainTile с переходами
     */
    public boolean hasExpansion() {
        return false;
    }

    /**
     * Возвращает фактические направления автодостройки после поворота.
     *
     * @return список переходов; у ExtraTile всегда пустой список
     */
    public List<Direction> expansionDirections() {
        return List.of();
    }

    /**
     * Проверяет, содержит ли тайл спаун-маркер динозавра.
     *
     * @return true только для ExtraTile со спаун-маркером
     */
    public boolean hasSpawn() {
        return false;
    }

    /**
     * Возвращает вид динозавра, отмеченный на спаун-маркере.
     *
     * @return вид динозавра или null, если спауна нет
     */
    public Species spawnSpecies() {
        return null;
    }

    /**
     * Отмечает спаун тайла как использованный.
     *
     * У MainTile метод ничего не делает, потому что основные тайлы не спаунят
     * динозавров. Да, этому наконец выдали отдельную ответственность.
     */
    public void markSpawnUsed() {
        // Нет спаун-маркера — нечего отмечать.
    }

    /**
     * Проверяет, использован ли спаун-маркер тайла.
     *
     * @return true, если спаун уже сработал или если у тайла нет спауна
     */
    public boolean isSpawnUsed() {
        return true;
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

    protected List<Direction> rotatedDirections(List<Direction> directions, int rotationQuarterTurns) {
        return directions.stream()
                .map(direction -> direction.rotateClockwiseQuarterTurns(rotationQuarterTurns))
                .toList();
    }
}
