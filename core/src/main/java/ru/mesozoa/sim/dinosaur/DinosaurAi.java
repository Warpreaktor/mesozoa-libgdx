package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.dinosaur.profile.DinosaurProfile;
import ru.mesozoa.sim.dinosaur.profile.DinosaurProfiles;
import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.tile.Tile;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Мозг динозавров
 */
public class DinosaurAi {

    /**
     * Прогнозирует клетку, в которую динозавр придёт по био-тропе на ближайшем
     * ходу, если такой маршрут уже существует на открытой карте.
     * Метод намеренно не прогнозирует случайный шаг: ловушка должна ставиться
     * на понятную клетку маршрута, а не на «авось он туда споткнётся», потому
     *
     * @param dinosaur динозавр, для которого строится прогноз
     * @return конечная клетка ближайшего движения по био-тропе или Optional.empty()
     */
    public Optional<Point> predictDinosaurBioTrailDestination(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.captured || dinosaur.trapped || dinosaur.removed) {
            return Optional.empty();
        }

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = map.tile(dinosaur.position);

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
     * непроходимыми. Вот это и убирает старую механику «вышел за край карты и
     * исчез», которая выглядела так, будто динозавров уносит бухгалтерия.
     */
    private boolean canDinosaurStandOn(Point point, DinosaurProfile profile) {
        if (point == null || !map.isPlaced(point) || map.isBase(point)) {
            return false;
        }

        Biome biome = tileBiome(point);
        return profile.canEnter(biome);
    }

    /**
     * Ищет ближайший тайл следующего биома, до которого динозавр может дойти
     * за один ход в пределах своей ловкости.
     *
     * Поиск идёт только по уже открытым клеткам и только по биомам, входящим
     * в био-тропу конкретного вида.
     */
    private Optional<List<Point>> findDinosaurPathToReachableBiome(
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
                return Optional.of(restorePath(previous, current));
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
     * Учитывается не только расстояние, но и порядок био-тропы. Велоцитаурус из
     * Луга не должен считаться готовым прийти в Хвойный лес за один ход только
     * потому, что клетка рядом: сначала у него по маршруту Лиственный лес.
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

        Tile currentTile = map.tile(dinosaur.position);
        Tile targetTile = map.tile(target);
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

    /** Считает расстояние по биомам, доступным конкретному динозавру. */
    private int dinosaurPathDistance(Point start, Point target, DinosaurProfile profile) {
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
     * Новая логика намеренно не уводит динозавра в туман войны. Если целевой
     * биом маршрута не открыт или недостижим за один ход по уже открытой карте,
     * динозавр делает один случайный шаг. Неизведанная область при этом считается
     * краем острова: если случайное направление ведёт в закрытую или непроходимую
     * клетку, динозавр остаётся на месте. Да, зверь наконец-то перестал исчезать
     * сразу после появления, это был не динозавр, а фокусник-шарлатан.
     */
    private void moveByBioTrail(Dinosaur dinosaur) {
        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = map.tile(dinosaur.position);

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
     * Перемещает динозавра в найденный целевой биом био-тропы.
     *
     * @param dinosaur динозавр, который перемещается
     * @param profile видовой профиль динозавра
     * @param nextBiome целевой биом био-тропы
     * @param path кратчайший путь до подходящего тайла, включая стартовую клетку
     */
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

    /**
     * Делает один случайный шаг динозавра, если следующий биом био-тропы сейчас
     * недостижим за один ход.
     *
     * Если случайное направление ведёт в закрытую клетку, базу или биом вне
     * маршрута этого вида, динозавр стоит на месте.
     */
    private void moveDinosaurInRandomDirection(Dinosaur dinosaur, DinosaurProfile profile, Biome nextBiome) {
        Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
        Point next = direction.from(dinosaur.position);

        if (canDinosaurStandOn(next, profile)) {
            dinosaur.position = next;
            Tile tile = map.tile(next);
            if (tile != null) {
                int newTrailIndex = profile.trailIndexOf(tile.biome);
                if (newTrailIndex >= 0) {
                    dinosaur.trailIndex = newTrailIndex;
                }
            }
            log(dinosaur.species.displayName + " #" + dinosaur.id
                    + " не нашёл рядом биом " + nextBiome.displayName
                    + " и шагнул " + direction + " на " + next);
            return;
        }

        log(dinosaur.species.displayName + " #" + dinosaur.id
                + " не нашёл доступный " + nextBiome.displayName
                + " и остался на месте: " + direction + " закрыт или непроходим");
    }
}
