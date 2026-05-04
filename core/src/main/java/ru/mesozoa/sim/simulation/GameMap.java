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
 * Центр карты определяется по BaseTile
 */
public final class GameMap {

    /**
     * Обычные тайлы местности, выложенные игроками на стол.
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
        return !isPlaced(p);
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
     * Возвращает следующий шаг обычного наземного рейнджера к цели.
     *
     * Если до самой цели пока нельзя добраться, метод выбирает ближайшую
     * достижимую клетку в сторону цели. Это позволяет рейнджеру подойти к
     * месту будущей постройки, не застревая на жадном шаге туда-сюда.
     */
    public Point stepGroundRangerToward(Point from, Point target) {
        List<Point> exactPath = findGroundRangerPath(from, target);
        if (!exactPath.isEmpty()) {
            return exactPath.size() < 2 ? from : exactPath.get(1);
        }

        Point closestReachable = nearestGroundRangerReachablePointTo(from, target);
        if (closestReachable == null || closestReachable.equals(from)) {
            return from;
        }

        List<Point> approachPath = findGroundRangerPath(from, closestReachable);
        if (approachPath.isEmpty() || approachPath.size() < 2) {
            return from;
        }

        return approachPath.get(1);
    }

    /**
     * Возвращает соседние клетки для обычного наземного рейнджера.
     *
     * Разведчик сюда не попадает: он перемещается по собственным правилам.
     */
    public List<Point> groundRangerReachableNeighbors(Point from) {
        ArrayList<Point> result = new ArrayList<>();

        if (!canGroundRangerStandOn(from)) {
            return result;
        }

        for (Point neighbor : from.neighbors4()) {
            if (canGroundRangerStandOn(neighbor)) {
                result.add(neighbor);
            }
        }

        return result;
    }

    /**
     * Проверяет, может ли обычный наземный рейнджер стоять на клетке.
     *
     * База проходима для рейнджеров. Для обычных тайлов используется состояние
     * самого тайла: мост или другие будущие эффекты могут менять проходимость
     * конкретной клетки без изменения её биома.
     */
    public boolean canGroundRangerStandOn(Point point) {
        if (point == null || !isPlaced(point)) return false;
        if (isBase(point)) return true;

        Tile tile = tile(point);
        return tile != null && tile.isGroundPassable();
    }


    /**
     * Проверяет, можно ли поставить ловушку на указанную клетку.
     *
     * База экспедиции считается непроходимой зоной для динозавров, поэтому
     * ловушки там не имеют игрового смысла и запрещены. Иначе инженер начинает
     * превращать штаб в капканный склад, что, конечно, по-своему эффективно,
     * но только против здравого смысла.
     *
     * @param point клетка для установки ловушки
     * @return true, если клетка открыта и не является базой
     */
    public boolean canPlaceTrap(Point point) {
        return point != null && isPlaced(point) && !isBase(point);
    }

    /**
     * Проверяет, можно ли разместить приманку на указанной клетке.
     *
     * Приманку нельзя класть на базе: динозавры не заходят на базовый тайл,
     * поэтому такая охота превращается в пикник у штаба, а не в засаду.
     *
     * @param point клетка, где охотник собирается использовать приманку
     * @return true, если клетка открыта и не является базой
     */
    public boolean canPlaceBait(Point point) {
        return point != null && isPlaced(point) && !isBase(point);
    }

    private List<Point> findGroundRangerPath(Point from, Point target) {
        if (from == null || target == null) return List.of();
        if (from.equals(target)) return canGroundRangerStandOn(from) ? List.of(from) : List.of();
        if (!canGroundRangerStandOn(from) || !canGroundRangerStandOn(target)) return List.of();

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Point> previous = new HashMap<>();

        queue.add(from);
        previous.put(from, null);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            if (current.equals(target)) break;

            for (Point neighbor : groundRangerReachableNeighbors(current)) {
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

    private Point nearestGroundRangerReachablePointTo(Point from, Point target) {
        if (from == null || target == null || !canGroundRangerStandOn(from)) return null;

        ArrayDeque<Point> queue = new ArrayDeque<>();
        LinkedHashSet<Point> visited = new LinkedHashSet<>();
        Point best = from;
        int bestDistance = from.manhattan(target);

        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int distance = current.manhattan(target);
            if (distance < bestDistance) {
                best = current;
                bestDistance = distance;
            }

            for (Point neighbor : groundRangerReachableNeighbors(current)) {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return best;
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


    /**
     * Проверяет, есть ли хотя бы одна дорога, соединяющая базу с соседним тайлом.
     *
     * База сама по себе не является дорогой. Для выезда джипа из стартовой клетки
     * инженер должен построить дорогу между базой и обычным тайлом.
     */
    public boolean hasRoadOutOfBase() {
        for (Point neighbor : base.neighbors4()) {
            if (!isPlaced(neighbor)) continue;
            if (canDriverMoveBetween(base, neighbor)) return true;
        }
        return false;
    }

    /**
     * Проверяет, можно ли построить дорогу между двумя соседними клетками.
     *
     * Дорога моделируется как связь между сторонами двух клеток, поэтому диагональные
     * соединения здесь запрещены. Дороги нельзя строить через горы и озёра.
     */
    public boolean canBuildRoadBetween(Point from, Point to) {
        if (from == null || to == null) return false;
        if (!isPlaced(from) || !isPlaced(to)) return false;

        Direction direction = directionBetween(from, to);
        if (direction == null || !isCardinal(direction)) return false;

        Direction opposite = direction.rotateClockwiseQuarterTurns(2);
        boolean fromIsBase = isBase(from);
        boolean toIsBase = isBase(to);

        if (fromIsBase && toIsBase) return false;

        if (fromIsBase || toIsBase) {
            Tile regularTile = fromIsBase ? tile(to) : tile(from);
            Direction roadDirectionFromRegularTile = fromIsBase ? opposite : direction;
            return canHoldRoad(regularTile) && !regularTile.hasRoadTo(roadDirectionFromRegularTile);
        }

        Tile fromTile = tile(from);
        Tile toTile = tile(to);
        if (!canHoldRoad(fromTile) || !canHoldRoad(toTile)) return false;

        return !fromTile.hasRoadTo(direction) && !toTile.hasRoadTo(opposite);
    }

    /**
     * Строит дорогу между двумя соседними клетками.
     *
     * Для связи с базой дорога записывается на обычный тайл в направлении базы,
     * потому что BaseTile не является обычным тайлом местности.
     */
    public boolean buildRoadBetween(Point from, Point to) {
        if (!canBuildRoadBetween(from, to)) return false;

        Direction direction = directionBetween(from, to);
        Direction opposite = direction.rotateClockwiseQuarterTurns(2);
        boolean fromIsBase = isBase(from);
        boolean toIsBase = isBase(to);

        if (fromIsBase) {
            return tile(to).addRoadTo(opposite);
        }

        if (toIsBase) {
            return tile(from).addRoadTo(direction);
        }

        return tile(from).addRoadTo(direction);
    }

    /**
     * Проверяет, можно ли построить мост на указанной клетке.
     *
     * Мост строится на реках и озёрах. На базе или обычной суше мост не нужен,
     * даже если инженеру очень хочется потратить стройматериалы.
     */
    public boolean canBuildBridge(Point point) {
        if (point == null || isBase(point)) return false;
        Tile tile = tile(point);
        if (tile == null || tile.hasBridge) return false;
        return tile.biome == Biome.RIVER || tile.biome == Biome.LAKE;
    }

    /**
     * Строит мост на указанной клетке.
     *
     * @return true, если мост был построен
     */
    public boolean buildBridge(Point point) {
        if (!canBuildBridge(point)) {
            return false;
        }
        return tile(point).addBridge();
    }


    /**
     * Проверяет, может ли инженер построить мост из своей клетки на указанном тайле.
     *
     * Мост можно поставить на текущей клетке инженера или на соседней клетке
     * по стороне. Это нужно прежде всего для озёр: инженер не может стоять на
     * озере без моста, но должен иметь возможность построить мост с берега.
     */
    public boolean canBuildBridgeFrom(Point engineerPosition, Point bridgePoint) {
        if (engineerPosition == null || bridgePoint == null) return false;
        if (!canGroundRangerStandOn(engineerPosition)) return false;
        if (!isPlaced(bridgePoint)) return false;

        boolean sameCell = engineerPosition.equals(bridgePoint);
        boolean cardinalNeighbor = engineerPosition.manhattan(bridgePoint) == 1;
        if (!sameCell && !cardinalNeighbor) return false;

        return canBuildBridge(bridgePoint);
    }

    /**
     * Строит мост из клетки инженера на указанном тайле.
     *
     * @return true, если мост был построен
     */
    public boolean buildBridgeFrom(Point engineerPosition, Point bridgePoint) {
        if (!canBuildBridgeFrom(engineerPosition, bridgePoint)) return false;
        return buildBridge(bridgePoint);
    }

    private boolean canHoldRoad(Tile tile) {
        return tile != null && tile.biome != Biome.MOUNTAIN && tile.biome != Biome.LAKE;
    }

    private boolean isCardinal(Direction direction) {
        return direction.dx == 0 || direction.dy == 0;
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

    /**
     * Количество открытых клеток карты, включая отдельный базовый тайл.
     */
    public int openedCount() {
        return placedTiles.size() + 1;
    }
}
