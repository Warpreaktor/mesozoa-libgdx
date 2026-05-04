package ru.mesozoa.sim.ranger;

/**
 * Тип выбранного плана активации рейнджера.
 *
 * Типы пока крупные, но уже фиксируют стратегию: исполнитель получает не просто
 * роль, а намерение, с которым эта роль была выбрана.
 */
public enum RangerPlanType {
    SCOUT_EXPLORE,
    HUNTER_CAPTURE_OR_MOVE,
    ENGINEER_WORK,
    DRIVER_MOVE,
    WAIT
}
