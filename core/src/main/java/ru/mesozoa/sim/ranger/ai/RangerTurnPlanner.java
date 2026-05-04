package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.ranger.Ranger;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.ranger.RangerPlanType;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ru.mesozoa.sim.model.RangerRole.DRIVER;
import static ru.mesozoa.sim.model.RangerRole.ENGINEER;
import static ru.mesozoa.sim.model.RangerRole.HUNTER;
import static ru.mesozoa.sim.model.RangerRole.SCOUT;

/**
 * Выбор плана для очередной активации игрока.
 *
 * Planner больше не выбирает голую роль. Он выбирает RangerPlan: конкретную
 * фигурку, оценку, причину и целевую точку. Поэтому action-слой получает хотя бы
 * базовый контекст того, зачем была выбрана фигурка, а не просто "ENGINEER, делай
 * что-нибудь". Это, как ни странно, полезно.
 */
public final class RangerTurnPlanner {

    /** Минимальная оценка, при которой план считается полезным для активации. */
    private static final double MIN_USEFUL_SCORE = 0.0;

    /** Максимальная разница очков, при которой планы считаются примерно равными. */
    private static final double NEAR_TIE_SCORE_DELTA = 5.0;

    private final GameSimulation simulation;
    private final ScoutAi scoutAi;
    private final HunterAi hunterAi;
    private final DriverAi driverAi;
    private final EngineerAi engineerAi;

    public RangerTurnPlanner(GameSimulation simulation) {
        this.simulation = simulation;
        this.scoutAi = new ScoutAi(simulation);
        this.hunterAi = new HunterAi(simulation);
        this.driverAi = new DriverAi(simulation);
        this.engineerAi = new EngineerAi(simulation);
    }

    /**
     * Выбирает следующий план активации для текущего игрока.
     *
     * @param player игрок, который сейчас ходит
     * @param alreadyUsedRoles роли, уже активированные этим игроком в текущем ходе
     * @return лучший план активации или null, если полезного действия нет
     */
    public RangerPlan chooseNextPlanForTurn(PlayerState player, Set<RangerRole> alreadyUsedRoles) {
        ArrayList<RangerPlan> candidates = new ArrayList<>();
        AiScore bestScore = new AiScore(Double.NEGATIVE_INFINITY, "нет оценки");

        for (RangerRole role : List.of(SCOUT, ENGINEER, HUNTER, DRIVER)) {
            if (alreadyUsedRoles.contains(role)) {
                continue;
            }

            RangerPlan plan = planRole(player, role);
            candidates.add(plan);

            if (plan.scoreValue() > bestScore.value()) {
                bestScore = plan.score();
            }
        }

        if (candidates.isEmpty()) {
            simulation.log("AI игрок " + player.id + ": нет доступного рейнджера для активации");
            return null;
        }

        if (bestScore.value() <= MIN_USEFUL_SCORE) {
            RangerPlan bestCandidate = candidates.stream()
                    .max(Comparator.comparingDouble(RangerPlan::scoreValue))
                    .orElse(null);

            simulation.log("AI игрок " + player.id
                    + ": нет полезного плана для активации; лучший кандидат "
                    + roleToText(bestCandidate.role())
                    + " получил оценку " + bestCandidate.scoreValue()
                    + " — " + bestCandidate.reason());
            return null;
        }

        AiScore finalBestScore = bestScore;
        List<RangerPlan> topCandidates = candidates.stream()
                .filter(candidate -> finalBestScore.value() - candidate.scoreValue() <= NEAR_TIE_SCORE_DELTA)
                .toList();

        RangerPlan selected = topCandidates.get(simulation.random.nextInt(topCandidates.size()));

        simulation.log("AI игрок " + player.id
                + ": выбран " + roleToText(selected.role())
                + " [" + selected.type() + "]"
                + " с оценкой " + selected.scoreValue()
                + " — " + selected.reason());

        return selected;
    }

    private RangerPlan planRole(PlayerState player, RangerRole role) {
        AiScore score = scoreRole(player, role);
        Ranger ranger = player.rangerFor(role);
        return new RangerPlan(ranger, planTypeFor(role), score, targetFor(player, role));
    }

    private RangerPlanType planTypeFor(RangerRole role) {
        return switch (role) {
            case SCOUT -> RangerPlanType.SCOUT_EXPLORE;
            case ENGINEER -> RangerPlanType.ENGINEER_WORK;
            case HUNTER -> RangerPlanType.HUNTER_CAPTURE_OR_MOVE;
            case DRIVER -> RangerPlanType.DRIVER_MOVE;
        };
    }

    private Point targetFor(PlayerState player, RangerRole role) {
        return switch (role) {
            case SCOUT -> null;
            case ENGINEER -> engineerTarget(player).orElse(player.scoutRanger.position());
            case HUNTER -> hunterTarget(player).orElse(player.scoutRanger.position());
            case DRIVER -> driverTarget(player);
        };
    }

    private Optional<Point> hunterTarget(PlayerState player) {
        return nearestNeededDinosaur(player, player.hunterRanger.position(), CaptureMethod.TRACKING, CaptureMethod.HUNT)
                .map(dinosaur -> dinosaur.position);
    }

    private Optional<Point> engineerTarget(PlayerState player) {
        Optional<Point> capturedTarget = simulation.dinosaurs.stream()
                .filter(dinosaur -> simulation.isTrappedByPlayer(dinosaur, player))
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineerRanger.position().manhattan(dinosaur.position)))
                .map(dinosaur -> dinosaur.position);
        if (capturedTarget.isPresent()) return capturedTarget;

        Optional<Point> trapTarget = nearestTrapAmbushPoint(player);
        if (trapTarget.isPresent()) return trapTarget;

        return hunterTarget(player);
    }

    /**
     * Выбирает ближайшую реальную клетку засады для инженера.
     * В план больше не попадает текущая позиция динозавра: action-слой не должен
     * потом гадать, хотел ли AI ловушку, дорогу или просто красный значок на карте.
     *
     * @param player игрок, для которого строится план инженера
     * @return ближайшая клетка, куда можно поставить новую ловушку
     */
    private Optional<Point> nearestTrapAmbushPoint(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.TRAP)
                .flatMap(dinosaur -> simulation.trapAmbushCandidatesFor(dinosaur).stream())
                .filter(point -> simulation.map.canPlaceTrap(point))
                .filter(point -> player.traps.stream().noneMatch(trap -> trap.active && trap.position.equals(point)))
                .filter(point -> simulation.dinosaurs.stream()
                        .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                        .noneMatch(dinosaur -> dinosaur.position.equals(point)))
                .min(Comparator.comparingInt(point -> point.manhattan(player.engineerRanger.position())));
    }

    private Point driverTarget(PlayerState player) {
        return driverAi.chooseDriverTarget(player);
    }

    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        EnumSet<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        for (CaptureMethod method : methods) {
            allowedMethods.add(method);
        }

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.manhattan(from)));
    }

    private String roleToText(RangerRole role) {
        return switch (role) {
            case SCOUT -> "разведчик";
            case DRIVER -> "водитель";
            case ENGINEER -> "инженер";
            case HUNTER -> "охотник";
        };
    }

    private AiScore scoreRole(PlayerState player, RangerRole role) {
        return switch (role) {
            case SCOUT -> scoutAi.scoreScout(player);
            case ENGINEER -> engineerAi.scoreEngineer(player);
            case HUNTER -> hunterAi.scoreHunter(player);
            case DRIVER -> driverAi.scoreDriver(player);
        };
    }
}
