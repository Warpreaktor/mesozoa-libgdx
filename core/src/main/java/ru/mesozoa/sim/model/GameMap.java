package ru.mesozoa.sim.model;

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
 * На столе существуют только те тайлы, которые игроки уже выложили.
 */
public final class GameMap {

    private final LinkedHashMap<Point, Tile> placedTiles = new LinkedHashMap<>();
    public final Point base;

    private GameMap(Point base) {
        this.base = base;
    }

    public static GameMap createWithLanding() {
        Point base = new Point(0, 0);
        GameMap map = new GameMap(base);
        Tile landing = new Tile(Biome.LANDING, null, List.of(), false, List.of());
        landing.place(base, 0);
        map.placeTile(base, landing);
        return map;
    }

    public boolean inBounds(Point p) {
        return true;
    }

    public boolean isPlaced(Point p) {
        return placedTiles.containsKey(p);
    }

    public boolean isOpen(Point p) {
        return isPlaced(p);
    }

    public Tile tile(Point p) {
        return placedTiles.get(p);
    }

    public Collection<Map.Entry<Point, Tile>> entries() {
        return Collections.unmodifiableCollection(placedTiles.entrySet());
    }

    public Collection<Point> points() {
        return Collections.unmodifiableSet(placedTiles.keySet());
    }

    /**
     * Ручная выкладка тайла игроком.
     * Новый тайл должен прилегать к уже выложенным по стороне.
     */
    public boolean placeTile(Point p, Tile tile) {
        if (!canPlace(p)) return false;
        placedTiles.put(p, tile);
        return true;
    }

    public boolean canPlace(Point p) {
        return !placedTiles.containsKey(p) && (placedTiles.isEmpty() || isAdjacentToPlacedTile(p));
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
        return !placedTiles.containsKey(p) && inBounds(p);
    }

    private boolean isAdjacentToPlacedTile(Point p) {
        for (Point n : p.neighbors4()) {
            if (placedTiles.containsKey(n)) return true;
        }
        return false;
    }

    public List<Point> availablePlacementPoints() {
        LinkedHashSet<Point> result = new LinkedHashSet<>();
        for (Point p : placedTiles.keySet()) {
            for (Point n : p.neighbors4()) {
                if (!placedTiles.containsKey(n)) result.add(n);
            }
        }
        return new ArrayList<>(result);
    }

    public List<Point> placedNeighbors(Point p) {
        ArrayList<Point> result = new ArrayList<>();
        for (Point n : p.neighbors4()) {
            if (placedTiles.containsKey(n)) result.add(n);
        }
        return result;
    }

    /**
     * Возвращает соседние клетки, куда может проехать водитель.
     *
     * Правило:
     * - если на одном из двух тайлов лежит мостик, проезд разрешён;
     * - если между двумя тайлами есть дорога, проезд разрешён.
     *
     * База не считается дорогой или мостом. Чтобы водитель выехал с базы,
     * из неё должна быть проложена обычная дорога.
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
        Tile fromTile = tile(from);
        Tile toTile = tile(to);

        if (fromTile == null || toTile == null) return false;

        Direction direction = directionBetween(from, to);
        if (direction == null) return false;

        Direction opposite = direction.rotateClockwiseQuarterTurns(2);
        boolean hasRoadBetweenTiles = fromTile.hasRoadTo(direction) || toTile.hasRoadTo(opposite);

        if (from.equals(base) || to.equals(base)) {
            return hasRoadBetweenTiles;
        }

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

    public Point nearestOpenedBiome(Point from, Biome biome) {
        return nearestPlacedBiome(from, biome);
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

    public int openedCount() {
        return placedTiles.size();
    }

    public int minX() {
        return placedTiles.keySet().stream().mapToInt(p -> p.x).min().orElse(0);
    }

    public int maxX() {
        return placedTiles.keySet().stream().mapToInt(p -> p.x).max().orElse(0);
    }

    public int minY() {
        return placedTiles.keySet().stream().mapToInt(p -> p.y).min().orElse(0);
    }

    public int maxY() {
        return placedTiles.keySet().stream().mapToInt(p -> p.y).max().orElse(0);
    }

    public int widthInTiles() {
        return maxX() - minX() + 1;
    }

    public int heightInTiles() {
        return maxY() - minY() + 1;
    }
}
