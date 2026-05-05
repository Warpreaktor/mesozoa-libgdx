package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI-оценка полезности охотника для очередной активации игрока.
 *
 * Охотник выбирает между двумя взрослыми занятиями: засадой на M-хищника и
 * выслеживанием M-травоядного по цепочке следов. Уже начатую охоту он не бросает
 * до успеха или провала, чтобы не возникало бесконечного переключения целей.
 */
public final class HunterAi {

    /** Вес невозможного или полностью бесполезного действия. */
    private static final double SCORE_IMPOSSIBLE = -100.0;

    /** Вес обязательного продолжения уже начатой охоты на хищника. */
    private static final double SCORE_ACTIVE_HUNT = 125.0;

    /** Вес обязательного продолжения уже начатого выслеживания. */
    private static final double SCORE_ACTIVE_TRACKING = 122.0;

    /** Максимальный вес ситуации, когда охотник уже может начать выслеживание. */
    private static final double SCORE_TRACKING_READY = 106.0;

    /** Вес ситуации, когда охотник может дойти до цели выслеживания за текущую активацию. */
    private static final double SCORE_TRACKING_REACHABLE_NOW = 96.0;

    /** Вес ситуации, когда охотник уже стоит на клетке будущей засады. */
    private static final double SCORE_HUNT_AMBUSH_READY = 105.0;

    /** Вес ситуации, когда охотник может дойти до клетки засады за текущую активацию. */
    private static final double SCORE_HUNT_AMBUSH_REACHABLE_NOW = 92.0;

    /** Вес ожидания рядом с разведчиком, когда видимых целей охотника пока нет. */
    private static final double SCORE_WAIT_NEAR_SCOUT = -5.0;

    /** Вес ситуации, когда охотник не может осмысленно действовать прямо сейчас. */
    private static final double SCORE_LOW_IDLE = -10.0;

    /** Штраф за каждую проваленную засаду на того же конкретного хищника. */
    private static final double FAILED_HUNT_ATTEMPT_PENALTY = 15.0;

    /** Штраф за каждую сорванную цепочку выслеживания на того же конкретного травоядного. */
    private static final double FAILED_TRACKING_CHAIN_PENALTY = 12.0;

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

        if (player.activeHunt != null) {
            return new AiScore(
                    SCORE_ACTIVE_HUNT,
                    "охотник обязан продолжать засаду на "
                            + player.activeHunt.species.displayName
                            + ", подготовка " + player.activeHunt.preparationScore()
            );
        }

        if (player.activeTracking != null) {
            return new AiScore(
                    SCORE_ACTIVE_TRACKING,
                    "охотник обязан идти по следу "
                            + player.activeTracking.species.displayName
                            + "; попытка " + player.activeTracking.attempts()
                            + ", карты " + player.activeTracking.preparationScore() + " / 10"
            );
        }

        if (remainingHunterSpecies.isEmpty()) {
            return new AiScore(
                    SCORE_IMPOSSIBLE,
                    "в задании не осталось целей для охотника"
            );
        }

        Optional<Dinosaur> trackingTarget = bestTrackingTarget(player);
        Optional<HuntPlan> huntPlan = bestHuntPlan(player);

        Optional<AiScore> bestCaptureScore = bestCaptureScore(player, trackingTarget, huntPlan);
        if (bestCaptureScore.isPresent()) {
            return bestCaptureScore.get();
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

    /**
     * Выбирает точку, куда должен двигаться охотник по лучшему текущему плану.
     *
     * @param player игрок, чей охотник планируется
     * @return целевая клетка или пустой результат, если охотнику лучше ждать
     */
    public Optional<Point> chooseHunterTarget(PlayerState player) {
        if (player.activeHunt != null) {
            return Optional.of(player.activeHunt.baitPosition);
        }

        if (player.activeTracking != null) {
            return dinosaurById(player.activeTracking.dinosaurId)
                    .map(dinosaur -> dinosaur.position)
                    .or(() -> Optional.of(player.hunterRanger.position()));
        }

        Optional<Dinosaur> trackingTarget = bestTrackingTarget(player);
        Optional<HuntPlan> huntPlan = bestHuntPlan(player);

        Optional<WeightedTarget> bestCaptureTarget = bestCaptureTarget(player, trackingTarget, huntPlan);
        if (bestCaptureTarget.isPresent()) {
            return Optional.of(bestCaptureTarget.get().target());
        }

        if (hasVisibleHuntTargetWithoutBait(player)) {
            return Optional.of(simulation.map.base);
        }

        if (shouldCatchUpToScout(player, remainingNeededHunterSpecies(player), trackingTarget, huntPlan)) {
            return Optional.of(player.scoutRanger.position());
        }

        return Optional.empty();
    }

    private Optional<AiScore> bestCaptureScore(
            PlayerState player,
            Optional<Dinosaur> trackingTarget,
            Optional<HuntPlan> huntPlan
    ) {
        ArrayList<AiScore> scores = new ArrayList<>();
        trackingTarget.map(dinosaur -> scoreTrackingTarget(player, dinosaur)).ifPresent(scores::add);
        huntPlan.map(plan -> scoreHuntAmbushPlan(player, plan)).ifPresent(scores::add);

        return scores.stream().max(Comparator.comparingDouble(AiScore::value));
    }

    private Optional<WeightedTarget> bestCaptureTarget(
            PlayerState player,
            Optional<Dinosaur> trackingTarget,
            Optional<HuntPlan> huntPlan
    ) {
        ArrayList<WeightedTarget> targets = new ArrayList<>();
        trackingTarget.ifPresent(dinosaur -> targets.add(new WeightedTarget(
                dinosaur.position,
                scoreTrackingTarget(player, dinosaur).value()
        )));
        huntPlan.ifPresent(plan -> targets.add(new WeightedTarget(
                plan.baitPosition(),
                scoreHuntAmbushPlan(player, plan).value()
        )));

        return targets.stream().max(Comparator.comparingDouble(WeightedTarget::score));
    }

    /** Оценивает план засады на M-хищника. */
    private AiScore scoreHuntAmbushPlan(PlayerState player, HuntPlan plan) {
        Point hunterPosition = player.hunterRanger.position();
        int distance = hunterPathDistance(hunterPosition, plan.baitPosition());
        int turns = simulation.dinosaurAi.estimateDinosaurTurnsTo(plan.dinosaur(), plan.baitPosition());
        double failurePenalty = player.failedHuntAttempts(plan.dinosaur().id) * FAILED_HUNT_ATTEMPT_PENALTY;

        if (hunterPosition.equals(plan.baitPosition())) {
            return new AiScore(
                    SCORE_HUNT_AMBUSH_READY - failurePenalty,
                    "охотник на клетке засады для " + plan.dinosaur().displayName
                            + ", ожидаемый приход через " + turnsText(turns)
                            + failurePenaltyText(failurePenalty)
            );
        }

        if (distance != UNREACHABLE_DISTANCE && distance <= HUNTER_ACTION_POINTS) {
            return new AiScore(
                    SCORE_HUNT_AMBUSH_REACHABLE_NOW - failurePenalty,
                    "охотник может занять клетку засады для " + plan.dinosaur().displayName
                            + " за текущую активацию; расстояние: " + distance
                            + ", ожидаемый приход через " + turnsText(turns)
                            + failurePenaltyText(failurePenalty)
            );
        }

        if (distance == UNREACHABLE_DISTANCE) {
            return new AiScore(
                    25.0 - failurePenalty,
                    "клетка засады видна, но путь охотника не найден: " + plan.dinosaur().displayName
                            + failurePenaltyText(failurePenalty)
            );
        }

        double score = 75.0 - Math.min(35.0, distance * 4.0) - failurePenalty;
        return new AiScore(
                score,
                "охотник идёт к клетке засады для " + plan.dinosaur().displayName
                        + "; расстояние: " + distance
                        + ", ожидаемый приход через " + turnsText(turns)
                        + failurePenaltyText(failurePenalty)
        );
    }

    /** Рассчитывает вес видимой цели выслеживания. */
    private AiScore scoreTrackingTarget(PlayerState player, Dinosaur dinosaur) {
        Point hunterPosition = player.hunterRanger.position();
        int distance = trackingPathDistance(hunterPosition, dinosaur.position);
        double agilityBonus = Math.max(0, 5 - dinosaur.agility) * 2.5;
        double failurePenalty = player.failedTrackingChains(dinosaur.id) * FAILED_TRACKING_CHAIN_PENALTY;

        if (hunterPosition.equals(dinosaur.position)) {
            return new AiScore(
                    SCORE_TRACKING_READY + agilityBonus - failurePenalty,
                    "охотник стоит на клетке цели выслеживания: " + dinosaur.displayName
                            + "; ловкость " + dinosaur.agility
                            + failurePenaltyText(failurePenalty)
            );
        }

        if (distance != UNREACHABLE_DISTANCE && distance <= HUNTER_ACTION_POINTS) {
            return new AiScore(
                    SCORE_TRACKING_REACHABLE_NOW + agilityBonus - failurePenalty,
                    "охотник может дойти до цели выслеживания за текущую активацию: "
                            + dinosaur.displayName
                            + ", расстояние по следовому пути: " + distance
                            + failurePenaltyText(failurePenalty)
            );
        }

        if (distance == UNREACHABLE_DISTANCE) {
            return new AiScore(
                    25.0 - failurePenalty,
                    "цель выслеживания видна, но даже следовой путь до неё не найден: "
                            + dinosaur.displayName
                            + failurePenaltyText(failurePenalty)
            );
        }

        double score = 72.0 + agilityBonus - Math.min(36.0, distance * 4.0) - failurePenalty;
        return new AiScore(
                score,
                "видимая цель выслеживания: "
                        + dinosaur.displayName
                        + ", расстояние по следовому пути: " + distance
                        + ", ловкость " + dinosaur.agility
                        + failurePenaltyText(failurePenalty)
        );
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
            if (!player.captured.contains(species) && isHunterCaptureMethod(Dinosaur.captureMethodOf(species))) {
                result.add(species);
            }
        }

        return result;
    }

    /** Ищет лучшую живую и непойманную цель выслеживания, нужную игроку. */
    public Optional<Dinosaur> bestTrackingTarget(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.captureMethod == CaptureMethod.TRACKING)
                .max(Comparator.comparingDouble(dinosaur -> scoreTrackingTarget(player, dinosaur).value()));
    }

    /** Выбирает лучшую засаду на видимого M-хищника. */
    public Optional<HuntPlan> bestHuntPlan(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.captureMethod == CaptureMethod.HUNT)
                .map(dinosaur -> bestHuntAmbushPointFor(player, dinosaur)
                        .map(point -> new HuntPlan(dinosaur, point)))
                .flatMap(Optional::stream)
                .min(Comparator
                        .comparingInt((HuntPlan plan) -> player.failedHuntAttempts(plan.dinosaur().id))
                        .thenComparingInt(plan -> simulation.dinosaurAi.estimateDinosaurTurnsTo(
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
                .filter(d -> allowedMethods.contains(d.captureMethod))
                .min(Comparator.comparingInt(d -> normalizedPathDistance(from, d.position, d.captureMethod)));
    }

    /** Проверяет, относится ли способ поимки к задачам охотника. */
    private boolean isHunterCaptureMethod(CaptureMethod captureMethod) {
        return captureMethod == CaptureMethod.TRACKING
                || captureMethod == CaptureMethod.HUNT;
    }

    /** Возвращает расстояние по подходящему пути или большое число для сортировки. */
    private int normalizedPathDistance(Point from, Point target, CaptureMethod method) {
        int distance = method == CaptureMethod.TRACKING
                ? trackingPathDistance(from, target)
                : hunterPathDistance(from, target);
        return distance == UNREACHABLE_DISTANCE ? Integer.MAX_VALUE - 1 : distance;
    }

    /** Считает кратчайшее расстояние по обычной открытой карте для охоты с приманкой. */
    private int hunterPathDistance(Point from, Point target) {
        return simulation.map.groundRangerPathDistance(from, target);
    }

    /** Считает кратчайшее расстояние по следовому режиму охотника. */
    private int trackingPathDistance(Point from, Point target) {
        return simulation.map.hunterTrackingPathDistance(from, target);
    }

    /** Форматирует оценку прихода хищника для лога AI. */
    private String turnsText(int turns) {
        if (turns == Integer.MAX_VALUE) {
            return "неизвестно";
        }
        return turns + " ход.";
    }

    private String failurePenaltyText(double failurePenalty) {
        return failurePenalty <= 0 ? "" : "; штраф за прошлые провалы " + (int) failurePenalty;
    }

    private Optional<Dinosaur> dinosaurById(int dinosaurId) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> dinosaur.id == dinosaurId)
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.trapped && !dinosaur.removed)
                .findFirst();
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
                        .thenComparingInt(point -> point.chebyshev(dinosaur.position)));
    }

    /**
     * Возвращает клетки, где охотник может ждать M-хищника с приманкой.
     *
     * @param dinosaur хищник, под маршрут которого ищутся клетки
     * @return список возможных клеток засады
     */
    public List<Point> huntAmbushCandidatesFor(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.captureMethod != CaptureMethod.HUNT) {
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

    /** План засады на M-хищника. */
    public record HuntPlan(Dinosaur dinosaur, Point baitPosition) {
    }

    private record WeightedTarget(Point target, double score) {
    }
}
