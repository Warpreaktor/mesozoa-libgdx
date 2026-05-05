package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayList;

/**
 * Исполнитель движения динозавров в фазу «Динозавры живут».
 * Класс отвечает только за то, чтобы активные звери сделали свой шаг по
 * био-тропе или другие возможные активности относящиеся к фазе хода.
 */
public final class DinosaurActionPlanner {

    private final GameSimulation simulation;
    private final DinosaurAi dinosaurAi;

    /**
     * Создаёт исполнитель движения динозавровой фазы.
     *
     * @param simulation текущая симуляция
     * @param dinosaurAi AI динозавров и био-троп
     */
    public DinosaurActionPlanner(GameSimulation simulation, DinosaurAi dinosaurAi) {
        this.simulation = simulation;
        this.dinosaurAi = dinosaurAi;
    }

    /**
     * Выполняет только перемещение всех активных динозавров.
     */
    public void dinosaurPhase() {
        for (Dinosaur dinosaur : new ArrayList<>(simulation.dinosaurs)) {
            if (dinosaur.captured || dinosaur.trapped || dinosaur.removed) continue;

            Point before = dinosaur.position;
            dinosaur.lastPosition = before;
            dinosaurAi.moveByBioTrail(dinosaur);

            if (!before.equals(dinosaur.position)) {
                simulation.log(dinosaur.displayName + " #" + dinosaur.id + " " + before + " -> " + dinosaur.position);
            }
        }
    }
}
