package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/** Разведчик: открывает карту и игнорирует обычную наземную проходимость. */
public class Scout extends AbstractRanger {
    public Scout(Point startPosition) {
        super(RangerRole.SCOUT, "разведчик", startPosition, 2);
    }
}
