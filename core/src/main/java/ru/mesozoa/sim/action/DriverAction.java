package ru.mesozoa.sim.action;

import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
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
        Point target = chooseDriverTarget(player);
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

        player.driver = position;
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
