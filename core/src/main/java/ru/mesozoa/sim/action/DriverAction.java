package ru.mesozoa.sim.action;

import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.rules.GameSimulation;

public class DriverAction {

    private final GameSimulation simulation;
    private final RangerActionExecutor rangerActionExecutor;

    public DriverAction(GameSimulation simulation,
                        RangerActionExecutor rangerActionExecutor) {

        this.simulation = simulation;
        this.rangerActionExecutor = rangerActionExecutor;
    }

    public void action(PlayerState player, int movementPoints) {
        Point target;

        if (!player.driver.equals(player.hunter)) {
            target = player.hunter;
        } else if (!player.driver.equals(player.engineer)) {
            target = player.engineer;
        } else {
            target = player.scout;
        }

        rangerActionExecutor.moveRoleToward(player, RangerRole.DRIVER, target, movementPoints);
    }
}
