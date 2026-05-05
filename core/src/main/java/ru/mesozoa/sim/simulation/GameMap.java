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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Динамическое игровое поле.
 * Центр карты определяется по BaseTile
 */
public final class GameMap {

    /**
     * Один конкретный шаг расширения дорожной сети, связанной с базой.
     *
     * @param workerPosition клетка, где должен стоять инженер
     * @param buildPoint клетка, на которую направлена стройка: сосед для дороги или клетка моста
     * @param bridge true, если шаг строит мост; false, если шаг строит дорогу
     */
    public record DriverNetworkBuildStep(Point workerPosition, Point buildPoint, boolean bridge) {
    }

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
     * <p>
     * База считается размещённой клеткой, хотя не лежит в placedTiles.
     */
    public boolean isPlaced(Point p) {
        return isBase(p) || placedTiles.containsKey(p);
    }

    /**
     * Возвращает обычный тайл местности по координате.
     * <p>
     * Для базы возвращается null, потому что база не имеет биома и не является Tile.
     */
    public Tile tile(Point p) {
        return placedTiles.get(p);
    }

    /**
     * Возвращает только обычные тайлы местности.
     * <p>
     * База не входит в этот набор и отрисовывается отдельно.
     */
    public Collection<Map.Entry<Point, Tile>> entries() {
        return Collections.unmodifiableCollection(placedTiles.entrySet());
    }

    /**
     * Ручная выкладка тайла игроком.
     * <p>
     * Новый основной тайл по-прежнему должен прилегать к базе или к уже выложенным
     * тайлам по стороне. Это правило раскладки карты отдельно от правила движения:
     * ходить можно по диагонали, но остров всё ещё собирается как нормальная
     * тайловая карта, а не как конфетти после офисного принтера.
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
     * <p>
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
     * <p>
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
     * Возвращает область карты, до которой обычная наземная команда может дойти от базы.
     * <p>
     * Разведчик не должен тянуть исследование от собственной удалённой позиции,
     * если остальная экспедиция туда не попадёт. Этот метод даёт AI карту
     * реального плацдарма команды: база, дороги не обязательны, но горы и озёра
     * без мостов по-прежнему разрывают связность. Да, география снова мешает
     * красивым планам, какая неожиданность.
     *
     * @return множество клеток, связанных с базой наземной проходимостью
     */
    public Set<Point> groundNetworkFromBase() {
        LinkedHashSet<Point> visited = new LinkedHashSet<>();
        ArrayDeque<Point> queue = new ArrayDeque<>();

        queue.add(base);
        visited.add(base);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            for (Point neighbor : groundRangerReachableNeighbors(current)) {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return visited;
    }

    /**
     * Считает, сколько соседей указанной клетки уже входит в наземный плацдарм от базы.
     * <p>
     * Для разведчика это главный признак полезного фронтира: если новая клетка
     * касается такой области, то найденный там динозавр хотя бы теоретически
     * будет нужен команде, а не одинокому биноклю где-то за горной стеной.
     *
     * @param point проверяемая свободная или уже открытая клетка
     * @return количество соседних клеток из наземной сети базы
     */
    public int groundNetworkSupportCount(Point point) {
        if (point == null) return 0;

        Set<Point> network = groundNetworkFromBase();
        int result = 0;
        for (Point neighbor : point.neighbors8()) {
            if (network.contains(neighbor)) {
                result++;
            }
        }
        return result;
    }

    /**
     * Проверяет, касается ли клетка наземной сети экспедиции от базы.
     *
     * @param point клетка для проверки
     * @return true, если рядом есть клетка, достижимая обычной командой от базы
     */
    public boolean touchesGroundNetworkFromBase(Point point) {
        return groundNetworkSupportCount(point) > 0;
    }

    /**
     * Возвращает расстояние от клетки до ближайшей точки наземной сети базы.
     * <p>
     * Метод использует восьминаправленное расстояние и нужен только как мягкий
     * штраф в AI разведчика. Это не pathfinding и не притворяется им, редкий
     * случай честности в вычислениях.
     *
     * @param point клетка, для которой ищется ближайшая опора команды
     * @return расстояние до сети или Integer.MAX_VALUE, если сети нет
     */
    public int distanceToGroundNetworkFromBase(Point point) {
        if (point == null) return Integer.MAX_VALUE;

        int best = Integer.MAX_VALUE;
        for (Point networkPoint : groundNetworkFromBase()) {
            best = Math.min(best, point.chebyshev(networkPoint));
        }
        return best;
    }

    /**
     * Ищет ближайшую к указанной клетке точку наземной сети базы.
     * <p>
     * Если разведчик вскрыл непроходимый тайл, его фигурка остаётся на ближайшей
     * рабочей кромке экспедиции, а не телепортируется на вершину горы, чтобы
     * потом оттуда торжественно портить план всем остальным.
     *
     * @param point ориентир, рядом с которым нужна опорная клетка
     * @return ближайшая точка наземной сети или пустой результат
     */
    public Optional<Point> nearestGroundNetworkPoint(Point point) {
        if (point == null) return Optional.empty();

        return groundNetworkFromBase().stream()
                .min(Comparator.comparingInt(point::chebyshev));
    }

    /**
     * Возвращает выложенных соседей клетки по восьми направлениям.
     * <p>
     * База входит в результат, если она соседствует с указанной клеткой. Для
     * перемещения рейнджеров диагональная клетка такая же соседняя, как и боковая.
     */
    public List<Point> placedNeighbors(Point p) {
        ArrayList<Point> result = new ArrayList<>();
        for (Point n : p.neighbors8()) {
            if (isPlaced(n)) result.add(n);
        }
        return result;
    }


    /**
     * Возвращает следующий шаг обычного наземного рейнджера к цели.
     * <p>
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
     * <p>
     * Охотник и инженер ходят по восьми направлениям. Разведчик сюда не попадает:
     * он перемещается по собственным правилам и не тратит очки на перелёт.
     */
    public List<Point> groundRangerReachableNeighbors(Point from) {
        ArrayList<Point> result = new ArrayList<>();

        if (!canGroundRangerStandOn(from)) {
            return result;
        }

        for (Direction direction : Direction.values()) {
            Point neighbor = direction.from(from);
            if (canGroundRangerStandOn(neighbor)) {
                result.add(neighbor);
            }
        }

        return result;
    }

    /**
     * Проверяет, может ли обычный наземный рейнджер стоять на клетке.
     * <p>
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
     * Возвращает длину кратчайшего пути обычного наземного рейнджера.
     * <p>
     * Метод нужен AI охотника, чтобы не планировать засаду на клетке, до которой
     * он физически не может дойти. Да, иногда даже героическому охотнику мешает
     * банальная река, эта жалкая мокрая бюрократия природы.
     *
     * @param from стартовая клетка
     * @param target целевая клетка
     * @return количество шагов или Integer.MAX_VALUE, если пути нет
     */
    public int groundRangerPathDistance(Point from, Point target) {
        List<Point> path = findGroundRangerPath(from, target);
        if (path.isEmpty()) return Integer.MAX_VALUE;
        return path.size() - 1;
    }

    /**
     * Возвращает следующий шаг охотника по следу M-травоядного.
     * <p>
     * Во время выслеживания охотник может пересекать любые уже открытые клетки,
     * включая воду, горы и другие биомы, недоступные обычному наземному движению.
     *
     * @param from текущая клетка охотника
     * @param target клетка целевого динозавра
     * @return следующий шаг или исходная клетка, если движение невозможно
     */
    public Point stepHunterTrackingToward(Point from, Point target) {
        List<Point> exactPath = findHunterTrackingPath(from, target);
        if (!exactPath.isEmpty()) {
            return exactPath.size() < 2 ? from : exactPath.get(1);
        }

        Point closestReachable = nearestHunterTrackingReachablePointTo(from, target);
        if (closestReachable == null || closestReachable.equals(from)) {
            return from;
        }

        List<Point> approachPath = findHunterTrackingPath(from, closestReachable);
        if (approachPath.isEmpty() || approachPath.size() < 2) {
            return from;
        }

        return approachPath.get(1);
    }

    /**
     * Возвращает длину пути охотника по следу M-травоядного.
     *
     * @param from стартовая клетка
     * @param target целевая клетка
     * @return количество шагов или Integer.MAX_VALUE, если путь не найден
     */
    public int hunterTrackingPathDistance(Point from, Point target) {
        List<Point> path = findHunterTrackingPath(from, target);
        if (path.isEmpty()) return Integer.MAX_VALUE;
        return path.size() - 1;
    }

    /**
     * Проверяет, может ли охотник стоять на клетке во время выслеживания.
     * <p>
     * След не даёт охотнику магическую амфибийно-альпинистскую лицензию: если
     * зверь ушёл на озеро без моста или в горы, цепочка должна оборваться.
     *
     * @param point проверяемая клетка
     * @return true, если охотник физически может стоять на этой клетке
     */
    public boolean canHunterTrackStandOn(Point point) {
        return canGroundRangerStandOn(point);
    }

    /**
     * Возвращает соседние клетки, доступные охотнику при движении по следу.
     *
     * @param from текущая клетка
     * @return список соседей по восьми направлениям
     */
    public List<Point> hunterTrackingReachableNeighbors(Point from) {
        ArrayList<Point> result = new ArrayList<>();
        if (!canHunterTrackStandOn(from)) {
            return result;
        }

        for (Direction direction : Direction.values()) {
            Point neighbor = direction.from(from);
            if (canHunterTrackStandOn(neighbor)) {
                result.add(neighbor);
            }
        }

        return result;
    }


    /**
     * Проверяет, можно ли поставить ловушку на указанную клетку.
     * <p>
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
     * <p>
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

        return restorePath(previous, target);
    }

    private List<Point> findHunterTrackingPath(Point from, Point target) {
        if (from == null || target == null) return List.of();
        if (from.equals(target)) return canHunterTrackStandOn(from) ? List.of(from) : List.of();
        if (!canHunterTrackStandOn(from) || !canHunterTrackStandOn(target)) return List.of();

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Point> previous = new HashMap<>();

        queue.add(from);
        previous.put(from, null);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            if (current.equals(target)) break;

            for (Point neighbor : hunterTrackingReachableNeighbors(current)) {
                if (previous.containsKey(neighbor)) continue;
                previous.put(neighbor, current);
                queue.addLast(neighbor);
            }
        }

        if (!previous.containsKey(target)) return List.of();

        return restorePath(previous, target);
    }

    private List<Point> restorePath(HashMap<Point, Point> previous, Point target) {
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
        int bestDistance = from.chebyshev(target);

        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int distance = current.chebyshev(target);
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

    private Point nearestHunterTrackingReachablePointTo(Point from, Point target) {
        if (from == null || target == null || !canHunterTrackStandOn(from)) return null;

        ArrayDeque<Point> queue = new ArrayDeque<>();
        LinkedHashSet<Point> visited = new LinkedHashSet<>();
        Point best = from;
        int bestDistance = from.chebyshev(target);

        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int distance = current.chebyshev(target);
            if (distance < bestDistance) {
                best = current;
                bestDistance = distance;
            }

            for (Point neighbor : hunterTrackingReachableNeighbors(current)) {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return best;
    }

    /**
     * Возвращает соседние клетки, куда может проехать водитель.
     * <p>
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

    /**
     * Возвращает клетки, связанные с базой действующей дорожной сетью водителя.
     *
     * База входит в результат даже если из неё пока не построена первая дорога.
     * Это позволяет инженеру корректно начинать сеть с базового тайла, а не
     * строить отдельные бессвязные обрывки дороги рядом с динозавром.
     *
     * @return множество клеток, до которых водитель может добраться из базы
     */
    public Set<Point> driverNetworkFromBase() {
        LinkedHashSet<Point> visited = new LinkedHashSet<>();
        ArrayDeque<Point> queue = new ArrayDeque<>();

        queue.add(base);
        visited.add(base);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            for (Point neighbor : driverReachableNeighbors(current)) {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return visited;
    }

    /**
     * Выбирает следующий строительный шаг, который расширит связанную с базой
     * дорожную сеть в сторону цели.
     *
     * Метод принципиально смотрит только на frontier текущей сети от базы.
     * Поэтому инженер больше не строит декоративные дороги у пойманного
     * динозавра, которые водитель не может использовать. Чудо инженерной мысли:
     * дорога наконец должна соединяться с другой дорогой.
     *
     * @param target клетка, к которой нужно подвести водителя
     * @return лучший шаг строительства или пустой результат, если сеть нельзя расширить
     */
    public Optional<DriverNetworkBuildStep> bestDriverNetworkBuildStepToward(Point target) {
        if (target == null || !isPlaced(target) || hasDriverPath(base, target)) {
            return Optional.empty();
        }

        Set<Point> network = driverNetworkFromBase();
        ArrayList<DriverNetworkBuildStep> candidates = new ArrayList<>();

        for (Point from : network) {
            collectRoadExpansionCandidates(from, network, candidates);
            collectBridgeExpansionCandidates(from, network, target, candidates);
        }

        return candidates.stream()
                .min(Comparator
                        .comparingInt((DriverNetworkBuildStep step) -> projectedNetworkDistance(step, network, target))
                        .thenComparingInt(step -> step.workerPosition().chebyshev(target))
                        .thenComparingInt(step -> step.buildPoint().chebyshev(target)));
    }

    /**
     * Проверяет, можно ли прямо сейчас выполнить выбранный шаг расширения сети.
     *
     * @param step шаг строительства из base-connected сети
     * @return true, если дорога или мост всё ещё могут быть построены
     */
    public boolean canBuildDriverNetworkStep(DriverNetworkBuildStep step) {
        if (step == null) return false;
        if (step.bridge()) {
            return canBuildBridgeFrom(step.workerPosition(), step.buildPoint());
        }
        return canBuildRoadBetween(step.workerPosition(), step.buildPoint());
    }

    /**
     * Выполняет выбранный шаг расширения дорожной сети водителя.
     *
     * @param step шаг строительства из base-connected сети
     * @return true, если постройка была выполнена
     */
    public boolean buildDriverNetworkStep(DriverNetworkBuildStep step) {
        if (!canBuildDriverNetworkStep(step)) return false;
        if (step.bridge()) {
            return buildBridgeFrom(step.workerPosition(), step.buildPoint());
        }
        return buildRoadBetween(step.workerPosition(), step.buildPoint());
    }

    /**
     * Добавляет кандидаты дорожного расширения из одной клетки текущей сети.
     *
     * @param from клетка уже связанной с базой сети
     * @param network текущая сеть водителя от базы
     * @param candidates список, куда добавляются найденные шаги
     */
    private void collectRoadExpansionCandidates(
            Point from,
            Set<Point> network,
            ArrayList<DriverNetworkBuildStep> candidates
    ) {
        for (Direction direction : Direction.values()) {
            Point to = direction.from(from);
            if (network.contains(to)) continue;
            if (canBuildRoadBetween(from, to)) {
                candidates.add(new DriverNetworkBuildStep(from, to, false));
            }
        }
    }

    /**
     * Добавляет кандидаты мостового расширения из одной клетки текущей сети.
     *
     * @param from клетка уже связанной с базой сети
     * @param network текущая сеть водителя от базы
     * @param target цель, к которой тянется сеть
     * @param candidates список, куда добавляются найденные шаги
     */
    private void collectBridgeExpansionCandidates(
            Point from,
            Set<Point> network,
            Point target,
            ArrayList<DriverNetworkBuildStep> candidates
    ) {
        if (canBuildBridgeFrom(from, from) && bridgeWouldExpandNetwork(from, network, target)) {
            candidates.add(new DriverNetworkBuildStep(from, from, true));
        }

        for (Direction direction : Direction.values()) {
            Point to = direction.from(from);
            if (canBuildBridgeFrom(from, to) && bridgeWouldExpandNetwork(to, network, target)) {
                candidates.add(new DriverNetworkBuildStep(from, to, true));
            }
        }
    }

    /**
     * Оценивает, насколько выбранный шаг приблизит связанную сеть к цели.
     *
     * @param step проверяемый шаг строительства
     * @param network текущая сеть водителя от базы
     * @param target цель, к которой строится путь
     * @return восьминаправленное расстояние от расширенной части сети до цели
     */
    private int projectedNetworkDistance(DriverNetworkBuildStep step, Set<Point> network, Point target) {
        if (step.bridge() && network.contains(step.buildPoint())) {
            return bestUnlockedNeighborDistance(step.buildPoint(), network, target);
        }
        return step.buildPoint().chebyshev(target);
    }

    /**
     * Проверяет, даст ли мост новую достижимую клетку для водителя.
     *
     * @param bridgePoint клетка будущего моста
     * @param network текущая сеть водителя от базы
     * @param target цель, к которой строится путь
     * @return true, если мост добавит новую клетку или приблизит сеть к цели
     */
    private boolean bridgeWouldExpandNetwork(Point bridgePoint, Set<Point> network, Point target) {
        if (!isPlaced(bridgePoint)) return false;
        if (!network.contains(bridgePoint)) return true;
        return bestUnlockedNeighborDistance(bridgePoint, network, target) < Integer.MAX_VALUE;
    }

    /**
     * Ищет ближайшего к цели соседа, который станет доступен после моста.
     *
     * @param bridgePoint клетка будущего моста
     * @param network текущая сеть водителя от базы
     * @param target цель, к которой строится путь
     * @return расстояние до цели или Integer.MAX_VALUE, если мост ничего не откроет
     */
    private int bestUnlockedNeighborDistance(Point bridgePoint, Set<Point> network, Point target) {
        int best = Integer.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            Point neighbor = direction.from(bridgePoint);
            if (!isPlaced(neighbor) || network.contains(neighbor)) continue;
            best = Math.min(best, neighbor.chebyshev(target));
        }
        return best;
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
        for (Point neighbor : base.neighbors8()) {
            if (!isPlaced(neighbor)) continue;
            if (canDriverMoveBetween(base, neighbor)) return true;
        }
        return false;
    }

    /**
     * Проверяет, можно ли построить дорогу между двумя соседними клетками.
     *
     * Дорога моделируется как связь между соседними клетками. Так как рейнджеры и
     * водитель могут двигаться по диагоналям, дорожная связь тоже может идти в
     * диагональную соседнюю клетку. Дороги нельзя строить через горы и озёра.
     */
    public boolean canBuildRoadBetween(Point from, Point to) {
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
     * <p>
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
     *<p>
     * Мост можно поставить на текущей клетке инженера или на любой соседней
     * клетке, включая диагональную. Это нужно прежде всего для озёр: инженер не
     * может стоять на озере без моста, но должен иметь возможность построить мост
     * с берега, не изображая привязанность к ортогональности как к семейной реликвии.
     */
    public boolean canBuildBridgeFrom(Point engineerPosition, Point bridgePoint) {
        if (engineerPosition == null || bridgePoint == null) return false;
        if (!canGroundRangerStandOn(engineerPosition)) return false;
        if (!isPlaced(bridgePoint)) return false;

        if (!engineerPosition.isSameOrAdjacent8(bridgePoint)) return false;

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
                int distance = from.chebyshev(p);
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
            int distance = from.chebyshev(p);
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
