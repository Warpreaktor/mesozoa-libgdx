package ru.mesozoa.sim.action;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.model.Trap;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Класс содержащий логику ходов и AI для инженера
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
     * Инженер ловит S-динозавров через ловушки.
     *
     * За одну активацию инженер может:
     * - переместиться на свои очки движения;
     * - выставить до лимита активных ловушек.
     *
     * Выставление одной, двух или трёх ловушек считается одним действием.
     */
    public void action(PlayerState player, int movementPoints) {
        boolean hasTrapTarget = hasNeededTrapTarget(player);

        moveEngineerTowardTrapTarget(player, movementPoints);

        if (!hasTrapTarget) {
            return;
        }

        int placed = placeAvailableTrapsAroundEngineer(player);
        if (placed > 0) {
            simulation.log("Игрок " + player.id + " выставил ловушки: " + placed);
        }
    }

    private boolean hasNeededTrapTarget(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .anyMatch(d -> d.species.captureMethod == CaptureMethod.TRAP);
    }

    /**
     * Подводит инженера к ближайшей ловушечной цели, если она пока вне зоны установки.
     */
    private void moveEngineerTowardTrapTarget(PlayerState player, int movementPoints) {
        Optional<Dinosaur> target = rangerActionExecutor.nearestNeededDinosaur(player, player.engineer, CaptureMethod.TRAP);

        if (target.isPresent()) {
            Point targetPosition = target.get().position;
            if (!isInTrapPlacementRange(player.engineer, targetPosition)) {
                rangerActionExecutor.moveRoleToward(player, RangerRole.ENGINEER, targetPosition, movementPoints);
            }
            return;
        }

        rangerActionExecutor.moveRoleToward(player, RangerRole.ENGINEER, player.scout, movementPoints);
    }

    /**
     * Выставляет ловушки в клетку инженера и соседние клетки, включая диагонали.
     *
     * Инженер не обязан ставить все три ловушки рядом с динозавром. Сначала он
     * пытается накрыть текущую и прогнозируемую позицию нужных S-динозавров,
     * затем добивает свободными открытыми клетками вокруг себя.
     */
    private int placeAvailableTrapsAroundEngineer(PlayerState player) {
        int available = Math.max(0, simulation.inventoryConfig.maxTrapsPerPlayer - activeTrapCount(player));
        if (available == 0) {
            return 0;
        }

        int placed = 0;
        for (Point candidate : trapPlacementCandidates(player)) {
            if (placed >= available) break;
            if (!simulation.map.isPlaced(candidate)) continue;
            if (hasActiveTrapAt(player, candidate)) continue;

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
     * Кандидаты для установки ловушек.
     *
     * Порядок важен: сначала клетки, связанные с нужными TRAP-целями,
     * затем клетка инженера и все восемь соседей.
     */
    private List<Point> trapPlacementCandidates(PlayerState player) {
        LinkedHashSet<Point> result = new LinkedHashSet<>();

        simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.TRAP)
                .sorted(Comparator.comparingInt(d -> d.position.manhattan(player.engineer)))
                .forEach(dinosaur -> {
                    Point predicted = predictNextBioStep(dinosaur);
                    if (predicted != null && isInTrapPlacementRange(player.engineer, predicted)) {
                        result.add(predicted);
                    }

                    if (isInTrapPlacementRange(player.engineer, dinosaur.position)) {
                        result.add(dinosaur.position);
                    }
                });

        result.add(player.engineer);

        for (Direction direction : Direction.values()) {
            result.add(direction.from(player.engineer));
        }

        return new ArrayList<>(result);
    }

    private boolean isInTrapPlacementRange(Point engineerPosition, Point target) {
        int dx = Math.abs(engineerPosition.x - target.x);
        int dy = Math.abs(engineerPosition.y - target.y);
        return dx <= 1 && dy <= 1;
    }

    private Point predictNextBioStep(Dinosaur dinosaur) {
        Point target = predictNextBioTarget(dinosaur);
        if (target == null) return null;
        return simulation.stepTowardPlaced(dinosaur.position, target);
    }

    private Point predictNextBioTarget(Dinosaur dinosaur) {
        Biome nextBiome = dinosaur.species.bioTrail.get((dinosaur.trailIndex + 1) % dinosaur.species.bioTrail.size());
        return simulation.map.nearestPlacedBiome(dinosaur.position, nextBiome);
    }
}
