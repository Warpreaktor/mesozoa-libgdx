package ru.mesozoa.sim.ai;

import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ru.mesozoa.sim.model.RangerRole.DRIVER;
import static ru.mesozoa.sim.model.RangerRole.ENGINEER;
import static ru.mesozoa.sim.model.RangerRole.HUNTER;
import static ru.mesozoa.sim.model.RangerRole.SCOUT;

/**
 * Выбор рейнджера для очередной активации игрока.
 *
 * Planner больше не выбирает сразу две роли в начале хода игрока.
 * Каждая активация выбирается отдельно, прямо перед выполнением действия.
 * Поэтому если разведчик открыл тайл со спауном, следующая роль выбирается
 * уже с учётом нового динозавра на карте.
 */
public final class RangerTurnPlanner {

    private final GameSimulation simulation;
    private final ScoutAi scoutAi;
    private final HunterAi hunterAi;
    private final DriverAi driverAi;
    private final EngineerAi engineerAi;

    public RangerTurnPlanner(GameSimulation simulation) {
        this.simulation = simulation;
        this.scoutAi = new ScoutAi(simulation);
        this.hunterAi = new HunterAi(simulation);
        this.driverAi = new DriverAi(simulation);
        this.engineerAi = new EngineerAi(simulation);
    }

    /**
     * Выбирает следующую роль для текущего игрока.
     *
     * @param player игрок, который сейчас ходит
     * @param alreadyUsedRoles роли, уже активированные этим игроком в текущем ходе
     * @return лучшая роль для следующей активации или null, если доступных ролей нет
     */
    public RangerRole chooseNextRangerForTurn(PlayerState player, Set<RangerRole> alreadyUsedRoles) {
        RangerRole bestRole = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (RangerRole role : List.of(SCOUT, ENGINEER, HUNTER, DRIVER)) {

            if (alreadyUsedRoles.contains(role)) {
                continue;
            }

            double score = scoreRole(player, role);

            if (score > bestScore) {
                bestScore = score;
                bestRole = role;
            }
        }

        return bestRole;
    }

    private double scoreRole(PlayerState player, RangerRole role) {
        return switch (role) {
            case SCOUT -> scoutAi.scoreScout(player);
            case ENGINEER -> engineerAi.scoreEngineer(player);
            case HUNTER -> hunterAi.scoreHunter(player);
            case DRIVER -> driverAi.scoreDriver(player);
        };
    }
}
