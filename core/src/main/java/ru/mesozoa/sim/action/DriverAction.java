package ru.mesozoa.sim.action;

import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.simulation.GameSimulation;

public class DriverAction {

    private final GameSimulation simulation;
    private final RangerActionExecutor rangerActionExecutor;

    public DriverAction(GameSimulation simulation,
                        RangerActionExecutor rangerActionExecutor) {

        this.simulation = simulation;
        this.rangerActionExecutor = rangerActionExecutor;
    }

    public void action(PlayerState player, RangerPlan plan) {
        Point target = plan.target() == null ? chooseDriverTarget(player) : plan.target();
        int movementPoints = plan.ranger().currentActionPoints();
        action(player, target, movementPoints);
        plan.ranger().spendActionPoints(movementPoints);
    }

    public void action(PlayerState player, int movementPoints) {
        Point target = chooseDriverTarget(player);
        action(player, target, movementPoints);
    }

    private void action(PlayerState player, Point target, int movementPoints) {
        if (target == null) {
            simulation.log("Водитель игрока " + player.id + " не нашёл цели движения");
            return;
        }
        Point position = player.driver;

        for (int i = 0; i < movementPoints; i++) {
            if (position.equals(target)) break;

            Point next = simulation.map.stepDriverToward(position, target);
            if (next.equals(position)) break;

            position = next;
        }

        if (position.equals(player.driver) && !position.equals(target)) {
            simulation.log("Водитель игрока " + player.id + " не нашёл дороги или моста к цели");
        }

        player.setPosition(RangerRole.DRIVER, position);
    }

    public Point chooseDriverTarget(PlayerState player) {
        if (!player.driver.equals(player.hunter)) {
            return player.hunter;
        }
        if (!player.driver.equals(player.engineer)) {
            return player.engineer;
        }
        return player.scout;
    }
}
