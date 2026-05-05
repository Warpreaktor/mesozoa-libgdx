package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/**
 * Разведчик: открывает один новый участок карты и не тратит очки на перемещение.
 *
 * По правилам симуляции разведчик летает на любое расстояние. Его единственное
 * полезное действие за активацию — разведать новую клетку карты. Поэтому у него
 * нет очков движения: иначе AI начинает лечить вертолёт как пехотинца, а это уже
 * не стратегия, а издевательство над авиацией.
 */
public class Scout extends AbstractRanger {
    public Scout(Point startPosition) {
        super(RangerRole.SCOUT, "разведчик", startPosition, 0);
    }
}
