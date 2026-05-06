package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Optional;

/**
 * AI-оценка полезности водителя для очередной активации игрока.
 */
public class DriverAi {

    /** Вес ситуации, когда водитель сейчас не имеет полезной задачи. */
    private static final double SCORE_NO_USEFUL_TASK = -10.0;

    /** Вес ситуации, когда нужный динозавр ждёт вывоза, но дороги для рейса нет. */
    private static final double SCORE_NEEDED_DINO_WITHOUT_ROUTE = -25.0;

    /** Вес рейса за динозавром, который закрывает задание штаба. */
    private static final double SCORE_READY_TASK_EXTRACTION = 150.0;

    /** Вес бонусного рейса за чужой/лишней добычей ради очков. */
    private static final double SCORE_READY_BONUS_EXTRACTION = 28.0;

    private final GameSimulation simulation;

    public DriverAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Рассчитывает вес активации водителя для текущего игрока.
     *
     * Водитель больше не смотрит на владельца ловушки. Если добыча лежит на
     * карте и есть дорога, её можно увезти. Цель из задания получает высокий
     * приоритет, бонусная добыча — низкий, чтобы AI не бросал важную охоту ради
     * случайного Криптогната, но мог украсть очки, когда других дел нет.
     *
     * @param player игрок, для которого оценивается полезность водителя
     * @return оценка полезности водителя и причина этой оценки
     */
    public AiScore scoreDriver(PlayerState player) {
        Optional<Dinosaur> reachableTaskTarget = nearestReachableTaskDinosaur(player);
        if (reachableTaskTarget.isPresent()) {
            Dinosaur dinosaur = reachableTaskTarget.get();
            return new AiScore(
                    SCORE_READY_TASK_EXTRACTION,
                    "водитель может вывезти нужную добычу: "
                            + dinosaur.displayName
                            + " на " + dinosaur.position
            );
        }

        Optional<Dinosaur> reachableBonusTarget = nearestReachableBonusDinosaur(player);
        if (reachableBonusTarget.isPresent()) {
            Dinosaur dinosaur = reachableBonusTarget.get();
            return new AiScore(
                    SCORE_READY_BONUS_EXTRACTION + simulation.capturePointsFor(dinosaur),
                    "водитель может украсть/вывезти бонусную добычу за очки: "
                            + dinosaur.displayName
                            + " на " + dinosaur.position
            );
        }

        Optional<Dinosaur> blockedTarget = nearestBlockedTaskDinosaur(player);
        if (blockedTarget.isPresent()) {
            Dinosaur dinosaur = blockedTarget.get();
            return new AiScore(
                    SCORE_NEEDED_DINO_WITHOUT_ROUTE,
                    "нужный динозавр ждёт вывоза, но дороги нет: "
                            + dinosaur.displayName
                            + " на " + dinosaur.position
            );
        }

        return new AiScore(
                SCORE_NO_USEFUL_TASK,
                "нет доступной добычи для водительского рейса"
        );
    }

    /**
     * Выбирает клетку, к которой водитель должен ехать за добычей.
     *
     * @param player игрок, для которого выбирается цель
     * @return клетка динозавра или null, если цель недоступна
     */
    public Point chooseDriverTarget(PlayerState player) {
        Optional<Dinosaur> task = nearestReachableTaskDinosaur(player);
        if (task.isPresent()) {
            return task.get().position;
        }

        return nearestReachableBonusDinosaur(player)
                .map(dinosaur -> dinosaur.position)
                .orElse(null);
    }

    /** Ищет ближайшую доступную добычу, закрывающую задание. */
    private Optional<Dinosaur> nearestReachableTaskDinosaur(PlayerState player) {
        return simulation.nearestAwaitingPickupDinosaur(
                player,
                player.driverRanger.position(),
                true,
                true
        );
    }

    /** Ищет ближайшую доступную бонусную добычу. */
    private Optional<Dinosaur> nearestReachableBonusDinosaur(PlayerState player) {
        return simulation.nearestAwaitingPickupDinosaur(
                player,
                player.driverRanger.position(),
                true,
                false
        ).filter(dinosaur -> !player.needs(dinosaur.species));
    }

    /** Ищет ближайшую нужную добычу без готового водительского маршрута. */
    private Optional<Dinosaur> nearestBlockedTaskDinosaur(PlayerState player) {
        return simulation.nearestAwaitingPickupDinosaur(
                player,
                player.driverRanger.position(),
                false,
                true
        ).filter(dinosaur -> !simulation.canDriverExtractTrappedDinosaur(player, dinosaur));
    }
}
