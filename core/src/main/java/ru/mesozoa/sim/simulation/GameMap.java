package ru.mesozoa.sim.simulation;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.tile.BaseTile;
import ru.mesozoa.sim.tile.Tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Динамическое игровое поле.
 *
 * Центр карты определяется тайлов BaseTile
 */
public final class GameMap {

    /**
     * Обычные тайлы местности, выложенные игроками на стол.
     *
     * Базовый тайл здесь не хранится, потому что это отдельный объект карты,
     * а не тайл биома из мешочка.
     */
    private final LinkedHashMap<Point, Tile> placedTiles = new LinkedHashMap<>();

    /**
     * Стартовый тайл экспедиции и компас игрового поля.
     */
    public final BaseTile baseTile;

    /**
     * Координата базы. Оставлена отдельным полем для удобства старого кода.
     */
    public final Point base;

    private GameMap(BaseTile baseTile) {
        this.baseTile = baseTile;
        this.base = baseTile.position;
    }

    /**
     * Создаёт карту с базовым тайлом в координате (0, 0).
     */
    public static GameMap createWithBase() {
        return new GameMap(new BaseTile());
    }

    public boolean inBounds(Point p) {
        return true;
    }

    /**
     * Проверяет, является ли координата координатой базового тайла.
     *
     * @param point проверяемая координата
     * @return true, если координата соответствует базе
     */
    public boolean isBase(Point point) {
        return base.equals(point);
    }

    /**
     * Проверяет, существует ли на карте клетка с указанной координатой.
     *
     * База считается размещённой клеткой, хотя не лежит в placedTiles.
     */
    public boolean isPlaced(Point p) {
        return isBase(p) || placedTiles.containsKey(p);
    }

    /**
     * Возвращает обычный тайл местности по координате.
     *
     * Для базы возвращается null, потому что база не имеет биома и не является Tile.
     */
    public Tile tile(Point p) {
        return placedTiles.get(p);
    }

    /**
     * Возвращает только обычные тайлы местности.
     *
     * База не входит в этот набор и отрисовывается отдельно.
     */
    public Collection<Map.Entry<Point, Tile>> entries() {
        return Collections.unmodifiableCollection(placedTiles.entrySet());
    }

    /**
     * Ручная выкладка тайла игроком.
     * Новый тайл должен прилегать к базе или к уже выложенным тайлам по стороне.
     */
    public boolean placeTile(Point p, Tile tile) {
        if (!canPlace(p)) return false;
        placedTiles.put(p, tile);
        return true;
    }

    public boolean canPlace(Point p) {
        return !isPlaced(p) && isAdjacentToPlacedTile(p);
    }

    /**
     * Автоматическая достройка по переходу.
     *
     * Диагональный переход касается исходного тайла только углом,
     * поэтому здесь нельзя требовать соседство по стороне.
     */
    public boolean placeExpansionTile(Point p, Tile tile) {
        if (!canPlaceExpansion(p)) return false;
        placedTiles.put(p, tile);
        return true;
    }

    public boolean canPlaceExpansion(Point p) {
        return !isPlaced(p) && inBounds(p);
    }

    private boolean isAdjacentToPlacedTile(Point p) {
        for (Point n : p.neighbors4()) {
            if (isPlaced(n)) return true;
        }
        return false;
    }

    /**
     * Возвращает свободные клетки, куда игрок может вручную положить новый тайл.
     *
     * Стартовая база тоже считается источником фронтира, но сама база не является
     * обычным тайлом карты.
     */
    public List<Point> availablePlacementPoints() {
        LinkedHashSet<Point> result = new LinkedHashSet<>();
        addFreeCardinalNeighbors(base, result);

        for (Point p : placedTiles.keySet()) {
            addFreeCardinalNeighbors(p, result);
        }
        return new ArrayList<>(result);
    }

    private void addFreeCardinalNeighbors(Point point, LinkedHashSet<Point> result) {
        for (Point n : point.neighbors4()) {
            if (!isPlaced(n)) result.add(n);
        }
    }

    /**
     * Возвращает выложенных соседей клетки по четырём сторонам.
     *
     * База входит в результат, если она соседствует с указанной клеткой.
     */
    public List<Point> placedNeighbors(Point p) {
        ArrayList<Point> result = new ArrayList<>();
        for (Point n : p.neighbors4()) {
            if (isPlaced(n)) result.add(n);
        }
        return result;
    }

    /**
     * Возвращает соседние клетки, куда может проехать водитель.
     *
     * Правило:
     * - база не считается дорогой или мостом;
     * - если один из двух концов пути — база, проезд возможен только по дороге;
     * - между обычными тайлами проезд возможен по дороге или через мостик.
     */
    public List<Point> driverReachableNeighbors(Point from) {
        ArrayList<Point> result = new ArrayList<>();

        if (!isPlaced(from)) {
            return result;
        }

        for (Direction direction : Direction.values()) {
            Point to = direction.from(from);
            if (isPlaced(to) && canDriverMoveBetween(from, to)) {
                result.add(to);
            }
        }

        return result;
    }

    public boolean hasDriverPath(Point from, Point target) {
        return nextDriverStepToward(from, target) != null;
    }

    /**
     * Возвращает длину кратчайшего водительского пути по дорогам и мостам.
     *
     * @param from стартовая клетка водителя
     * @param target целевая клетка
     * @return количество шагов до цели или Integer.MAX_VALUE, если пути нет
     */
    public int driverPathDistance(Point from, Point target) {
        List<Point> path = findDriverPath(from, target);
        if (path.isEmpty()) return Integer.MAX_VALUE;
        return path.size() - 1;
    }

    public Point stepDriverToward(Point from, Point target) {
        Point next = nextDriverStepToward(from, target);
        return next == null ? from : next;
    }

    private Point nextDriverStepToward(Point from, Point target) {
        List<Point> path = findDriverPath(from, target);
        if (path.isEmpty()) return null;
        if (path.size() < 2) return from;
        return path.get(1);
    }

    private List<Point> findDriverPath(Point from, Point target) {
        if (from == null || target == null) return List.of();
        if (from.equals(target)) return List.of(from);
        if (!isPlaced(from) || !isPlaced(target)) return List.of();

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Point> previous = new HashMap<>();

        queue.add(from);
        previous.put(from, null);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            if (current.equals(target)) break;

            for (Point neighbor : driverReachableNeighbors(current)) {
                if (previous.containsKey(neighbor)) continue;
                previous.put(neighbor, current);
                queue.addLast(neighbor);
            }
        }

        if (!previous.containsKey(target)) return List.of();

        ArrayList<Point> path = new ArrayList<>();
        Point step = target;
        while (step != null) {
            path.add(step);
            step = previous.get(step);
        }

        Collections.reverse(path);
        return path;
    }

    private boolean canDriverMoveBetween(Point from, Point to) {
        if (from == null || to == null) return false;
        if (!isPlaced(from) || !isPlaced(to)) return false;

        Direction direction = directionBetween(from, to);
        if (direction == null) return false;

        Direction opposite = direction.rotateClockwiseQuarterTurns(2);
        boolean fromIsBase = isBase(from);
        boolean toIsBase = isBase(to);

        if (fromIsBase && toIsBase) return false;

        if (fromIsBase || toIsBase) {
            Tile regularTile = fromIsBase ? tile(to) : tile(from);
            Direction roadDirectionFromRegularTile = fromIsBase ? opposite : direction;
            return regularTile != null && regularTile.hasRoadTo(roadDirectionFromRegularTile);
        }

        Tile fromTile = tile(from);
        Tile toTile = tile(to);

        if (fromTile == null || toTile == null) return false;

        boolean hasRoadBetweenTiles = fromTile.hasRoadTo(direction) || toTile.hasRoadTo(opposite);
        return fromTile.hasBridge || toTile.hasBridge || hasRoadBetweenTiles;
    }

    private Direction directionBetween(Point from, Point to) {
        int dx = Integer.compare(to.x, from.x);
        int dy = Integer.compare(to.y, from.y);

        if (to.x - from.x != dx || to.y - from.y != dy) return null;

        for (Direction direction : Direction.values()) {
            if (direction.dx == dx && direction.dy == dy) {
                return direction;
            }
        }

        return null;
    }

    public Point nearestPlacedBiome(Point from, Biome biome) {
        Point best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (var entry : placedTiles.entrySet()) {
            Point p = entry.getKey();
            Tile tile = entry.getValue();
            if (tile.biome == biome) {
                int distance = from.manhattan(p);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = p;
                }
            }
        }

        return best;
    }

    public Point nearestUnexploredFrontier(Point from) {
        Point best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Point p : availablePlacementPoints()) {
            int distance = from.manhattan(p);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = p;
            }
        }

        return best;
    }

    public Point nearestClosed(Point from) {
        return nearestUnexploredFrontier(from);
    }

    /**
     * Количество открытых клеток карты, включая отдельный базовый тайл.
     */
    public int openedCount() {
        return placedTiles.size() + 1;
    }

    public int minX() {
        return Math.min(base.x, placedTiles.keySet().stream().mapToInt(p -> p.x).min().orElse(base.x));
    }

    public int maxX() {
        return Math.max(base.x, placedTiles.keySet().stream().mapToInt(p -> p.x).max().orElse(base.x));
    }

    public int minY() {
        return Math.min(base.y, placedTiles.keySet().stream().mapToInt(p -> p.y).min().orElse(base.y));
    }

    public int maxY() {
        return Math.max(base.y, placedTiles.keySet().stream().mapToInt(p -> p.y).max().orElse(base.y));
    }

    public int widthInTiles() {
        return maxX() - minX() + 1;
    }

    public int heightInTiles() {
        return maxY() - minY() + 1;
    }
}
