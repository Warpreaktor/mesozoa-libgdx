package ru.mesozoa.sim.action;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.map.PathFinder;
import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.ranger.Ranger;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.ranger.ai.RangerTurnPlanner;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Исполнение действий рейнджеров.
 *
 * Основная точка входа теперь принимает RangerPlan. План содержит выбранную
 * фигурку, её AI-оценку, причину и цель, а очки действий берутся из самой фигурки.
 */
public final class RangerActionExecutor {

    private final GameSimulation simulation;
    private final ScoutAction scoutAction;
    private final HunterAction hunterAction;
    private final EngineerAction engineerAction;
    private final DriverAction driverAction;
    private final DinosaurAction dinosaurAction;

    public RangerActionExecutor(GameSimulation simulation) {

        this.simulation = simulation;
        this.dinosaurAction = new DinosaurAction(simulation);
        this.scoutAction = new ScoutAction(simulation, dinosaurAction);
        this.hunterAction = new HunterAction(simulation, this);
        this.engineerAction = new EngineerAction(simulation, this);
        this.driverAction = new DriverAction(simulation, this);
    }

    /**
     * Исполняет выбранный AI-план.
     *
     * @param player владелец фигурки
     * @param plan выбранный план действия
     */
    public void executePlan(PlayerState player, RangerPlan plan) {
        Ranger ranger = plan.ranger();
        ranger.startActivation();
        ranger.play(plan, this, player);
        ranger.finishActivation();
    }

    /**
     * Исполняет план внутри конкретной фигурки.
     *
     * Пока старые action-классы ещё живы, здесь остаётся переходный switch. Он
     * должен исчезнуть позже, когда логика исполнения переедет в конкретные фигурки
     * или в отдельные command-обработчики.
     *
     * @param player владелец фигурки
     * @param plan выбранный план
     */
    public void executePlanForRanger(PlayerState player, RangerPlan plan) {
        switch (plan.role()) {
            case SCOUT -> scoutAction.action(player, plan);
            case ENGINEER -> engineerAction.action(player, plan);
            case HUNTER -> hunterAction.action(player, plan);
            case DRIVER -> driverAction.action(player, plan);
        }
    }

    public void moveRoleToward(PlayerState player, RangerRole role, Point target, int movementPoints) {
        Point position = player.positionOf(role);

        for (int i = 0; i < movementPoints; i++) {
            if (position.equals(target)) break;

            Point next = role == RangerRole.SCOUT
                    ? PathFinder.stepTowardPlaced(
                            simulation.map,
                            position,
                            target,
                            point -> PathFinder.isScoutPassable(simulation.map, point)
                    )
                    : simulation.map.stepGroundRangerToward(position, target);
            if (next.equals(position)) break;

            position = next;
        }

        player.setPosition(role, position);
    }

    public Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        Set<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        allowedMethods.addAll(Arrays.asList(methods));

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.chebyshev(from)));
    }
}
