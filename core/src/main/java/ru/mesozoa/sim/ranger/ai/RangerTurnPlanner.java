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

    /**
     * Совместимый старый метод выбора роли.
     *
     * @deprecated Используй {@link #chooseNextPlanForTurn(PlayerState, Set)}.
     */
    @Deprecated(forRemoval = true)
    public RangerRole chooseNextRangerForTurn(PlayerState player, Set<RangerRole> alreadyUsedRoles) {
        RangerPlan plan = chooseNextPlanForTurn(player, alreadyUsedRoles);
        return plan == null ? null : plan.role();
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
            case ENGINEER -> engineerTarget(player).orElse(player.scout);
            case HUNTER -> hunterTarget(player).orElse(player.scout);
            case DRIVER -> driverTarget(player);
        };
    }

    private Optional<Point> hunterTarget(PlayerState player) {
        return nearestNeededDinosaur(player, player.hunter, CaptureMethod.TRACKING, CaptureMethod.HUNT)
                .map(dinosaur -> dinosaur.position);
    }

    private Optional<Point> engineerTarget(PlayerState player) {
        Optional<Point> trapTarget = nearestNeededDinosaur(player, player.engineer, CaptureMethod.TRAP)
                .map(dinosaur -> dinosaur.position);
        if (trapTarget.isPresent()) return trapTarget;

        Optional<Point> capturedTarget = simulation.dinosaurs.stream()
                .filter(dinosaur -> dinosaur.captured)
                .filter(dinosaur -> player.captured.contains(dinosaur.species))
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineer.manhattan(dinosaur.position)))
                .map(dinosaur -> dinosaur.position);
        if (capturedTarget.isPresent()) return capturedTarget;

        return hunterTarget(player);
    }

    private Point driverTarget(PlayerState player) {
        if (!player.driver.equals(player.hunter)) {
            return player.hunter;
        }
        if (!player.driver.equals(player.engineer)) {
            return player.engineer;
        }
        if (!player.driver.equals(player.scout)) {
            return player.scout;
        }
        return null;
    }

    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        EnumSet<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        for (CaptureMethod method : methods) {
            allowedMethods.add(method);
        }

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
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
