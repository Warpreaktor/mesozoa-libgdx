package ru.mesozoa.sim.action;

import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class HunterAction {

    private final GameSimulation simulation;
    private final RangerActionExecutor rangerActionExecutor;

    public HunterAction(GameSimulation simulation,
                        RangerActionExecutor rangerActionExecutor) {

        this.simulation = simulation;
        this.rangerActionExecutor = rangerActionExecutor;
    }


    public void action(PlayerState player, RangerPlan plan) {
        int movementPoints = plan.ranger().currentActionPoints();
        action(player, plan.target(), movementPoints);
        plan.ranger().spendActionPoints(movementPoints);
    }

    public void action(PlayerState player, int movementPoints) {
        action(player, null, movementPoints);
    }

    private void action(PlayerState player, Point plannedTarget, int movementPoints) {
        boolean acted = attemptCapture(player);
        if (acted) {
            return;
        }

        if (plannedTarget != null) {
            rangerActionExecutor.moveRoleToward(player, RangerRole.HUNTER, plannedTarget, movementPoints);
            return;
        }

        Optional<Dinosaur> target = rangerActionExecutor.nearestNeededDinosaur(
                player,
                player.hunterRanger.position(),
                CaptureMethod.TRACKING,
                CaptureMethod.HUNT
        );

        if (target.isPresent()) {
            rangerActionExecutor.moveRoleToward(player, RangerRole.HUNTER, target.get().position, movementPoints);
            return;
        }

        rangerActionExecutor.moveRoleToward(player, RangerRole.HUNTER, player.scoutRanger.position(), movementPoints);
    }

    private boolean attemptCapture(PlayerState player) {
        List<Dinosaur> needed = simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod != CaptureMethod.TRAP)
                .sorted(Comparator.comparingInt(d -> d.position.manhattan(player.hunterRanger.position())))
                .toList();

        for (Dinosaur dinosaur : needed) {
            if (dinosaur.species.captureMethod == CaptureMethod.TRACKING) {
                if (player.hunterRanger.position().manhattan(dinosaur.position) <= 1) {
                    double chance = simulation.gameMechanicConfig.trackingBaseSuccess
                            + simulation.gameMechanicConfig.trackingStepBonus
                            * simulation.random.nextInt(simulation.gameMechanicConfig.trackingMaxSteps);

                    if (simulation.random.nextDouble() < chance) {
                        capture(player, dinosaur, "выслеживание");
                        simulation.result.trackingCaptures++;
                    }
                    return true;
                }

                player.setPosition(RangerRole.HUNTER, simulation.map.stepGroundRangerToward(player.hunterRanger.position(), dinosaur.position));
                return true;
            }

            if (dinosaur.species.captureMethod == CaptureMethod.HUNT) {
                if (player.hunterBait <= 0) return false;

                if (player.hunterRanger.position().manhattan(dinosaur.position) <= 2) {
                    if (!simulation.map.canPlaceBait(player.hunterRanger.position())) {
                        return false;
                    }

                    double chance = simulation.random.nextBoolean()
                            ? simulation.gameMechanicConfig.huntBaseSuccess
                            : simulation.gameMechanicConfig.huntPreparedSuccess;

                    if (simulation.random.nextDouble() < chance) {
                        capture(player, dinosaur, "охота");
                        simulation.result.huntCaptures++;
                    }

                    player.hunterBait--;
                    return true;
                }

                player.setPosition(RangerRole.HUNTER, simulation.map.stepGroundRangerToward(player.hunterRanger.position(), dinosaur.position));
                return true;
            }
        }

        return false;
    }

    private void capture(PlayerState player, Dinosaur dinosaur, String method) {
        dinosaur.captured = true;
        player.captured.add(dinosaur.species);
        simulation.log("ПОЙМАН: игрок " + player.id + " поймал "
                + dinosaur.species.displayName + " (" + method + ")");
    }
}
