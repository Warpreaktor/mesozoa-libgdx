package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.action.RangerActionExecutor;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/**
 * Игровая фигурка рейнджера.
 *
 * Интерфейс отделяет состояние конкретной фигурки от PlayerState и GameSimulation.
 * Симуляция выбирает RangerPlan, а затем выбранная фигурка исполняет этот план.
 */
public interface Ranger {

    /** @return роль рейнджера в команде игрока */
    RangerRole role();

    /** @return название фигурки для логов */
    String displayName();

    /** @return текущая позиция фигурки на карте */
    Point position();

    /** @param position новая позиция фигурки */
    void setPosition(Point position);

    /** @return максимальное количество очков действий за активацию */
    int maxActionPoints();

    /** @return оставшиеся очки действий в текущей активации */
    int currentActionPoints();

    /** Начинает активацию и восстанавливает очки действий. */
    void startActivation();

    /**
     * Тратит очки действий.
     *
     * @param points количество очков действий
     */
    void spendActionPoints(int points);

    /** Завершает активацию фигурки. */
    void finishActivation();

    /**
     * Исполняет выбранный план через общий исполнитель действий.
     *
     * @param plan выбранный план активации
     * @param executor исполнитель действий рейнджеров
     * @param player владелец фигурки
     */
    default void play(RangerPlan plan, RangerActionExecutor executor, PlayerState player) {
        executor.executePlanForRanger(player, plan);
    }
}
