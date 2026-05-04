package ru.mesozoa.sim.action;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Optional;

public class DriverAction {

    private final GameSimulation simulation;

    private final RangerActionExecutor rangerActionExecutor;

    public DriverAction(GameSimulation simulation,
                        RangerActionExecutor rangerActionExecutor) {

        this.simulation = simulation;
        this.rangerActionExecutor = rangerActionExecutor;
    }

    /**
     * Выполняет активацию водителя.
     *
     * Водитель больше не пытается «приближаться» к другим рейнджерам. Его полезная
     * задача появляется только после срабатывания ловушки: доехать по готовой
     * дорожной сети до динозавра, забрать его и вернуться на базу.
     *
     * @param player игрок, чей водитель активируется
     * @param plan выбранный AI-план
     */
    public void action(PlayerState player, RangerPlan plan) {
        Point target = plan.target() == null ? chooseDriverTarget(player) : plan.target();
        int actionPoints = plan.ranger().currentActionPoints();
        action(player, target, actionPoints);
        plan.ranger().spendActionPoints(actionPoints);
    }

    /**
     * Выполняет водительское действие по выбранной клетке.
     *
     * @param player игрок, чей водитель действует
     * @param target целевая клетка с ловушкой и динозавром
     * @param actionPoints доступные очки действия водителя
     */
    private void action(PlayerState player, Point target, int actionPoints) {
        if (target == null) {
            simulation.log("Водитель игрока " + player.id + " ждёт задачи: вывозить пока некого");
            return;
        }

        Optional<Dinosaur> trappedDinosaur = trappedDinosaurAt(player, target);
        if (trappedDinosaur.isEmpty()) {
            simulation.log("Водитель игрока " + player.id + " не нашёл динозавра в ловушке на " + target);
            return;
        }

        extractTrappedDinosaur(player, trappedDinosaur.get(), actionPoints);
    }

    /**
     * Выбирает ближайшего динозавра, которого водитель может вывезти прямо сейчас.
     *
     * @param player игрок, для которого выбирается цель водителя
     * @return клетка динозавра в ловушке или null, если готовой цели нет
     */
    public Point chooseDriverTarget(PlayerState player) {
        return simulation.nearestTrappedNeededDinosaurAwaitingPickup(
                        player,
                        player.driverRanger.position(),
                        true
                )
                .map(dinosaur -> dinosaur.position)
                .orElse(null);
    }

    /**
     * Ищет динозавра игрока, сидящего в ловушке на указанной клетке.
     *
     * @param player игрок-владелец ловушки
     * @param target клетка проверки
     * @return динозавр в ловушке
     */
    private Optional<Dinosaur> trappedDinosaurAt(PlayerState player, Point target) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> simulation.isTrappedByPlayer(dinosaur, player))
                .filter(dinosaur -> dinosaur.position.equals(target))
                .findFirst();
    }

    /**
     * Вывозит динозавра из ловушки, если хватает действий и дорожная сеть готова.
     *
     * @param player игрок, чей водитель выполняет вывоз
     * @param dinosaur динозавр в ловушке
     * @param actionPoints доступные очки действий
     */
    private void extractTrappedDinosaur(PlayerState player, Dinosaur dinosaur, int actionPoints) {
        if (actionPoints < 2) {
            simulation.log("Водителю игрока " + player.id + " не хватает действий для рейса туда-обратно");
            return;
        }

        if (!simulation.canDriverExtractTrappedDinosaur(player, dinosaur)) {
            simulation.log("Водитель игрока " + player.id
                    + " не может вывезти " + dinosaur.species.displayName
                    + ": нет связанной дороги до ловушки и обратно на базу");
            return;
        }

        player.setPosition(RangerRole.DRIVER, dinosaur.position);
        if (simulation.extractTrappedDinosaurToBase(player, dinosaur)) {
            player.setPosition(RangerRole.DRIVER, simulation.map.base);
        }
    }
}
