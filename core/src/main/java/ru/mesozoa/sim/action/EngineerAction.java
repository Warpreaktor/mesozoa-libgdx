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
import ru.mesozoa.sim.simulation.GameMap;
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

        if (hasStaleTrapLayout(player)) {
            boolean done = recoverStaleTrap(player, plannedTarget, movementPoints);
            if (done) {
                return;
            }
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
     * Проверяет, занял ли игрок весь лимит ловушек старой раскладкой.
     *
     * Если все ловушки стоят на карте, но ни одна не перекрывает прогнозный
     * маршрут нужного S-динозавра, инженер должен снять одну из них, а не
     * ждать 200 раундов, пока Галлимимон сам придёт на бюрократическую мину.
     *
     * @param player игрок, чьи ловушки проверяются
     * @return true, если нужно переставить пустую ловушку
     */
    private boolean hasStaleTrapLayout(PlayerState player) {
        return staleTrapToRecover(player, player.engineerRanger.position()).isPresent();
    }

    /**
     * Снимает устаревшую ловушку или двигает инженера к ней.
     *
     * @param player игрок, чей инженер переставляет ловушки
     * @param plannedTarget цель из AI-плана, обычно позиция старой ловушки
     * @param movementPoints доступные очки движения инженера
     * @return true, если инженер снял ловушку, поставил новую или хотя бы сдвинулся к старой
     */
    private boolean recoverStaleTrap(PlayerState player, Point plannedTarget, int movementPoints) {
        Optional<Trap> targetTrap = staleTrapToRecover(player, plannedTarget);
        if (targetTrap.isEmpty()) {
            return false;
        }

        Trap trap = targetTrap.get();
        if (!isInTrapPlacementRange(player.engineerRanger.position(), trap.position)) {
            boolean moved = moveEngineerToward(player, trap.position, movementPoints);
            if (!isInTrapPlacementRange(player.engineerRanger.position(), trap.position)) {
                return moved;
            }
        }

        trap.active = false;
        trap.trappedDinosaurId = 0;
        simulation.log("Инженер игрока " + player.id + " снял старую ловушку на " + trap.position);

        int placed = placeAvailableTrapsAroundEngineer(player);
        if (placed > 0) {
            simulation.log("Игрок " + player.id + " переставил ловушки: " + placed);
        }
        return true;
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
     * Инфраструктура строится только как продолжение сети, уже связанной с базой.
     * Иначе водитель всё равно не сможет доехать до динозавра, а инженер будет
     * производить художественные дорожные инсталляции в лесу. Очень атмосферно,
     * но бесполезно.
     *
     * @param player игрок, чей инженер строит
     * @param plannedTarget целевая клетка из AI-плана
     * @return true, если дорога или мост были построены
     */
    private boolean tryBuildInfrastructure(PlayerState player, Point plannedTarget) {
        Optional<GameMap.DriverNetworkBuildStep> step = bestDriverNetworkBuildStep(player, plannedTarget);
        if (step.isEmpty()) return false;

        GameMap.DriverNetworkBuildStep buildStep = step.get();
        if (!player.engineerRanger.position().equals(buildStep.workerPosition())) {
            return false;
        }

        if (!simulation.map.buildDriverNetworkStep(buildStep)) {
            return false;
        }

        simulation.log("Инженер игрока " + player.id + " построил "
                + (buildStep.bridge() ? "мост" : "дорогу")
                + " к дорожной сети");
        return true;
    }

    /**
     * Выбирает следующий шаг строительства от связанной с базой дорожной сети.
     *
     * @param player игрок, для которого ищется инфраструктурный шаг
     * @param plannedTarget цель из AI-плана
     * @return шаг строительства дороги или моста
     */
    private Optional<GameMap.DriverNetworkBuildStep> bestDriverNetworkBuildStep(PlayerState player, Point plannedTarget) {
        Optional<Point> target = bestInfrastructureTarget(player, plannedTarget);
        if (target.isEmpty()) return Optional.empty();
        return simulation.map.bestDriverNetworkBuildStepToward(target.get());
    }

    /**
     * Перемещает инженера к точке, из которой можно расширить связанную с базой
     * дорожную сеть.
     *
     * Инженеру больше не нужно ломиться к самому динозавру в ловушке. Ему нужно
     * встать на frontier текущей дороги и построить следующий сегмент. Дорога,
     * внезапно, должна начинаться не с пленного Криптогната, а с базы.
     *
     * @param player игрок, чей инженер двигается
     * @param plannedTarget цель из AI-плана
     * @param movementPoints доступные очки движения
     * @return true, если инженер сменил позицию
     */
    private boolean moveEngineerTowardInfrastructureTarget(PlayerState player, Point plannedTarget, int movementPoints) {
        Optional<GameMap.DriverNetworkBuildStep> step = bestDriverNetworkBuildStep(player, plannedTarget);
        if (step.isPresent()) {
            return moveEngineerToward(player, step.get().workerPosition(), movementPoints);
        }

        Optional<Point> target = bestInfrastructureTarget(player, plannedTarget);
        return target.isPresent() && moveEngineerToward(player, target.get(), movementPoints);
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

        Optional<Point> hunterTarget = activeTrackingTargetWithoutDriverAccess(player);
        if (hunterTarget.isPresent()) return hunterTarget;

        return Optional.empty();
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

    /**
     * Выбирает пустую активную ловушку для возврата в инвентарь.
     *
     * @param player игрок-владелец ловушек
     * @param preferredPosition позиция из AI-плана, если план уже выбрал конкретную ловушку
     * @return ловушка, которую стоит снять
     */
    private Optional<Trap> staleTrapToRecover(PlayerState player, Point preferredPosition) {
        if (activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer) {
            return Optional.empty();
        }

        Set<Point> usefulTrapPositions = usefulTrapPositions(player);
        if (usefulTrapPositions.isEmpty()) {
            return Optional.empty();
        }

        boolean hasUsefulTrap = player.traps.stream()
                .filter(trap -> trap.active && !trap.hasDinosaur())
                .anyMatch(trap -> usefulTrapPositions.contains(trap.position));
        if (hasUsefulTrap) {
            return Optional.empty();
        }

        Point from = preferredPosition != null ? preferredPosition : player.engineerRanger.position();
        return player.traps.stream()
                .filter(trap -> trap.active && !trap.hasDinosaur())
                .min(Comparator.comparingInt(trap -> trap.position.chebyshev(from)));
    }

    /**
     * Собирает клетки, на которых ловушки сейчас имеют смысл для нужных S-целей.
     *
     * @param player игрок, для которого строится прогноз
     * @return множество полезных позиций ловушек
     */
    private Set<Point> usefulTrapPositions(PlayerState player) {
        Set<Point> result = new java.util.HashSet<>();

        simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .forEach(dinosaur -> result.addAll(simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur)));

        return result;
    }

    private int activeTrapCount(PlayerState player) {
        return (int) player.traps.stream()
                .filter(trap -> trap.active)
                .count();
    }

    private boolean hasActiveTrapAt(Point point) {
        return simulation.players.stream()
                .flatMap(owner -> owner.traps.stream())
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
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .flatMap(dinosaur -> simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).stream())
                .filter(point -> isUsableTrapPoint(player, point))
                .min(Comparator.comparingInt(point -> from == null ? 0 : point.chebyshev(from)));
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
                && !hasActiveTrapAt(point)
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
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .sorted(Comparator.comparingInt(d -> d.position.chebyshev(player.engineerRanger.position())))
                .forEach(dinosaur -> simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).stream()
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
                .min(Comparator.comparingInt(dinosaur -> player.engineerRanger.position().chebyshev(dinosaur.position)))
                .map(dinosaur -> dinosaur.position);
    }

    private Optional<Point> activeTrackingTargetWithoutDriverAccess(PlayerState player) {
        if (player.activeTracking == null) {
            return Optional.empty();
        }

        return simulation.dinosaurs.stream()
                .filter(dinosaur -> dinosaur.id == player.activeTracking.dinosaurId)
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.trapped && !dinosaur.removed)
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> dinosaur.captureMethod == CaptureMethod.TRACKING)
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .map(dinosaur -> dinosaur.position)
                .findFirst();
    }


    private Optional<Point> nearestUnconnectedNeededBiomePoint(PlayerState player) {
        Set<Biome> neededBiomes = remainingNeededBiomes(player);

        return simulation.map.entries().stream()
                .filter(entry -> neededBiomes.contains(entry.getValue().biome))
                .filter(entry -> !simulation.map.hasDriverPath(simulation.map.base, entry.getKey()))
                .min(Comparator.comparingInt(entry -> player.engineerRanger.position().chebyshev(entry.getKey())))
                .map(java.util.Map.Entry::getKey);
    }

    private Set<Biome> remainingNeededBiomes(PlayerState player) {
        EnumSet<Biome> result = EnumSet.noneOf(Biome.class);

        for (Species species : player.task) {
            if (player.captured.contains(species)) continue;
            if (Dinosaur.captureMethodOf(species) == CaptureMethod.HUNT) continue;
            result.add(Dinosaur.spawnBiomeOf(species));
            result.addAll(Dinosaur.bioTrailOf(species));
        }

        return result;
    }

    private boolean isInTrapPlacementRange(Point engineerPosition, Point target) {
        return engineerPosition != null && engineerPosition.isSameOrAdjacent8(target);
    }

}
