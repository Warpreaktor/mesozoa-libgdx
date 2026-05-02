package ru.mesozoa.sim.action;

import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.rules.GameSimulation;
import ru.mesozoa.sim.rules.RangerTurnPlanner;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Исполнение действий рейнджеров.
 */
public final class RangerActionExecutor {

    private final GameSimulation simulation;
    private final RangerTurnPlanner planner;
    private final ScoutAction scoutAction;
    private final HunterAction hunterAction;
    private final EngineerAction engineerAction;
    private final DriverAction driverAction;
    private final DinosaurAction dinosaurAction;

    public RangerActionExecutor(GameSimulation simulation,
                                RangerTurnPlanner planner) {

        this.simulation = simulation;
        this.planner = planner;
        this.dinosaurAction = new DinosaurAction(simulation);
        this.scoutAction = new ScoutAction(simulation, this, dinosaurAction);
        this.hunterAction = new HunterAction(simulation, this);
        this.engineerAction = new EngineerAction(simulation, this);
        this.driverAction = new DriverAction(simulation, this);
    }

    /**
     * Начинает ход игрока и возвращает две роли, которые будут активированы по одной.
     */
    public List<RangerRole> startTurn(PlayerState player) {
        if (player.isComplete()) {
            simulation.log("Игрок " + player.id + " уже выполнил задание.");
            return List.of();
        }

        if (player.turnsSkipped > 0) {
            player.turnsSkipped--;
            simulation.log("Игрок " + player.id + " пропускает ход.");
            return List.of();
        }

        List<RangerRole> roles = planner.chooseTwoRangersForTurn(player);

        simulation.log("Ход игрока " + player.id + " (" + player.color.assetSuffix + "): "
                + planner.roleListToText(roles));

        return roles;
    }

    /**
     * Исполняет одну активацию одной роли.
     */
    public void playRole(PlayerState player, RangerRole role, int movementPoints) {
        switch (role) {
            case SCOUT -> scoutAction.action(player, movementPoints);
            case ENGINEER -> engineerAction.action(player, movementPoints);
            case HUNTER -> hunterAction.action(player, movementPoints);
            case DRIVER -> driverAction.action(player, movementPoints);
        }
    }

    public void moveRoleToward(PlayerState player, RangerRole role, Point target, int movementPoints) {
        Point position = player.positionOf(role);

        for (int i = 0; i < movementPoints; i++) {
            if (position.equals(target)) break;

            Point next = simulation.stepTowardPlaced(position, target);
            if (next.equals(position)) break;

            position = next;
        }

        player.setPosition(role, position);
    }

    public Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        Set<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        allowedMethods.addAll(Arrays.asList(methods));

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.manhattan(from)));
    }
}
