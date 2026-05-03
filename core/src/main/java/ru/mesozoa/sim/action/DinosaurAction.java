package ru.mesozoa.sim.action;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.simulation.GameSimulation;

public class DinosaurAction {

    private final GameSimulation simulation;

    public DinosaurAction(GameSimulation simulation) {
        this.simulation = simulation;
    }

    public void spawnDinosaur(Species species, Point position) {
        Dinosaur dino = new Dinosaur(simulation.nextDinoId++, species, position);
        simulation.dinosaurs.add(dino);
        simulation.result.firstSpawnRound.putIfAbsent(species, simulation.round);
        simulation.log("СПАУН: " + species.displayName + " на " + position);
    }
}
