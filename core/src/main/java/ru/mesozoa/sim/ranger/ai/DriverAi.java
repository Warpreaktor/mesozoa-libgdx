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

    /** Вес ситуации, когда динозавр в ловушке есть, но дороги для вывоза нет. */
    private static final double SCORE_TRAPPED_DINO_WITHOUT_ROUTE = -25.0;

    /** Вес рейса водителя к динозавру или обратно на базу с грузом. */
    private static final double SCORE_READY_EXTRACTION = 120.0;

    private final GameSimulation simulation;

    public DriverAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Рассчитывает вес активации водителя для текущего игрока.
     *
     * Водитель не получает очки за сближение с охотником, инженером или разведчиком:
     * по текущим правилам он ездит только по связанной дорожной сети. Его работа
     * начинается только тогда, когда в ловушке уже сидит нужный динозавр.
     *
     * @param player игрок, для которого оценивается полезность водителя
     * @return оценка полезности водителя и причина этой оценки
     */
    public AiScore scoreDriver(PlayerState player) {
        Optional<Dinosaur> reachableTarget = nearestReachableTrappedDinosaur(player);
        if (reachableTarget.isPresent()) {
            Dinosaur dinosaur = reachableTarget.get();
            String driverTask = player.driverRanger.position().equals(dinosaur.position)
                    ? "водитель уже у ловушки, можно везти на базу: "
                    : "водитель должен ехать к динозавру в ловушке: ";
            return new AiScore(
                    SCORE_READY_EXTRACTION,
                    driverTask
                            + dinosaur.displayName
                            + " на " + dinosaur.position
            );
        }

        Optional<Dinosaur> blockedTarget = nearestBlockedTrappedDinosaur(player);
        if (blockedTarget.isPresent()) {
            Dinosaur dinosaur = blockedTarget.get();
            return new AiScore(
                    SCORE_TRAPPED_DINO_WITHOUT_ROUTE,
                    "динозавр в ловушке ждёт вывоза, но дороги нет: "
                            + dinosaur.displayName
                            + " на " + dinosaur.position
            );
        }

        return new AiScore(
                SCORE_NO_USEFUL_TASK,
                "нет динозавров в ловушках, водитель не тратит ход на бессмысленные покатушки"
        );
    }

    /**
     * Выбирает клетку, к которой водитель должен ехать или с которой должен возвращаться.
     *
     * @param player игрок, для которого выбирается цель
     * @return клетка динозавра в ловушке или null, если цель недоступна
     */
    public Point chooseDriverTarget(PlayerState player) {
        return nearestReachableTrappedDinosaur(player)
                .map(dinosaur -> dinosaur.position)
                .orElse(null);
    }

    /**
     * Ищет ближайшего динозавра в ловушке, к которому готов водительский маршрут.
     *
     * @param player игрок, чей водитель оценивается
     * @return ближайшая доступная цель водительского рейса
     */
    private Optional<Dinosaur> nearestReachableTrappedDinosaur(PlayerState player) {
        return simulation.nearestTrappedNeededDinosaurAwaitingPickup(
                player,
                player.driverRanger.position(),
                true
        );
    }

    /**
     * Ищет ближайшего динозавра в ловушке, до которого ещё нет дорожного доступа.
     *
     * @param player игрок, чей водитель оценивается
     * @return ближайшая заблокированная цель вывоза
     */
    private Optional<Dinosaur> nearestBlockedTrappedDinosaur(PlayerState player) {
        return simulation.nearestTrappedNeededDinosaurAwaitingPickup(
                player,
                player.driverRanger.position(),
                false
        ).filter(dinosaur -> !simulation.canDriverExtractTrappedDinosaur(player, dinosaur));
    }
}
