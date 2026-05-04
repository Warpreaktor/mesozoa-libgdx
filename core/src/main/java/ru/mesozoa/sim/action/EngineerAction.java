package ru.mesozoa.sim.action;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.model.Trap;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Класс содержит практическую логику действия инженера.
 *
 * Инженер пытается выполнять задачи в том же порядке, в котором EngineerAi
 * оценивает его полезность: ловушки для S-динозавров, затем инфраструктура,
 * затем подтягивание к будущим зонам работ.
 */
public class EngineerAction {

    private final GameSimulation simulation;
    private final RangerActionExecutor rangerActionExecutor;

    public EngineerAction(GameSimulation simulation,
                          RangerActionExecutor rangerActionExecutor) {

        this.simulation = simulation;
        this.rangerActionExecutor = rangerActionExecutor;
    }

    /**
     * Выполняет одну активацию инженера.
     *
     * Инженер может переместиться на свои очки движения, а затем выполнить одну
     * инженерную работу: поставить ловушки, построить дорогу или мост.
     */
    public void action(PlayerState player, RangerPlan plan) {
        int movementPoints = plan.ranger().currentActionPoints();
        action(player, plan.target(), movementPoints);
        plan.ranger().spendActionPoints(movementPoints);
    }

    /**
     * Выполняет действие инженера с учётом цели, выбранной AI-планировщиком.
     *
     * Раньше AI выбирал инженера по конкретной причине, но само действие заново
     * искало цель и иногда забывало, зачем его вообще позвали. Теперь плановая
     * цель используется как главный ориентир для дороги, моста или перемещения.
     */
    private void action(PlayerState player, Point plannedTarget, int movementPoints) {
        if (hasTrappedDinosaurWithoutDriverAccess(player)) {
            if (tryBuildInfrastructure(player, plannedTarget)) {
                return;
            }

            boolean moved = moveEngineerTowardInfrastructureTarget(player, plannedTarget, movementPoints);
            if (tryBuildInfrastructure(player, plannedTarget)) {
                return;
            }

            if (!moved) {
                simulation.log("Инженер игрока " + player.id + " не смог приблизить дорогу к динозавру в ловушке");
            }
            return;
        }

        if (hasActionableTrapTarget(player)) {
            moveEngineerTowardTrapTarget(player, movementPoints);
            int placed = placeAvailableTrapsAroundEngineer(player);
            if (placed > 0) {
                simulation.log("Игрок " + player.id + " выставил ловушки: " + placed);
                return;
            }

            if (tryBuildInfrastructure(player, plannedTarget)) {
                return;
            }

            simulation.log("Инженер игрока " + player.id + " видел ловушечную цель, но не нашёл легальной клетки для ловушки");
            return;
        }

        if (tryBuildInfrastructure(player, plannedTarget)) {
            return;
        }

        boolean moved = moveEngineerTowardInfrastructureTarget(player, plannedTarget, movementPoints);

        if (tryBuildInfrastructure(player, plannedTarget)) {
            return;
        }

        if (!moved) {
            simulation.log("Инженер игрока " + player.id + " не нашёл полезного инженерного действия");
        }
    }

    /**
     * Проверяет, есть ли динозавр в ловушке игрока без готового дорожного вывоза.
     *
     * @param player игрок, чей инженер оценивает инфраструктурную задачу
     * @return true, если водитель пока не может забрать динозавра из ловушки
     */
    private boolean hasTrappedDinosaurWithoutDriverAccess(PlayerState player) {
        return nearestCapturedNeededDinosaurWithoutDriverAccess(player).isPresent();
    }

    /**
     * Проверяет, есть ли ловушечная цель, для которой уже существует легальная
     * клетка засады. Просто наличие Галлимимона на карте больше не считается
     * задачей инженера: иначе он выбирается AI, чешет репу и стоит рядом, как
     * памятник неудачному геймдизайну.
     *
     * @param player игрок, чей инженер ищет работу с ловушками
     * @return true, если инженер может поставить ловушку сейчас или двигаться к клетке засады
     */
    private boolean hasActionableTrapTarget(PlayerState player) {
        return activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer
                && bestTrapAmbushPoint(player, player.engineerRanger.position()).isPresent();
    }

    /** Подводит инженера к ближайшей клетке засады, если она пока вне зоны установки. */
    private void moveEngineerTowardTrapTarget(PlayerState player, int movementPoints) {
        Optional<Point> target = bestTrapAmbushPoint(player, player.engineerRanger.position());

        if (target.isPresent()) {
            Point trapPosition = target.get();
            if (!isInTrapPlacementRange(player.engineerRanger.position(), trapPosition)) {
                moveEngineerToward(player, trapPosition, movementPoints);
            }
            return;
        }

        moveEngineerToward(player, player.scoutRanger.position(), movementPoints);
    }

    /**
     * Пытается выполнить полезную инфраструктурную работу рядом с инженером.
     *
     * Инженер строит только из своей клетки: дорогу — между своей клеткой и
     * соседней клеткой по стороне, мост — на своей клетке.
     */
    private boolean tryBuildInfrastructure(PlayerState player, Point plannedTarget) {
        Optional<Point> target = bestInfrastructureTarget(player, plannedTarget);
        if (target.isEmpty()) return false;

        if (tryBuildBridgeTowardTarget(player, target.get())) {
            return true;
        }

        return tryBuildRoadTowardTarget(player, target.get());
    }

    /**
     * Пытается построить мост рядом с инженером в сторону инфраструктурной цели.
     *
     * Сначала проверяется текущая клетка инженера, затем соседняя клетка в прямом
     * направлении к цели, затем остальные соседние клетки по стороне.
     */
    private boolean tryBuildBridgeTowardTarget(PlayerState player, Point target) {
        if (simulation.map.canBuildBridgeFrom(player.engineerRanger.position(), player.engineerRanger.position())) {
            simulation.map.buildBridgeFrom(player.engineerRanger.position(), player.engineerRanger.position());
            simulation.log("Инженер игрока " + player.id + " построил мост");
            return true;
        }

        Point direct = player.engineerRanger.position().stepToward(target);
        if (simulation.map.canBuildBridgeFrom(player.engineerRanger.position(), direct)) {
            simulation.map.buildBridgeFrom(player.engineerRanger.position(), direct);
            simulation.log("Инженер игрока " + player.id + " построил мост");
            return true;
        }

        Optional<Point> bridgePoint = player.engineerRanger.position().neighbors4().stream()
                .filter(point -> simulation.map.canBuildBridgeFrom(player.engineerRanger.position(), point))
                .min(Comparator.comparingInt(point -> point.manhattan(target)));

        if (bridgePoint.isPresent()) {
            simulation.map.buildBridgeFrom(player.engineerRanger.position(), bridgePoint.get());
            simulation.log("Инженер игрока " + player.id + " построил мост");
            return true;
        }

        return false;
    }

    /**
     * Пытается построить дорогу из клетки инженера в соседнюю клетку по стороне.
     *
     * Кандидат выбирается среди соседних клеток, где дорогу действительно можно
     * построить, с предпочтением клетки, которая ближе к инфраструктурной цели.
     */
    private boolean tryBuildRoadTowardTarget(PlayerState player, Point target) {
        Optional<Point> nextRoadPoint = player.engineerRanger.position().neighbors4().stream()
                .filter(point -> simulation.map.canBuildRoadBetween(player.engineerRanger.position(), point))
                .min(Comparator.comparingInt(point -> point.manhattan(target)));

        if (nextRoadPoint.isEmpty()) {
            return false;
        }

        simulation.map.buildRoadBetween(player.engineerRanger.position(), nextRoadPoint.get());
        simulation.log("Инженер игрока " + player.id + " построил дорогу");
        return true;
    }

    /**
     * Перемещает инженера к ближайшей инфраструктурной цели.
     *
     * Если точная цель недостижима из-за воды или гор, инженер всё равно идёт
     * к ближайшей достижимой клетке, откуда сможет строить мост или продолжать
     * дорогу. Это убирает старую гениальную схему база-лес-база.
     */
    private boolean moveEngineerTowardInfrastructureTarget(PlayerState player, Point plannedTarget, int movementPoints) {
        Optional<Point> target = bestInfrastructureTarget(player, plannedTarget);
        return moveEngineerToward(player, target.orElse(player.scoutRanger.position()), movementPoints);
    }

    /**
     * Перемещает инженера по его собственным правилам проходимости.
     *
     * Инженер не заходит в болото, горы и озёра без моста. Для движения
     * используется BFS-логика карты, а не жадный шаг, который мог уводить его
     * вперёд и тут же возвращать обратно вторым очком движения.
     */
    private boolean moveEngineerToward(PlayerState player, Point target, int movementPoints) {
        Point before = player.engineerRanger.position();
        Point position = player.engineerRanger.position();

        for (int i = 0; i < movementPoints; i++) {
            if (position.equals(target)) break;

            Point next = simulation.map.stepGroundRangerToward(position, target);
            if (next.equals(position)) break;

            position = next;
        }

        player.setPosition(RangerRole.ENGINEER, position);

        if (!before.equals(position)) {
            simulation.log("Инженер игрока " + player.id + " переместился");
            return true;
        }

        return false;
    }

    /**
     * Выбирает практическую цель для строительства дорог и мостов.
     *
     * Приоритеты:
     * 1. нужный динозавр в ловушке без доступа водителя;
     * 2. видимая цель охотника без дороги;
     * 3. открытый нужный биом без дороги;
     * 4. разведчик.
     */
    private Optional<Point> bestInfrastructureTarget(PlayerState player, Point plannedTarget) {
        if (plannedTarget != null) {
            return Optional.of(plannedTarget);
        }

        Optional<Point> capturedTarget = nearestCapturedNeededDinosaurWithoutDriverAccess(player);
        if (capturedTarget.isPresent()) return capturedTarget;

        Optional<Point> hunterTarget = nearestNeededHunterTargetWithoutDriverAccess(player);
        if (hunterTarget.isPresent()) return hunterTarget;

        Optional<Point> biomeTarget = nearestUnconnectedNeededBiomePoint(player);
        if (biomeTarget.isPresent()) return biomeTarget;

        return Optional.ofNullable(player.scoutRanger.position());
    }

    /**
     * Выставляет доступные ловушки на прогнозные клетки прихода нужных S-динозавров.
     *
     * @param player игрок, чей инженер ставит ловушки
     * @return количество новых ловушек
     */
    private int placeAvailableTrapsAroundEngineer(PlayerState player) {
        int available = Math.max(0, simulation.inventoryConfig.maxTrapsPerPlayer - activeTrapCount(player));
        if (available == 0) {
            return 0;
        }

        int placed = 0;
        for (Point candidate : trapPlacementCandidates(player)) {
            if (placed >= available) break;
            if (!isUsableTrapPoint(player, candidate)) continue;

            player.traps.add(new Trap(player.id, candidate));
            placed++;
        }

        return placed;
    }

    private int activeTrapCount(PlayerState player) {
        return (int) player.traps.stream()
                .filter(trap -> trap.active)
                .count();
    }

    private boolean hasActiveTrapAt(PlayerState player, Point point) {
        return player.traps.stream()
                .anyMatch(trap -> trap.active && trap.position.equals(point));
    }

    /**
     * Проверяет, стоит ли живой динозавр на выбранной клетке прямо сейчас.
     *
     * @param point клетка для проверки
     * @return true, если клетка занята непойманным и не удалённым динозавром
     */
    private boolean hasLiveDinosaurAt(Point point) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .anyMatch(dinosaur -> dinosaur.position.equals(point));
    }

    /**
     * Выбирает ближайшую легальную клетку засады для нужных ловушечных динозавров.
     * Учитываются только клетки, куда динозавр может прийти, а не его текущая
     * позиция. Да, капкан под стоящим зверем всё ещё запрещён, трагедия для
     * поклонников мгновенной бюрократии.
     *
     * @param player игрок, который планирует ловушку
     * @param from позиция, от которой оценивается близость клетки
     * @return ближайшая клетка для ловушки
     */
    private Optional<Point> bestTrapAmbushPoint(PlayerState player, Point from) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.TRAP)
                .flatMap(dinosaur -> simulation.trapAmbushCandidatesFor(dinosaur).stream())
                .filter(point -> isUsableTrapPoint(player, point))
                .min(Comparator.comparingInt(point -> from == null ? 0 : point.manhattan(from)));
    }

    /**
     * Проверяет, можно ли игроку занять клетку новой ловушкой.
     *
     * @param player игрок-владелец будущей ловушки
     * @param point проверяемая клетка
     * @return true, если клетка свободна для новой ловушки
     */
    private boolean isUsableTrapPoint(PlayerState player, Point point) {
        return simulation.map.canPlaceTrap(point)
                && !hasActiveTrapAt(player, point)
                && !hasLiveDinosaurAt(point);
    }

    /**
     * Кандидаты для установки ловушек.
     *
     * Сначала используются точные прогнозы по био-тропе. Если следующий биом
     * сейчас недостижим, берутся соседние клетки, куда динозавр может случайно
     * шагнуть в рамках своего маршрута. Текущая клетка динозавра намеренно
     * исключена: ловушка под лапами — это не засада, а бюрократический телепорт.
     */
    private List<Point> trapPlacementCandidates(PlayerState player) {
        LinkedHashSet<Point> result = new LinkedHashSet<>();

        simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.TRAP)
                .sorted(Comparator.comparingInt(d -> d.position.manhattan(player.engineerRanger.position())))
                .forEach(dinosaur -> simulation.trapAmbushCandidatesFor(dinosaur).stream()
                        .filter(point -> isInTrapPlacementRange(player.engineerRanger.position(), point))
                        .filter(point -> isUsableTrapPoint(player, point))
                        .forEach(result::add));

        return new ArrayList<>(result);
    }

    /**
     * Ищет ближайшего нужного динозавра в ловушке, к которому ещё не подведена дорога.
     *
     * @param player игрок, чей инженер строит инфраструктуру
     * @return клетка динозавра в ловушке без водительского доступа
     */
    private Optional<Point> nearestCapturedNeededDinosaurWithoutDriverAccess(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> simulation.isTrappedByPlayer(dinosaur, player))
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineerRanger.position().manhattan(dinosaur.position)))
                .map(dinosaur -> dinosaur.position);
    }

    private Optional<Point> nearestNeededHunterTargetWithoutDriverAccess(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.trapped && !dinosaur.removed)
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> dinosaur.species.captureMethod == CaptureMethod.TRACKING
                        || dinosaur.species.captureMethod == CaptureMethod.HUNT)
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineerRanger.position().manhattan(dinosaur.position)))
                .map(dinosaur -> dinosaur.position);
    }


    private Optional<Point> nearestUnconnectedNeededBiomePoint(PlayerState player) {
        Set<Biome> neededBiomes = remainingNeededBiomes(player);

        return simulation.map.entries().stream()
                .filter(entry -> neededBiomes.contains(entry.getValue().biome))
                .filter(entry -> !simulation.map.hasDriverPath(simulation.map.base, entry.getKey()))
                .min(Comparator.comparingInt(entry -> player.engineerRanger.position().manhattan(entry.getKey())))
                .map(java.util.Map.Entry::getKey);
    }

    private Set<Biome> remainingNeededBiomes(PlayerState player) {
        EnumSet<Biome> result = EnumSet.noneOf(Biome.class);

        for (Species species : player.task) {
            if (player.captured.contains(species)) continue;
            result.add(species.spawnBiome);
            result.addAll(species.bioTrail);
        }

        return result;
    }

    private boolean isInTrapPlacementRange(Point engineerPosition, Point target) {
        int dx = Math.abs(engineerPosition.x - target.x);
        int dy = Math.abs(engineerPosition.y - target.y);
        return dx <= 1 && dy <= 1;
    }

}
