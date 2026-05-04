package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI-оценка полезности охотника для очередной активации игрока.
 *
 * Охотник теперь не бросается грудью на M-хищника. Для HUNT-целей он ищет клетку
 * будущего маршрута, начинает засаду и затем обязан каждый ход поддерживать эту
 * засаду. Иначе это была не охота с транквилизатором, а доставка охотника в пасть.
 */
public final class HunterAi {

    /** Вес невозможного или полностью бесполезного действия. */
    private static final double SCORE_IMPOSSIBLE = -100.0;

    /** Вес обязательного продолжения уже начатой охоты. */
    private static final double SCORE_ACTIVE_HUNT = 120.0;

    /** Максимальный вес ситуации, когда охотник уже может начать выслеживание. */
    private static final double SCORE_TRACKING_READY = 100.0;

    /** Вес ситуации, когда охотник может дойти до цели выслеживания за текущую активацию. */
    private static final double SCORE_TRACKING_REACHABLE_NOW = 90.0;

    /** Вес ситуации, когда охотник уже стоит на клетке будущей засады. */
    private static final double SCORE_HUNT_AMBUSH_READY = 105.0;

    /** Вес ситуации, когда охотник может дойти до клетки засады за текущую активацию. */
    private static final double SCORE_HUNT_AMBUSH_REACHABLE_NOW = 92.0;

    /** Низкий вес ожидания рядом с разведчиком, когда видимых целей охотника пока нет. */
    private static final double SCORE_WAIT_NEAR_SCOUT = 10.0;

    /** Вес ситуации, когда охотник не может осмысленно действовать прямо сейчас. */
    private static final double SCORE_LOW_IDLE = 5.0;

    /** Количество очков движения охотника за одну активацию в текущей модели симуляции. */
    private static final int HUNTER_ACTION_POINTS = 2;

    /** Радиус, в котором охотник считается достаточно близким к разведчику. */
    private static final int NEAR_SCOUT_DISTANCE = 1;

    /** Значение расстояния, если путь до цели не найден. */
    private static final int UNREACHABLE_DISTANCE = Integer.MAX_VALUE;

    /** Текущая симуляция, из которой AI читает состояние партии. */
    private final GameSimulation simulation;

    /**
     * Создаёт AI-оценщик охотника.
     *
     * @param simulation текущая симуляция игры
     */
    public HunterAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Рассчитывает вес активации охотника для текущего игрока.
     *
     * @param player игрок, для которого оценивается полезность охотника
     * @return оценка полезности охотника и причина этой оценки
     */
    public AiScore scoreHunter(PlayerState player) {
        Set<Species> remainingHunterSpecies = remainingNeededHunterSpecies(player);
        Optional<Dinosaur> trackingTarget = nearestTrackingTarget(player);
        Optional<HuntPlan> huntPlan = bestHuntPlan(player);

        if (player.activeHunt != null) {
            return new AiScore(
                    SCORE_ACTIVE_HUNT,
                    "охотник обязан продолжать засаду на "
                            + player.activeHunt.species.displayName
                            + ", подготовка " + player.activeHunt.preparationScore()
            );
        }

        if (remainingHunterSpecies.isEmpty()) {
            return new AiScore(
                    SCORE_IMPOSSIBLE,
                    "в задании не осталось целей для охотника"
            );
        }

        if (isHunterOnTrackingTarget(player, trackingTarget)) {
            Dinosaur dinosaur = trackingTarget.get();
            return new AiScore(
                    SCORE_TRACKING_READY,
                    "охотник стоит на клетке цели выслеживания: " + dinosaur.species.displayName
            );
        }

        if (canReachTrackingTargetThisActivation(player, trackingTarget)) {
            Dinosaur dinosaur = trackingTarget.get();
            int distance = hunterPathDistance(player.hunterRanger.position(), dinosaur.position);
            return new AiScore(
                    SCORE_TRACKING_REACHABLE_NOW,
                    "охотник может дойти до цели выслеживания за текущую активацию: "
                            + dinosaur.species.displayName
                            + ", расстояние по пути: " + distance
            );
        }

        if (huntPlan.isPresent()) {
            return scoreHuntAmbushPlan(player, huntPlan.get());
        }

        if (trackingTarget.isPresent()) {
            return scoreVisibleTrackingTarget(player, trackingTarget.get());
        }

        if (hasVisibleHuntTargetWithoutBait(player)) {
            return scoreReturnForBait(player);
        }

        if (shouldCatchUpToScout(player, remainingHunterSpecies, trackingTarget, huntPlan)) {
            return scoreCatchUpToScout(player);
        }

        if (isHunterAlreadyNearScout(player, remainingHunterSpecies, trackingTarget, huntPlan)) {
            return new AiScore(
                    SCORE_WAIT_NEAR_SCOUT,
                    "видимых целей охотника нет, но охотник уже рядом с разведчиком"
            );
        }

        return new AiScore(
                SCORE_LOW_IDLE,
                "для охотника сейчас нет срочной цели или полезного перемещения"
        );
    }

    /** Оценивает план засады на M-хищника. */
    private AiScore scoreHuntAmbushPlan(PlayerState player, HuntPlan plan) {
        Point hunterPosition = player.hunterRanger.position();
        int distance = hunterPathDistance(hunterPosition, plan.baitPosition());
        int turns = simulation.dinosaurAi.estimateDinosaurTurnsTo(plan.dinosaur(), plan.baitPosition());

        if (hunterPosition.equals(plan.baitPosition())) {
            return new AiScore(
                    SCORE_HUNT_AMBUSH_READY,
                    "охотник на клетке засады для " + plan.dinosaur().species.displayName
                            + ", ожидаемый приход через " + turnsText(turns)
            );
        }

        if (distance != UNREACHABLE_DISTANCE && distance <= HUNTER_ACTION_POINTS) {
            return new AiScore(
                    SCORE_HUNT_AMBUSH_REACHABLE_NOW,
                    "охотник может занять клетку засады для " + plan.dinosaur().species.displayName
                            + " за текущую активацию; расстояние: " + distance
                            + ", ожидаемый приход через " + turnsText(turns)
            );
        }

        if (distance == UNREACHABLE_DISTANCE) {
            return new AiScore(
                    25.0,
                    "клетка засады видна, но путь охотника не найден: " + plan.dinosaur().species.displayName
            );
        }

        double score = 75.0 - Math.min(35.0, distance * 4.0);
        return new AiScore(
                score,
                "охотник идёт к клетке засады для " + plan.dinosaur().species.displayName
                        + "; расстояние: " + distance
                        + ", ожидаемый приход через " + turnsText(turns)
        );
    }

    /** Проверяет, стоит ли охотник на клетке с нужной целью для выслеживания. */
    private boolean isHunterOnTrackingTarget(PlayerState player, Optional<Dinosaur> trackingTarget) {
        return trackingTarget.isPresent()
                && player.hunterRanger.position().equals(trackingTarget.get().position);
    }

    /** Проверяет, может ли охотник дойти до цели выслеживания за текущую активацию. */
    private boolean canReachTrackingTargetThisActivation(PlayerState player, Optional<Dinosaur> trackingTarget) {
        if (trackingTarget.isEmpty()) return false;
        int distance = hunterPathDistance(player.hunterRanger.position(), trackingTarget.get().position);
        return distance > 0 && distance <= HUNTER_ACTION_POINTS;
    }

    /** Проверяет, есть ли видимая HUNT-цель, но нет приманки. */
    private boolean hasVisibleHuntTargetWithoutBait(PlayerState player) {
        if (player.hunterBait > 0) return false;
        return nearestNeededDinosaur(player, player.hunterRanger.position(), CaptureMethod.HUNT).isPresent();
    }

    /** Рассчитывает вес возврата на базу за новой приманкой. */
    private AiScore scoreReturnForBait(PlayerState player) {
        if (player.hunterRanger.position().equals(simulation.map.base)) {
            return new AiScore(
                    88.0,
                    "есть цель для охоты, охотник на базе и может пополнить приманку"
            );
        }

        int distance = hunterPathDistance(player.hunterRanger.position(), simulation.map.base);
        if (distance == UNREACHABLE_DISTANCE) {
            return new AiScore(
                    20.0,
                    "есть цель для охоты, но приманка закончилась и путь на базу не найден"
            );
        }

        double score = Math.max(45.0, 78.0 - Math.min(30.0, distance * 4.0));
        return new AiScore(
                score,
                "есть цель для охоты, но приманка закончилась; охотник возвращается на базу, расстояние: " + distance
        );
    }

    /** Проверяет, стоит ли охотнику подтянуться к разведчику. */
    private boolean shouldCatchUpToScout(
            PlayerState player,
            Set<Species> remainingHunterSpecies,
            Optional<Dinosaur> trackingTarget,
            Optional<HuntPlan> huntPlan
    ) {
        if (remainingHunterSpecies.isEmpty()) return false;
        if (trackingTarget.isPresent() || huntPlan.isPresent()) return false;

        int distance = hunterPathDistance(player.hunterRanger.position(), player.scoutRanger.position());
        return distance != UNREACHABLE_DISTANCE && distance > NEAR_SCOUT_DISTANCE;
    }

    /** Проверяет, что охотник уже находится рядом с разведчиком. */
    private boolean isHunterAlreadyNearScout(
            PlayerState player,
            Set<Species> remainingHunterSpecies,
            Optional<Dinosaur> trackingTarget,
            Optional<HuntPlan> huntPlan
    ) {
        if (remainingHunterSpecies.isEmpty()) return false;
        if (trackingTarget.isPresent() || huntPlan.isPresent()) return false;

        int distance = hunterPathDistance(player.hunterRanger.position(), player.scoutRanger.position());
        return distance <= NEAR_SCOUT_DISTANCE;
    }

    /** Рассчитывает вес видимой цели выслеживания, если до неё нельзя дойти прямо сейчас. */
    private AiScore scoreVisibleTrackingTarget(PlayerState player, Dinosaur dinosaur) {
        int distance = hunterPathDistance(player.hunterRanger.position(), dinosaur.position);

        if (distance == UNREACHABLE_DISTANCE) {
            return new AiScore(
                    25.0,
                    "цель выслеживания видна, но путь до неё не найден: " + dinosaur.species.displayName
            );
        }

        double score = 65.0 - Math.min(35.0, distance * 4.0);
        return new AiScore(
                score,
                "видимая цель выслеживания: "
                        + dinosaur.species.displayName
                        + ", расстояние по пути: " + distance
        );
    }

    /** Рассчитывает вес движения охотника к разведчику. */
    private AiScore scoreCatchUpToScout(PlayerState player) {
        int distance = hunterPathDistance(player.hunterRanger.position(), player.scoutRanger.position());

        if (distance == UNREACHABLE_DISTANCE) {
            return new AiScore(
                    15.0,
                    "видимых целей охотника нет, но путь к разведчику не найден"
            );
        }

        double score = Math.min(70.0, 20.0 + distance * 10.0);
        return new AiScore(
                score,
                "видимых целей охотника нет, охотник подтягивается к разведчику; расстояние по пути: " + distance
        );
    }

    /** Возвращает непойманные виды из задания игрока, которые должен ловить охотник. */
    private Set<Species> remainingNeededHunterSpecies(PlayerState player) {
        EnumSet<Species> result = EnumSet.noneOf(Species.class);

        for (Species species : player.task) {
            if (!player.captured.contains(species) && isHunterCaptureMethod(species.captureMethod)) {
                result.add(species);
            }
        }

        return result;
    }

    /** Ищет ближайшую живую и непойманную цель выслеживания, нужную игроку. */
    private Optional<Dinosaur> nearestTrackingTarget(PlayerState player) {
        return nearestNeededDinosaur(player, player.hunterRanger.position(), CaptureMethod.TRACKING);
    }

    /** Выбирает лучшую засаду на видимого M-хищника. */
    public Optional<HuntPlan> bestHuntPlan(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.HUNT)
                .map(dinosaur -> bestHuntAmbushPointFor(player, dinosaur)
                        .map(point -> new HuntPlan(dinosaur, point)))
                .flatMap(Optional::stream)
                .min(Comparator
                        .comparingInt((HuntPlan plan) -> simulation.dinosaurAi.estimateDinosaurTurnsTo(
                                plan.dinosaur(),
                                plan.baitPosition()
                        ))
                        .thenComparingInt(plan -> hunterPathDistance(
                                player.hunterRanger.position(),
                                plan.baitPosition()
                        )));
    }

    /** Ищет ближайшего нужного игроку динозавра с одним из указанных способов поимки. */
    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        List<CaptureMethod> allowedMethods = List.of(methods);
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> normalizedPathDistance(from, d.position)));
    }

    /** Проверяет, относится ли способ поимки к задачам охотника. */
    private boolean isHunterCaptureMethod(CaptureMethod captureMethod) {
        return captureMethod == CaptureMethod.TRACKING
                || captureMethod == CaptureMethod.HUNT;
    }

    /** Возвращает расстояние по реальному пути охотника или большое число для сортировки. */
    private int normalizedPathDistance(Point from, Point target) {
        int distance = hunterPathDistance(from, target);
        return distance == UNREACHABLE_DISTANCE ? Integer.MAX_VALUE - 1 : distance;
    }

    /** Считает кратчайшее расстояние по уже открытой карте для обычного движения охотника. */
    private int hunterPathDistance(Point from, Point target) {
        return simulation.map.groundRangerPathDistance(from, target);
    }

    /** Форматирует оценку прихода хищника для лога AI. */
    private String turnsText(int turns) {
        if (turns == Integer.MAX_VALUE) {
            return "неизвестно";
        }
        return turns + " ход.";
    }

    /** План засады на M-хищника. */
    private record HuntPlan(Dinosaur dinosaur, Point baitPosition) {
    }

    /** Проверяет, может ли указанная клетка быть клеткой охотничьей засады. */
    private boolean isLegalHuntAmbushPoint(PlayerState player, Point point) {
        if (point == null || !simulation.map.canPlaceBait(point)) return false;
        if (player.rejectedHuntBaitPositions.contains(point)) return false;
        if (!simulation.map.canGroundRangerStandOn(point)) return false;
        if (simulation.map.groundRangerPathDistance(player.hunterRanger.position(), point) == Integer.MAX_VALUE) return false;

        return simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .noneMatch(dinosaur -> dinosaur.position.equals(point));
    }

    /**
     * Выбирает лучшую клетку для охотничьей засады на конкретного M-хищника.
     *
     * @param player игрок, чей охотник планирует засаду
     * @param dinosaur целевой M-хищник
     * @return лучшая клетка для приманки или Optional.empty(), если охота невозможна
     */
    public Optional<Point> bestHuntAmbushPointFor(PlayerState player, Dinosaur dinosaur) {
        if (player == null || dinosaur == null || player.hunterBait <= 0) {
            return Optional.empty();
        }

        return huntAmbushCandidatesFor(dinosaur).stream()
                .filter(point -> isLegalHuntAmbushPoint(player, point))
                .min(Comparator
                        .comparingInt((Point point) -> huntAmbushTimingPenalty(dinosaur, point))
                        .thenComparingInt(point -> simulation.map.groundRangerPathDistance(
                                player.hunterRanger.position(),
                                point
                        ))
                        .thenComparingInt(point -> point.manhattan(dinosaur.position)));
    }

    /**
     * Возвращает клетки, где охотник может ждать M-хищника с приманкой.
     *
     * @param dinosaur хищник, под маршрут которого ищутся клетки
     * @return список возможных клеток засады
     */
    public List<Point> huntAmbushCandidatesFor(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.species.captureMethod != CaptureMethod.HUNT) {
            return List.of();
        }
        return simulation.dinosaurAi.predictDinosaurBioTrailRoute(dinosaur, 5).stream()
                .filter(point -> !point.equals(dinosaur.position))
                .filter(simulation.map::canPlaceBait)
                .toList();
    }

    /** Считает штраф тайминга для клетки засады. */
    private int huntAmbushTimingPenalty(Dinosaur dinosaur, Point point) {
        int turns = predictedDinosaurTurnTo(dinosaur, point, 5);
        if (turns == Integer.MAX_VALUE) {
            turns = simulation.dinosaurAi.estimateDinosaurTurnsTo(dinosaur, point);
        }
        if (turns == Integer.MAX_VALUE) return Integer.MAX_VALUE;

        int desiredTurns = 2;
        int earlyPenalty = turns < 1 ? 1000 : 0;
        return earlyPenalty + Math.abs(turns - desiredTurns) * 10;
    }

    /**
     * Возвращает номер прогнозируемого хода, на котором динозавр окажется в клетке.
     *
     * @param dinosaur динозавр
     * @param point клетка проверки
     * @param maxTurns глубина прогноза
     * @return номер фазы динозавров, начиная с 1, или Integer.MAX_VALUE
     */
    private int predictedDinosaurTurnTo(Dinosaur dinosaur, Point point, int maxTurns) {
        List<Point> route = simulation.dinosaurAi.predictDinosaurBioTrailRoute(dinosaur, maxTurns);
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).equals(point)) {
                return i + 1;
            }
        }
        return Integer.MAX_VALUE;
    }


}
