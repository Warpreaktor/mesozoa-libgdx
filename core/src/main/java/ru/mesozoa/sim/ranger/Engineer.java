package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/** Инженер: строит дороги, мосты и ставит ловушки. */
public class Engineer extends AbstractRanger {
    public Engineer(Point startPosition) {
        super(RangerRole.ENGINEER, "инженер", startPosition, 2);
    }
}
