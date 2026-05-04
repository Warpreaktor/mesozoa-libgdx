package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/** Водитель: ездит только по связанной сети дорог и мостов. */
public class Driver extends AbstractRanger {
    public Driver(Point startPosition) {
        super(RangerRole.DRIVER, "водитель", startPosition, 2);
    }
}
