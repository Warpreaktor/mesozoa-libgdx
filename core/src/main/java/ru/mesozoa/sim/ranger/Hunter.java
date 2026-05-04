package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/** Охотник: выслеживает M-травоядных и охотится на хищников. */
public class Hunter extends AbstractRanger {
    public Hunter(Point startPosition) {
        super(RangerRole.HUNTER, "охотник", startPosition, 2);
    }
}
