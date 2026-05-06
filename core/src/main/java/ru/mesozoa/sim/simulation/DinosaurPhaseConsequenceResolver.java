package ru.mesozoa.sim.simulation;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.model.Trap;

/**
 * Техническая фаза разрешения последствий после хода динозавров.
 *
 * Динозавровая фаза только двигает зверей по био-тропам. После этого отдельный
 * резолвер проходит по состоянию карты и применяет правила, которые срабатывают
 * из-за новой позиции динозавра: ловушки, охотничьи засады и мелкие пакости
 * Криптогната. Так движение зверя не превращается в склад знаний обо всей игре.
 */
public final class DinosaurPhaseConsequenceResolver {

    private final GameSimulation simulation;

    /**
     * Создаёт резолвер технических последствий динозавровой фазы.
     *
     * @param simulation текущая симуляция игры
     */
    public DinosaurPhaseConsequenceResolver(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Разрешает все правила, которые должны проверяться после завершения движения динозавров.
     */
    public void resolveAfterDinosaurPhase() {
        resolveTrapCaptures();
        resolveHuntAmbushes();
        resolveCryptognathBaitTheft();
    }

    /**
     * Проверяет срабатывание сетевых ловушек после завершения перемещения динозавров.
     */
    private void resolveTrapCaptures() {
        for (Dinosaur dinosaur : simulation.dinosaurs) {
            if (!isMovedAndFree(dinosaur)) continue;
            if (dinosaur.captureMethod != CaptureMethod.TRAP) continue;

            for (PlayerState player : simulation.players) {
                for (Trap trap : player.traps) {
                    if (trap.canCatchDinosaur() && trap.position.equals(dinosaur.position)) {
                        dinosaur.trapped = true;
                        dinosaur.trappedByPlayerId = player.id;
                        trap.trappedDinosaurId = dinosaur.id;
                        simulation.result.trapCaptures++;
                        simulation.log("В ЛОВУШКЕ: ловушка игрока " + player.id + " удержала "
                                + dinosaur.displayName
                                + " #" + dinosaur.id
                                + "; вывезти добычу может любой водитель с дорогой к клетке");
                        break;
                    }
                }

                if (dinosaur.trapped) break;
            }
        }
    }

    /**
     * Проверяет, вошли ли M-хищники в клетки активных охотничьих засад.
     */
    private void resolveHuntAmbushes() {
        for (Dinosaur dinosaur : simulation.dinosaurs) {
            if (!isMovedAndFree(dinosaur)) continue;
            if (dinosaur.captureMethod != CaptureMethod.HUNT) continue;

            resolveHuntAmbushFor(dinosaur);
        }
    }

    /**
     * Разрешает результат одной активной засады, если указанный хищник вошёл в клетку приманки.
     *
     * @param dinosaur M-хищник, который переместился в динозавровую фазу
     */
    private void resolveHuntAmbushFor(Dinosaur dinosaur) {
        for (PlayerState player : simulation.players) {
            if (player.activeHunt == null) continue;
            if (player.activeHunt.dinosaurId != dinosaur.id) continue;
            if (!player.activeHunt.baitPosition.equals(dinosaur.position)) continue;
            if (!player.needs(dinosaur.species)) {
                player.activeHunt = null;
                continue;
            }

            int dinosaurRoll = simulation.random.nextInt(6) + 1;
            int dinosaurScore = dinosaurRoll + dinosaur.agility;
            int hunterScore = player.activeHunt.preparationScore();

            if (hunterScore > dinosaurScore) {
                dinosaur.trapped = true;
                dinosaur.trappedByPlayerId = player.id;
                player.clearCaptureFailures(dinosaur.id);
                player.activeHunt = null;
                simulation.result.huntCaptures++;
                simulation.log("УСЫПЛЁН: охотник игрока " + player.id
                        + " обездвижил " + dinosaur.displayName
                        + " #" + dinosaur.id
                        + " в засаде; подготовка " + hunterScore
                        + " против " + dinosaurScore
                        + " (1d6=" + dinosaurRoll + ")"
                        + "; добычу засчитает первый водитель, который вывезет её на базу");
                return;
            }

            player.hunterRanger.setPosition(simulation.map.base);
            player.registerFailedHuntAttempt(dinosaur.id);
            player.activeHunt = null;
            simulation.log("ПРОВАЛ ОХОТЫ: " + dinosaur.displayName
                    + " #" + dinosaur.id
                    + " обошёл засаду игрока " + player.id
                    + "; подготовка " + hunterScore
                    + " против " + dinosaurScore
                    + " (1d6=" + dinosaurRoll + "). Охотник вернулся на базу");
            return;
        }
    }

    /**
     * Проверяет кражу приманки Криптогнатом после завершения перемещения динозавров.
     */
    private void resolveCryptognathBaitTheft() {
        for (Dinosaur dinosaur : simulation.dinosaurs) {
            if (!isMovedAndFree(dinosaur)) continue;
            if (dinosaur.species != Species.CRYPTOGNATH) continue;

            for (PlayerState player : simulation.players) {
                if (player.hunterBait > 0 && player.hunterRanger.position().equals(dinosaur.position)) {
                    player.hunterBait--;
                    simulation.log("Криптогнат украл приманку у игрока " + player.id);
                }
            }
        }
    }

    /**
     * Проверяет, что динозавр действительно вошёл в новую клетку и ещё может вызывать последствия шага.
     *
     * @param dinosaur проверяемый динозавр
     * @return true, если динозавр двигался в завершённую фазу и не был уже убран из активной игры
     */
    private boolean isMovedAndFree(Dinosaur dinosaur) {
        return dinosaur != null
                && !dinosaur.captured
                && !dinosaur.trapped
                && !dinosaur.removed
                && dinosaur.lastPosition != null
                && !dinosaur.lastPosition.equals(dinosaur.position);
    }
}
