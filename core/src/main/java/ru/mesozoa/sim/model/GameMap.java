package ru.mesozoa.sim.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
        map.placeTile(base, new Tile(Biome.LANDING, null, true));
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
