package ru.mesozoa.sim.ai;

import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.List;
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

    /** Минимальная оценка, при которой роль считается полезной для активации. */
    private static final double MIN_USEFUL_SCORE = 0.0;

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
        AiScore bestScore = new AiScore(Double.NEGATIVE_INFINITY, "нет оценки");

        for (RangerRole role : List.of(SCOUT, ENGINEER, HUNTER, DRIVER)) {

            if (alreadyUsedRoles.contains(role)) {
                continue;
            }

            AiScore score = scoreRole(player, role);

            if (score.value() > bestScore.value()) {
                bestScore = score;
                bestRole = role;
            }
        }

        if (bestRole == null) {
            simulation.log("AI игрок " + player.id + ": нет доступного рейнджера для активации");
            return null;
        }

        if (bestScore.value() <= MIN_USEFUL_SCORE) {
            simulation.log("AI игрок " + player.id
                    + ": нет полезной роли для активации; лучший кандидат "
                    + roleToText(bestRole)
                    + " получил оценку " + bestScore.value()
                    + " — " + bestScore.reason());
            return null;
        }

        simulation.log("AI игрок " + player.id
                + ": выбран " + roleToText(bestRole)
                + " с оценкой " + bestScore.value()
                + " — " + bestScore.reason());

        return bestRole;
    }

    private String roleToText(RangerRole role) {
        return switch (role) {
            case SCOUT -> "разведчик";
            case DRIVER -> "водитель";
            case ENGINEER -> "инженер";
            case HUNTER -> "охотник";
        };
    }

    private AiScore scoreRole(PlayerState player, RangerRole role) {
        return switch (role) {
            case SCOUT -> scoutAi.scoreScout(player);
            case ENGINEER -> engineerAi.scoreEngineer(player);
            case HUNTER -> hunterAi.scoreHunter(player);
            case DRIVER -> driverAi.scoreDriver(player);
        };
    }
}
