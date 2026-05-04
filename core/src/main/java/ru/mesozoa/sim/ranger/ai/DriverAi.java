package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameSimulation;

/**
 * AI-оценка полезности водителя для очередной активации игрока.
 */
public class DriverAi {

    /** Вес действия, которое водитель сейчас выполнить не может. */
    private static final double SCORE_IMPOSSIBLE = -100.0;

    /** Вес ситуации, когда водитель не решает полезной задачи. */
    private static final double SCORE_NO_USEFUL_TASK = -10.0;

    /** Вес ситуации, когда цель понятна, но к ней нет дороги или мостового пути. */
    private static final double SCORE_NO_ROUTE = -30.0;

    /** Базовый вес полезного логистического действия водителя. */
    private static final double SCORE_USEFUL_ROUTE_BASE = 18.0;

    /** Максимальный бонус за длинную связную дорогу, по которой водитель может быстро подтянуться. */
    private static final double SCORE_ROUTE_DISTANCE_BONUS_MAX = 10.0;

    private final GameSimulation simulation;

    public DriverAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Рассчитывает вес активации водителя для текущего игрока.
     *
     * @param player игрок, для которого оценивается полезность водителя
     * @return оценка полезности водителя и причина этой оценки
     */
    public AiScore scoreDriver(PlayerState player) {
        Point target = chooseDriverTarget(player);

        if (hasNoUsefulDriverTarget(player, target)) {
            return new AiScore(
                    SCORE_NO_USEFUL_TASK,
                    "водитель стоит вместе со всей командой, логистической задачи нет"
            );
        }

        if (isTargetMissing(target)) {
            return new AiScore(
                    SCORE_IMPOSSIBLE,
                    "у водителя нет корректной цели движения"
            );
        }

        if (isDriverAlreadyAtTarget(player, target)) {
            return new AiScore(
                    SCORE_NO_USEFUL_TASK,
                    "водитель уже находится в выбранной целевой клетке"
            );
        }

        if (hasNoDriverRouteToTarget(player, target)) {
            return new AiScore(
                    SCORE_NO_ROUTE,
                    "водитель не может проехать к цели: нет связанного пути по дорогам или мостам"
            );
        }

        return scoreReachableDriverTarget(player, target);
    }

    /**
     * Выбирает ближайшую логистическую задачу водителя в рамках текущей упрощённой модели.
     *
     * @param player игрок, для которого выбирается цель водителя
     * @return позиция рейнджера, к которому водитель должен подтянуться
     */
    private Point chooseDriverTarget(PlayerState player) {
        if (!player.driverRanger.position().equals(player.hunterRanger.position())) {
            return player.hunterRanger.position();
        }

        if (!player.driverRanger.position().equals(player.engineerRanger.position())) {
            return player.engineerRanger.position();
        }

        if (!player.driverRanger.position().equals(player.scoutRanger.position())) {
            return player.scoutRanger.position();
        }

        return null;
    }

    /**
     * Проверяет, есть ли у водителя вообще полезная задача.
     *
     * @param player игрок, чей водитель оценивается
     * @param target выбранная цель водителя
     * @return true, если водитель не имеет полезной логистической задачи
     */
    private boolean hasNoUsefulDriverTarget(PlayerState player, Point target) {
        return target == null
                && player.driverRanger.position().equals(player.hunterRanger.position())
                && player.driverRanger.position().equals(player.engineerRanger.position())
                && player.driverRanger.position().equals(player.scoutRanger.position());
    }

    /**
     * Проверяет, что цель движения не была определена.
     *
     * @param target выбранная цель водителя
     * @return true, если цель отсутствует
     */
    private boolean isTargetMissing(Point target) {
        return target == null;
    }

    /**
     * Проверяет, находится ли водитель уже в целевой клетке.
     * В такой ситуации активация водителя не изменит положение на карте, поэтому
     * она должна иметь отрицательный вес и не должна перебивать охотника, инженера
     * или разведчика.
     *
     * @param player игрок, чей водитель оценивается
     * @param target выбранная цель водителя
     * @return true, если водитель уже стоит в целевой клетке
     */
    private boolean isDriverAlreadyAtTarget(PlayerState player, Point target) {
        return player.driverRanger.position().equals(target);
    }

    /**
     * Проверяет, существует ли путь до цели по дорогам или мостам.
     * Если из базы не проложена дорога или не существует допустимый мостовой/дорожный маршрут,
     * водитель не должен получать положительный вес.
     *
     * @param player игрок, чей водитель оценивается
     * @param target выбранная цель водителя
     * @return true, если водитель не может проехать к цели
     */
    private boolean hasNoDriverRouteToTarget(PlayerState player, Point target) {
        return !simulation.map.hasDriverPath(player.driverRanger.position(), target);
    }

    /**
     * Рассчитывает положительный вес для достижимой цели водителя.
     * Водитель — логистическая поддержка, поэтому его вес намеренно ниже весов
     * срочной поимки охотником или инженером.
     *
     * @param player игрок, чей водитель оценивается
     * @param target достижимая цель водителя
     * @return положительная оценка полезного водительского действия
     */
    private AiScore scoreReachableDriverTarget(PlayerState player, Point target) {
        int distance = simulation.map.driverPathDistance(player.driverRanger.position(), target);
        double distanceBonus = Math.min(SCORE_ROUTE_DISTANCE_BONUS_MAX, Math.max(0, distance - 1) * 2.0);
        double score = SCORE_USEFUL_ROUTE_BASE + distanceBonus;

        return new AiScore(
                score,
                "водитель может проехать к цели по дорогам или мостам, расстояние по маршруту: " + distance
        );
    }
}
