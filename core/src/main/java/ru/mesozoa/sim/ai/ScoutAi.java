package ru.mesozoa.sim.ai;

import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.simulation.GameSimulation;

public class ScoutAi {

    private final GameSimulation simulation;

    public ScoutAi(GameSimulation gameSimulation) {
        this.simulation = gameSimulation;
    }

    /**
     * Разведчик полезен, пока есть тайлы в мешке.
     *
     * Но он больше не получает абсолютный приоритет всегда и везде.
     * Если на карте уже есть конкретная цель для охотника или инженера,
     * эти роли смогут перебить разведчика по весу.
     */
    public double scoreScout(PlayerState player) {

        if (simulation.tileBag.isEmpty()) {
            simulation.log("мешочек основных тайлов пуст, разведка невозможна");
            return -100.0;
        }

        double score = 35.0;

        if (!hasVisibleNeededDinosaur(player)) {
            score += 25.0;
            simulation.log("на карте нет нужных динозавров, стоит провести разведку");
        } else {
            simulation.log("на карте уже есть нужный динозавр, разведка не главный приоритет");
        }

        return score;
    }

    /**
     * Проверяет, есть ли на карте хотя бы один живой и ещё не пойманный динозавр,
     * который нужен текущему игроку для выполнения задания.
     *
     * @param player игрок, для которого проверяется наличие нужных динозавров
     * @return true, если на карте есть хотя бы один нужный игроку динозавр,
     *         который не пойман и не удалён с карты
     */
    private boolean hasVisibleNeededDinosaur(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .anyMatch(d -> player.needs(d.species));
    }
}
