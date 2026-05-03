package ru.mesozoa.sim.ai;

import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.simulation.GameSimulation;

public class DriverAi {

    private final GameSimulation gameSimulation;

    public DriverAi(GameSimulation gameSimulation) {
        this.gameSimulation = gameSimulation;
    }

    /**
     * Водитель пока имеет низкий вес и выбирается только как логистическая поддержка.
     *
     * Важно: фактическое ограничение движения по дорогам/мостам должно проверяться
     * в действии водителя. Planner только решает, стоит ли вообще пытаться его активировать.
     */
    public AiScore scoreDriver(PlayerState player) {
        if (player.driver.equals(player.hunter)
                && player.driver.equals(player.engineer)
                && player.driver.equals(player.scout)) {
            return new AiScore(
                    2.0,
                    "водитель стоит вместе со всей командой, срочной логистической задачи нет"
            );
        }

        return new AiScore(
                18.0,
                "водитель отделён от части команды и может попытаться подтянуться по дорогам или мостам"
        );
    }
}
