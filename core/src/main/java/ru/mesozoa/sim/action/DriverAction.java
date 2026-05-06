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
     * В конкурентном режиме водитель делает полный рейс за одну активацию:
     * доезжает по своей дорожной сети до обездвиженного динозавра, забирает
     * добычу и возвращается на базу. Поэтому чужая ловушка без защищённой
     * логистики становится добычей того, кто быстрее подведёт дорогу.
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
     * Выполняет водительский рейс к выбранной клетке с добычей.
     *
     * @param player игрок, чей водитель действует
     * @param target клетка с обездвиженным динозавром
     * @param actionPoints доступные очки действия водителя
     */
    private void action(PlayerState player, Point target, int actionPoints) {
        if (target == null) {
            simulation.log("Водитель игрока " + player.id + " ждёт задачи: вывозить пока некого");
            return;
        }

        Optional<Dinosaur> trappedDinosaur = trappedDinosaurAt(player, target);
        if (trappedDinosaur.isEmpty()) {
            simulation.log("Водитель игрока " + player.id + " не нашёл добычу на " + target);
            return;
        }

        Dinosaur dinosaur = trappedDinosaur.get();
        extractTrappedDinosaur(player, dinosaur, actionPoints);
    }

    /**
     * Выбирает ближайшего динозавра, которого водитель может вывезти.
     *
     * @param player игрок, для которого выбирается цель водителя
     * @return клетка добычи или null, если готовой цели нет
     */
    public Point chooseDriverTarget(PlayerState player) {
        return simulation.nearestAwaitingPickupDinosaur(
                        player,
                        player.driverRanger.position(),
                        true,
                        false
                )
                .map(dinosaur -> dinosaur.position)
                .orElse(null);
    }

    /**
     * Ищет обездвиженного динозавра на указанной клетке.
     *
     * Если на клетке есть несколько теоретических целей, водитель предпочитает
     * вид из своего задания, а затем более дорогую бонусную добычу. Да,
     * моральная сторона кражи добычи оставлена за пределами симуляции, как и
     * положено семейной настолке про динозавров.
     *
     * @param player игрок, чей водитель выбирает добычу
     * @param target клетка проверки
     * @return динозавр, которого можно вывезти
     */
    private Optional<Dinosaur> trappedDinosaurAt(PlayerState player, Point target) {
        return simulation.dinosaurs.stream()
                .filter(simulation::isAwaitingPickup)
                .filter(dinosaur -> dinosaur.position.equals(target))
                .sorted(java.util.Comparator
                        .comparingInt((Dinosaur dinosaur) -> player.needs(dinosaur.species) ? 0 : 1)
                        .thenComparing(java.util.Comparator.comparingInt(simulation::capturePointsFor).reversed()))
                .findFirst();
    }

    /**
     * Доставляет динозавра на базу за одну водительскую активацию.
     *
     * @param player игрок, чей водитель выполняет рейс
     * @param dinosaur динозавр в ловушке, по следу или после охоты
     * @param actionPoints доступные очки действий
     */
    private void extractTrappedDinosaur(PlayerState player, Dinosaur dinosaur, int actionPoints) {
        if (actionPoints < 2) {
            simulation.log("Водителю игрока " + player.id + " не хватает действий для полного рейса за добычей");
            return;
        }

        if (!simulation.canDriverExtractTrappedDinosaur(player, dinosaur)) {
            simulation.log("Водитель игрока " + player.id
                    + " не может вывезти " + dinosaur.displayName
                    + ": нет связанной дороги от водителя до добычи и обратно на базу");
            return;
        }

        Point before = player.driverRanger.position();
        if (simulation.extractTrappedDinosaurToBase(player, dinosaur)) {
            player.setPosition(RangerRole.DRIVER, simulation.map.base);
            simulation.log("ВОДИТЕЛЬСКИЙ РЕЙС: игрок " + player.id
                    + " " + before + " -> " + dinosaur.position + " -> " + simulation.map.base);
        }
    }
}
