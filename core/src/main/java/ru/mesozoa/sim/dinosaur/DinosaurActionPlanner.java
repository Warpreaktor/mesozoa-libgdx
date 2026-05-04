package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;

import java.util.ArrayList;

public class DinosaurActionPlanner {

    private void dinosaurPhase() {
        for (Dinosaur dinosaur : new ArrayList<>(dinosaurs)) {
            if (dinosaur.captured || dinosaur.trapped || dinosaur.removed) continue;

            Point before = dinosaur.position;
            dinosaur.lastPosition = before;
            moveByBioTrail(dinosaur);

            if (!before.equals(dinosaur.position)) {
                log(dinosaur.species.displayName + " #" + dinosaur.id + " " + before + " -> " + dinosaur.position);
                checkTrapCapture(dinosaur);
                resolveHuntAmbush(dinosaur);
            }

            if (dinosaur.trapped || dinosaur.captured) {
                continue;
            }

            if (dinosaur.species == Species.CRYPTOGNATH) {
                stealBaitIfPossible(dinosaur);
            }
        }
    }
}
