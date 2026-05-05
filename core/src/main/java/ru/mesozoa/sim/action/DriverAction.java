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
     * Водитель работает в два видимых этапа: сначала приезжает к клетке с
     * обездвиженным динозавром, а уже следующей активацией везёт его на базу.
     * Иначе фишка зверя исчезает с карты мгновенно, будто джип освоил
     * телепортацию, а это уже не настолка, а финансовый отчёт с чудесами.
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
     * Если водитель ещё не приехал к ловушке, эта активация только ставит джип на
     * клетку с динозавром. Доставка и зачёт происходят отдельной активацией, когда
     * водитель уже стоит рядом с грузом.
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

        Dinosaur dinosaur = trappedDinosaur.get();
        if (!player.driverRanger.position().equals(dinosaur.position)) {
            driveToTrappedDinosaur(player, dinosaur, actionPoints);
            return;
        }

        extractTrappedDinosaur(player, dinosaur, actionPoints);
    }

    /**
     * Выбирает ближайшего динозавра, к которому водитель может начать рейс.
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
     * Перегоняет джип к клетке с обездвиженным динозавром без зачёта по заданию.
     *
     * @param player игрок, чей водитель едет к ловушке
     * @param dinosaur динозавр, ожидающий вывоза
     * @param actionPoints доступные очки действий
     */
    private void driveToTrappedDinosaur(PlayerState player, Dinosaur dinosaur, int actionPoints) {
        if (actionPoints < 1) {
            simulation.log("Водителю игрока " + player.id + " не хватает действий, чтобы доехать до ловушки");
            return;
        }

        if (!simulation.canDriverExtractTrappedDinosaur(player, dinosaur)) {
            simulation.log("Водитель игрока " + player.id
                    + " не может доехать до " + dinosaur.displayName
                    + ": нет связанной дороги до ловушки и обратного маршрута на базу");
            return;
        }

        player.setPosition(RangerRole.DRIVER, dinosaur.position);
        simulation.log("ВОДИТЕЛЬ ПРИЕХАЛ: водитель игрока " + player.id
                + " доехал до " + dinosaur.displayName
                + " #" + dinosaur.id
                + " на " + dinosaur.position
                + "; динозавр всё ещё ждёт доставки на базу");
    }

    /**
     * Доставляет динозавра на базу, если водитель уже стоит на клетке ловушки.
     *
     * @param player игрок, чей водитель выполняет обратный рейс
     * @param dinosaur динозавр в ловушке
     * @param actionPoints доступные очки действий
     */
    private void extractTrappedDinosaur(PlayerState player, Dinosaur dinosaur, int actionPoints) {
        if (actionPoints < 1) {
            simulation.log("Водителю игрока " + player.id + " не хватает действий для обратного рейса на базу");
            return;
        }

        if (!player.driverRanger.position().equals(dinosaur.position)) {
            simulation.log("Водитель игрока " + player.id
                    + " ещё не приехал к " + dinosaur.displayName
                    + " #" + dinosaur.id + "; сначала нужен рейс к ловушке");
            return;
        }

        if (!simulation.canDriverExtractTrappedDinosaur(player, dinosaur)) {
            simulation.log("Водитель игрока " + player.id
                    + " не может вывезти " + dinosaur.displayName
                    + ": нет связанной дороги обратно на базу");
            return;
        }

        if (simulation.extractTrappedDinosaurToBase(player, dinosaur)) {
            player.setPosition(RangerRole.DRIVER, simulation.map.base);
        }
    }
}
