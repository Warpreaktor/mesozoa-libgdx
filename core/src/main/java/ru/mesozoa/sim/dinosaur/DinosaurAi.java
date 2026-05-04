package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.dinosaur.profile.DinosaurProfile;
import ru.mesozoa.sim.dinosaur.profile.DinosaurProfiles;
import ru.mesozoa.sim.map.PathFinder;
import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameSimulation;
import ru.mesozoa.sim.tile.Tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * AI и навигация динозавров.
 *
 * Класс отвечает за био-тропы, прогнозы движения и клетки, куда есть смысл
 * ставить ловушки. GameSimulation больше не должен выбирать, куда зверь пойдёт,
 * иначе он опять превратится в министерство всего на острове Крейтос.
 */
public final class DinosaurAi {

    private final GameSimulation simulation;

    /**
     * Создаёт сервис динозавровой логики.
     *
     * @param simulation текущая симуляция
     */
    public DinosaurAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Прогнозирует клетку, в которую динозавр придёт по био-тропе на ближайшем
     * ходу, если такой маршрут уже существует на открытой карте.
     *
     * @param dinosaur динозавр, для которого строится прогноз
     * @return конечная клетка ближайшего движения по био-тропе или Optional.empty()
     */
    public Optional<Point> predictDinosaurBioTrailDestination(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.captured || dinosaur.trapped || dinosaur.removed) {
            return Optional.empty();
        }

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = simulation.map.tile(dinosaur.position);

        if (currentTile == null) {
            return Optional.empty();
        }

        Biome nextBiome = profile.nextBiomeAfter(currentTile.biome, dinosaur.trailIndex);
        Optional<List<Point>> path = findDinosaurPathToReachableBiome(
                dinosaur.position,
                nextBiome,
                profile
        );

        if (path.isEmpty() || path.get().size() < 2) {
            return Optional.empty();
        }

        return Optional.of(path.get().get(path.get().size() - 1));
    }

    /**
     * Проверяет, может ли динозавр стоять на клетке.
     *
     * Неизведанная область, база и биомы вне био-тропы вида считаются
     * непроходимыми.
     *
     * @param point проверяемая клетка
     * @param profile профиль вида
     * @return true, если динозавр может находиться на этой клетке
     */
    public boolean canDinosaurStandOn(Point point, DinosaurProfile profile) {
        if (point == null || !simulation.map.isPlaced(point) || simulation.map.isBase(point)) {
            return false;
        }

        Biome biome = tileBiome(point);
        return profile.canEnter(biome);
    }

    /**
     * Ищет ближайший тайл следующего биома, до которого динозавр может дойти
     * за один ход в пределах своей ловкости.
     *
     * @param start стартовая клетка
     * @param targetBiome целевой биом
     * @param profile профиль вида
     * @return путь до целевого биома или пустой результат
     */
    public Optional<List<Point>> findDinosaurPathToReachableBiome(
            Point start,
            Biome targetBiome,
            DinosaurProfile profile
    ) {
        if (!canDinosaurStandOn(start, profile)) {
            return Optional.empty();
        }

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Point> previous = new HashMap<>();
        HashMap<Point, Integer> distance = new HashMap<>();

        queue.add(start);
        previous.put(start, null);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int currentDistance = distance.get(current);

            if (!current.equals(start) && tileBiome(current) == targetBiome) {
                return Optional.of(PathFinder.restorePath(previous, current));
            }

            if (currentDistance >= profile.agility()) {
                continue;
            }

            for (Point neighbor : current.neighbors4()) {
                if (previous.containsKey(neighbor)) {
                    continue;
                }

                if (!canDinosaurStandOn(neighbor, profile)) {
                    continue;
                }

                previous.put(neighbor, current);
                distance.put(neighbor, currentDistance + 1);
                queue.addLast(neighbor);
            }
        }

        return Optional.empty();
    }

    /**
     * Примерно оценивает, через сколько фаз динозавров хищник дойдёт до клетки.
     *
     * @param dinosaur хищник
     * @param target клетка с приманкой
     * @return число фаз динозавров или Integer.MAX_VALUE, если путь не найден
     */
    public int estimateDinosaurTurnsTo(Dinosaur dinosaur, Point target) {
        if (dinosaur == null || target == null) return Integer.MAX_VALUE;
        if (dinosaur.position.equals(target)) return 0;

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        int distance = dinosaurPathDistance(dinosaur.position, target, profile);
        if (distance == Integer.MAX_VALUE) return Integer.MAX_VALUE;

        Tile currentTile = simulation.map.tile(dinosaur.position);
        Tile targetTile = simulation.map.tile(target);
        if (currentTile == null || targetTile == null) return Integer.MAX_VALUE;

        int currentIndex = profile.trailIndexOf(currentTile.biome);
        int targetIndex = profile.trailIndexOf(targetTile.biome);
        if (currentIndex < 0 || targetIndex < 0) return Integer.MAX_VALUE;

        int routeSteps = Math.floorMod(targetIndex - currentIndex, dinosaur.species.bioTrail.size());
        if (routeSteps == 0) {
            routeSteps = 1;
        }

        int agility = Math.max(1, profile.agility());
        int distanceTurns = Math.max(1, (int) Math.ceil(distance / (double) agility));
        return Math.max(routeSteps, distanceTurns);
    }

    /**
     * Считает расстояние по биомам, доступным конкретному динозавру.
     *
     * @param start стартовая клетка
     * @param target целевая клетка
     * @param profile профиль вида
     * @return число шагов или Integer.MAX_VALUE
     */
    public int dinosaurPathDistance(Point start, Point target, DinosaurProfile profile) {
        if (!canDinosaurStandOn(start, profile) || !canDinosaurStandOn(target, profile)) {
            return Integer.MAX_VALUE;
        }

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Integer> distance = new HashMap<>();

        queue.add(start);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int currentDistance = distance.get(current);

            if (current.equals(target)) {
                return currentDistance;
            }

            for (Point neighbor : current.neighbors4()) {
                if (distance.containsKey(neighbor)) continue;
                if (!canDinosaurStandOn(neighbor, profile)) continue;

                distance.put(neighbor, currentDistance + 1);
                queue.addLast(neighbor);
            }
        }

        return Integer.MAX_VALUE;
    }

    /**
     * Перемещает динозавра по его биологической тропе.
     *
     * Если следующий биом маршрута недостижим, динозавр делает один случайный
     * шаг. Неизведанная область считается краем острова.
     *
     * @param dinosaur перемещаемый динозавр
     */
    public void moveByBioTrail(Dinosaur dinosaur) {
        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = simulation.map.tile(dinosaur.position);

        if (currentTile == null) {
            return;
        }

        Biome nextBiome = profile.nextBiomeAfter(currentTile.biome, dinosaur.trailIndex);
        Optional<List<Point>> path = findDinosaurPathToReachableBiome(
                dinosaur.position,
                nextBiome,
                profile
        );

        if (path.isPresent()) {
            moveDinosaurAlongBioTrailPath(dinosaur, profile, nextBiome, path.get());
            return;
        }

        moveDinosaurInRandomDirection(dinosaur, profile, nextBiome);
    }

    /**
     * Возвращает упорядоченный список клеток, куда имеет смысл ставить ловушку
     * для указанного динозавра.
     *
     * @param dinosaur динозавр, для которого планируется засада
     * @return клетки для ловушки в порядке убывания полезности
     */
    public List<Point> trapAmbushCandidatesFor(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.captured || dinosaur.trapped || dinosaur.removed) {
            return List.of();
        }

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = simulation.map.tile(dinosaur.position);
        if (currentTile == null) {
            return List.of();
        }

        Optional<Point> exactBioTrailDestination = predictDinosaurBioTrailDestination(dinosaur)
                .filter(point -> !point.equals(dinosaur.position))
                .filter(simulation.map::canPlaceTrap);

        if (exactBioTrailDestination.isPresent()) {
            return List.of(exactBioTrailDestination.get());
        }

        LinkedHashSet<Point> result = new LinkedHashSet<>();

        for (Point neighbor : dinosaur.position.neighbors4()) {
            if (!simulation.map.canPlaceTrap(neighbor)) continue;
            if (!canDinosaurStandOn(neighbor, profile)) continue;
            result.add(neighbor);
        }

        if (!result.isEmpty()) {
            return new ArrayList<>(result);
        }

        Biome nextBiome = profile.nextBiomeAfter(currentTile.biome, dinosaur.trailIndex);
        simulation.map.entries().stream()
                .filter(entry -> entry.getValue().biome == nextBiome)
                .map(entry -> entry.getKey())
                .filter(point -> !point.equals(dinosaur.position))
                .filter(simulation.map::canPlaceTrap)
                .sorted(Comparator.comparingInt(point -> dinosaur.position.manhattan(point)))
                .forEach(result::add);

        return new ArrayList<>(result);
    }

    /**
     * Прогнозирует несколько следующих клеток био-тропы динозавра без случайных шагов.
     *
     * @param dinosaur динозавр, чей маршрут прогнозируется
     * @param maxTurns максимум будущих фаз динозавров
     * @return список прогнозируемых клеток в порядке посещения
     */
    public List<Point> predictDinosaurBioTrailRoute(Dinosaur dinosaur, int maxTurns) {
        if (dinosaur == null || dinosaur.captured || dinosaur.trapped || dinosaur.removed || maxTurns <= 0) {
            return List.of();
        }

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Point position = dinosaur.position;
        int trailIndex = dinosaur.trailIndex;
        ArrayList<Point> route = new ArrayList<>();

        for (int i = 0; i < maxTurns; i++) {
            Tile currentTile = simulation.map.tile(position);
            if (currentTile == null) {
                break;
            }

            Biome nextBiome = profile.nextBiomeAfter(currentTile.biome, trailIndex);
            Optional<List<Point>> path = findDinosaurPathToReachableBiome(position, nextBiome, profile);
            if (path.isEmpty() || path.get().size() < 2) {
                break;
            }

            Point nextPosition = path.get().get(path.get().size() - 1);
            if (nextPosition.equals(position)) {
                break;
            }

            route.add(nextPosition);
            position = nextPosition;

            int newTrailIndex = profile.trailIndexOf(nextBiome);
            if (newTrailIndex >= 0) {
                trailIndex = newTrailIndex;
            }
        }

        return route;
    }

    private void moveDinosaurAlongBioTrailPath(
            Dinosaur dinosaur,
            DinosaurProfile profile,
            Biome nextBiome,
            List<Point> path
    ) {
        if (path.size() < 2) {
            return;
        }

        dinosaur.position = path.get(path.size() - 1);
        int newTrailIndex = profile.trailIndexOf(nextBiome);
        if (newTrailIndex >= 0) {
            dinosaur.trailIndex = newTrailIndex;
        }
    }

    private void moveDinosaurInRandomDirection(Dinosaur dinosaur, DinosaurProfile profile, Biome nextBiome) {
        Direction direction = Direction.values()[simulation.random.nextInt(Direction.values().length)];
        Point next = direction.from(dinosaur.position);

        if (canDinosaurStandOn(next, profile)) {
            dinosaur.position = next;
            Tile tile = simulation.map.tile(next);
            if (tile != null) {
                int newTrailIndex = profile.trailIndexOf(tile.biome);
                if (newTrailIndex >= 0) {
                    dinosaur.trailIndex = newTrailIndex;
                }
            }
            simulation.log(dinosaur.species.displayName + " #" + dinosaur.id
                    + " не нашёл рядом биом " + nextBiome.displayName
                    + " и шагнул " + direction + " на " + next);
            return;
        }

        simulation.log(dinosaur.species.displayName + " #" + dinosaur.id
                + " не нашёл доступный " + nextBiome.displayName
                + " и остался на месте: " + direction + " закрыт или непроходим");
    }

    private Biome tileBiome(Point point) {
        Tile tile = simulation.map.tile(point);
        return tile == null ? null : tile.biome;
    }
}
