package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayList;
import java.util.Set;

/**
 * Исполнитель движения динозавров в фазу «Динозавры живут».
 * Класс отвечает только за то, чтобы активные звери сделали свой шаг по
 * био-тропе. Какие звери получают обычный шаг в этой фазе, решает внешний
 * дирижёр симуляции, а не сам исполнитель движения.
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
     * Выполняет перемещение всех активных динозавров, которым разрешён обычный
     * шаг фазы «Динозавры живут».
     *
     * @param skippedDinosaurIds динозавры, чей обычный шаг в эту фазу пропускается
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
