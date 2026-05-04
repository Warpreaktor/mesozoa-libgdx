package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.model.Trap;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayList;

/**
 * Исполнитель фазы «Динозавры живут».
 *
 * GameSimulation только решает, когда наступила фаза динозавров. Все детали
 * обхода динозавров, проверки ловушек, засад и мелких пакостей Криптогната
 * живут здесь. Потому что симуляция — дирижёр, а не каждый музыкант сразу.
 */
public final class DinosaurActionPlanner {

    private final GameSimulation simulation;
    private final DinosaurAi dinosaurAi;

    /**
     * Создаёт исполнитель динозавровой фазы.
     *
     * @param simulation текущая симуляция
     * @param dinosaurAi AI динозавров и био-троп
     */
    public DinosaurActionPlanner(GameSimulation simulation, DinosaurAi dinosaurAi) {
        this.simulation = simulation;
        this.dinosaurAi = dinosaurAi;
    }

    /**
     * Выполняет полный ход всех активных динозавров.
     */
    public void dinosaurPhase() {
        for (Dinosaur dinosaur : new ArrayList<>(simulation.dinosaurs)) {
            if (dinosaur.captured || dinosaur.trapped || dinosaur.removed) continue;

            Point before = dinosaur.position;
            dinosaur.lastPosition = before;
            dinosaurAi.moveByBioTrail(dinosaur);

            if (!before.equals(dinosaur.position)) {
                simulation.log(dinosaur.species.displayName + " #" + dinosaur.id + " " + before + " -> " + dinosaur.position);
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

    /**
     * Проверяет, вошёл ли M-хищник на клетку активной приманки и чем закончилась охота.
     *
     * @param dinosaur только что переместившийся динозавр
     */
    private void resolveHuntAmbush(Dinosaur dinosaur) {
        if (dinosaur.species.captureMethod != CaptureMethod.HUNT) return;
        if (dinosaur.lastPosition == null || dinosaur.lastPosition.equals(dinosaur.position)) return;

        for (PlayerState player : simulation.players) {
            if (player.activeHunt == null) continue;
            if (player.activeHunt.dinosaurId != dinosaur.id) continue;
            if (!player.activeHunt.baitPosition.equals(dinosaur.position)) continue;
            if (!player.needs(dinosaur.species)) {
                player.activeHunt = null;
                continue;
            }

            int dinosaurRoll = simulation.random.nextInt(6) + 1;
            int dinosaurScore = dinosaurRoll + dinosaur.species.agility;
            int hunterScore = player.activeHunt.preparationScore();

            if (hunterScore > dinosaurScore) {
                dinosaur.captured = true;
                player.captured.add(dinosaur.species);
                player.activeHunt = null;
                simulation.result.huntCaptures++;
                simulation.log("ПОЙМАН: охотник игрока " + player.id
                        + " усыпил " + dinosaur.species.displayName
                        + " #" + dinosaur.id
                        + " в засаде; подготовка " + hunterScore
                        + " против " + dinosaurScore
                        + " (1d6=" + dinosaurRoll + ")");
                return;
            }

            player.hunterRanger.setPosition(simulation.map.base);
            player.activeHunt = null;
            simulation.log("ПРОВАЛ ОХОТЫ: " + dinosaur.species.displayName
                    + " #" + dinosaur.id
                    + " обошёл засаду игрока " + player.id
                    + "; подготовка " + hunterScore
                    + " против " + dinosaurScore
                    + " (1d6=" + dinosaurRoll + "). Охотник вернулся на базу");
            return;
        }
    }

    /**
     * Проверяет срабатывание ловушки после перемещения динозавра.
     *
     * @param dinosaur динозавр, который только что переместился
     */
    private void checkTrapCapture(Dinosaur dinosaur) {
        if (dinosaur.species.captureMethod != CaptureMethod.TRAP) return;
        if (dinosaur.lastPosition == null || dinosaur.lastPosition.equals(dinosaur.position)) return;

        for (PlayerState player : simulation.players) {
            if (!player.needs(dinosaur.species)) continue;

            for (Trap trap : player.traps) {
                if (trap.canCatchDinosaur() && trap.position.equals(dinosaur.position)) {
                    dinosaur.trapped = true;
                    dinosaur.trappedByPlayerId = player.id;
                    trap.trappedDinosaurId = dinosaur.id;
                    simulation.result.trapCaptures++;
                    simulation.log("В ЛОВУШКЕ: игрок " + player.id + " поймал "
                            + dinosaur.species.displayName
                            + " #" + dinosaur.id
                            + "; нужен водитель для вывоза на базу");
                    return;
                }
            }
        }
    }

    private void stealBaitIfPossible(Dinosaur dinosaur) {
        for (PlayerState player : simulation.players) {
            if (player.hunterBait > 0 && player.hunterRanger.position().equals(dinosaur.position)) {
                player.hunterBait--;
                simulation.log("Криптогнат украл приманку у игрока " + player.id);
            }
        }
    }
}
