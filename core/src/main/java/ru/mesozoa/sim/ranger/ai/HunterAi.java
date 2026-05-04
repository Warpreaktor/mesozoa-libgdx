package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

/**
 * AI-оценка полезности охотника для очередной активации игрока.
 *
 * Охотник занимается только теми динозаврами, которые ловятся через охоту
 * или выслеживание. Ловушечные цели относятся к инженеру и здесь не учитываются.
 */
public final class HunterAi {

    /** Вес невозможного или полностью бесполезного действия. */
    private static final double SCORE_IMPOSSIBLE = -100.0;

    /** Максимальный вес ситуации, когда охотник уже может начать выслеживание. */
    private static final double SCORE_TRACKING_READY = 100.0;

    /** Вес ситуации, когда охотник может дойти до цели выслеживания за текущую активацию. */
    private static final double SCORE_TRACKING_REACHABLE_NOW = 90.0;

    /** Вес ситуации, когда охотник уже может начинать охоту с приманкой. */
    private static final double SCORE_HUNT_READY = 95.0;

    /** Вес ситуации, когда охотник может выйти в радиус охоты за текущую активацию. */
    private static final double SCORE_HUNT_REACHABLE_NOW = 85.0;

    /** Низкий вес ожидания рядом с разведчиком, когда видимых целей охотника пока нет. */
    private static final double SCORE_WAIT_NEAR_SCOUT = 10.0;

    /** Вес ситуации, когда охотник не может осмысленно действовать прямо сейчас. */
    private static final double SCORE_LOW_IDLE = 5.0;

    /** Количество очков движения охотника за одну активацию в текущей модели симуляции. */
    private static final int HUNTER_ACTION_POINTS = 2;

    /** Радиус, в котором текущая упрощённая симуляция позволяет начать охоту на HUNT-цель. */
    private static final int HUNT_START_RANGE = 2;

    /** Радиус, в котором охотник считается достаточно близким к разведчику. */
    private static final int NEAR_SCOUT_DISTANCE = 1;

    /** Значение расстояния, если путь до цели не найден. */
    private static final int UNREACHABLE_DISTANCE = Integer.MAX_VALUE;

    /**
     * Текущая симуляция, из которой AI читает состояние карты, динозавров,
     * игроков и конфигурации механик.
     */
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
     * Метод идёт от самых срочных ситуаций к менее срочным:
     * готовое выслеживание, достижимая цель выслеживания, готовая охота,
     * видимые цели, а затем позиционирование рядом с разведчиком.
     *
     * @param player игрок, для которого оценивается полезность охотника
     * @return оценка полезности охотника и причина этой оценки
     */
    public AiScore scoreHunter(PlayerState player) {
        Set<Species> remainingHunterSpecies = remainingNeededHunterSpecies(player);
        Optional<Dinosaur> trackingTarget = nearestTrackingTarget(player);
        Optional<Dinosaur> huntTarget = nearestHuntTarget(player);

        if (hasNoRemainingHunterTargets(remainingHunterSpecies)) {
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

        if (canStartHuntNow(player, huntTarget)) {
            Dinosaur dinosaur = huntTarget.get();
            int distance = hunterPathDistance(player.hunterRanger.position(), dinosaur.position);
            return new AiScore(
                    SCORE_HUNT_READY,
                    "хищник в радиусе охоты и есть приманка: "
                            + dinosaur.species.displayName
                            + ", расстояние по пути: " + distance
            );
        }

        if (canReachHuntRangeThisActivation(player, huntTarget)) {
            Dinosaur dinosaur = huntTarget.get();
            int distance = hunterPathDistance(player.hunterRanger.position(), dinosaur.position);
            return new AiScore(
                    SCORE_HUNT_REACHABLE_NOW,
                    "охотник может выйти в радиус охоты за текущую активацию: "
                            + dinosaur.species.displayName
                            + ", расстояние по пути: " + distance
            );
        }

        if (hasVisibleTrackingTarget(trackingTarget)) {
            return scoreVisibleTrackingTarget(player, trackingTarget.get());
        }

        if (hasVisibleHuntTargetWithoutBait(player, huntTarget)) {
            Dinosaur dinosaur = huntTarget.get();
            return new AiScore(
                    35.0,
                    "есть цель для охоты, но у охотника нет приманки: " + dinosaur.species.displayName
            );
        }

        if (hasVisibleHuntTarget(huntTarget)) {
            return scoreVisibleHuntTarget(player, huntTarget.get());
        }

        if (shouldCatchUpToScout(player, remainingHunterSpecies, trackingTarget, huntTarget)) {
            return scoreCatchUpToScout(player);
        }

        if (isHunterAlreadyNearScout(player, remainingHunterSpecies, trackingTarget, huntTarget)) {
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
     * Проверяет, остались ли в задании игрока виды, которые ловятся охотником.
     *
     * Если таких видов нет, охотник не приближает игрока к выполнению задания.
     * S-динозавры с CaptureMethod.TRAP намеренно игнорируются: это работа инженера.
     *
     * @param remainingHunterSpecies оставшиеся непойманные виды, доступные охотнику
     * @return true, если охотнику больше некого ловить по заданию
     */
    private boolean hasNoRemainingHunterTargets(Set<Species> remainingHunterSpecies) {
        return remainingHunterSpecies.isEmpty();
    }

    /**
     * Проверяет, стоит ли охотник на клетке с нужной целью для выслеживания.
     *
     * По правилам выслеживание M-травоядных начинается, когда охотник встаёт
     * на клетку с таким динозавром. Поэтому эта ситуация получает максимальный вес.
     *
     * @param player игрок, чей охотник оценивается
     * @param trackingTarget ближайшая цель выслеживания, если она есть
     * @return true, если охотник уже находится на клетке цели выслеживания
     */
    private boolean isHunterOnTrackingTarget(PlayerState player, Optional<Dinosaur> trackingTarget) {
        return trackingTarget.isPresent()
                && player.hunterRanger.position().equals(trackingTarget.get().position);
    }

    /**
     * Проверяет, может ли охотник дойти до цели выслеживания за текущую активацию.
     *
     * Если путь до M-травоядного укладывается в очки движения охотника, роль получает
     * высокий вес: следующим действием охотник сможет начать или приблизить фазу выслеживания.
     *
     * @param player игрок, чей охотник оценивается
     * @param trackingTarget ближайшая цель выслеживания, если она есть
     * @return true, если цель достижима за одну активацию охотника
     */
    private boolean canReachTrackingTargetThisActivation(PlayerState player, Optional<Dinosaur> trackingTarget) {
        if (trackingTarget.isEmpty()) return false;
        int distance = hunterPathDistance(player.hunterRanger.position(), trackingTarget.get().position);
        return distance > 0 && distance <= HUNTER_ACTION_POINTS;
    }

    /**
     * Проверяет, может ли охотник прямо сейчас начать охоту на хищника.
     *
     * В текущей упрощённой симуляции охота начинается, если HUNT-цель находится
     * в радиусе двух клеток и у охотника есть приманка.
     *
     * @param player игрок, чей охотник оценивается
     * @param huntTarget ближайшая цель охоты, если она есть
     * @return true, если охотник уже находится в радиусе охоты и имеет приманку
     */
    private boolean canStartHuntNow(PlayerState player, Optional<Dinosaur> huntTarget) {
        if (huntTarget.isEmpty() || player.hunterBait <= 0) return false;
        int distance = hunterPathDistance(player.hunterRanger.position(), huntTarget.get().position);
        return distance <= HUNT_START_RANGE;
    }

    /**
     * Проверяет, может ли охотник за текущую активацию выйти в радиус охоты.
     *
     * Если хищник ещё не в радиусе, но охотник может приблизиться так, чтобы после
     * движения оказаться в радиусе применения приманки, охотник получает высокий вес.
     *
     * @param player игрок, чей охотник оценивается
     * @param huntTarget ближайшая цель охоты, если она есть
     * @return true, если охотник может дойти до радиуса охоты за одну активацию
     */
    private boolean canReachHuntRangeThisActivation(PlayerState player, Optional<Dinosaur> huntTarget) {
        if (huntTarget.isEmpty() || player.hunterBait <= 0) return false;
        int distance = hunterPathDistance(player.hunterRanger.position(), huntTarget.get().position);
        return distance > HUNT_START_RANGE
                && distance <= HUNT_START_RANGE + HUNTER_ACTION_POINTS;
    }

    /**
     * Проверяет, есть ли на карте видимая цель для выслеживания.
     *
     * @param trackingTarget ближайшая цель выслеживания, если она есть
     * @return true, если цель выслеживания уже обнаружена на карте
     */
    private boolean hasVisibleTrackingTarget(Optional<Dinosaur> trackingTarget) {
        return trackingTarget.isPresent();
    }

    /**
     * Проверяет, есть ли видимая цель охоты, но нет приманки.
     *
     * В такой ситуации охотник всё ещё связан с задачей, но его полезность ниже:
     * без приманки он не может нормально начать охоту на хищника.
     *
     * @param player игрок, чей охотник оценивается
     * @param huntTarget ближайшая цель охоты, если она есть
     * @return true, если цель охоты есть, а приманки нет
     */
    private boolean hasVisibleHuntTargetWithoutBait(PlayerState player, Optional<Dinosaur> huntTarget) {
        return huntTarget.isPresent() && player.hunterBait <= 0;
    }

    /**
     * Проверяет, есть ли на карте видимая цель для охоты.
     *
     * @param huntTarget ближайшая цель охоты, если она есть
     * @return true, если цель охоты уже обнаружена на карте
     */
    private boolean hasVisibleHuntTarget(Optional<Dinosaur> huntTarget) {
        return huntTarget.isPresent();
    }

    /**
     * Проверяет, стоит ли охотнику подтянуться к разведчику.
     *
     * Если видимых целей охотника пока нет, но в задании есть ненайденные цели,
     * которые должен ловить охотник, он не должен сидеть на базе. Новые динозавры
     * появляются рядом с разведкой, значит охотнику выгодно держаться ближе к разведчику.
     *
     * @param player игрок, чей охотник оценивается
     * @param remainingHunterSpecies оставшиеся виды задания, которые ловит охотник
     * @param trackingTarget ближайшая цель выслеживания, если она есть
     * @param huntTarget ближайшая цель охоты, если она есть
     * @return true, если охотнику полезно двигаться к разведчику
     */
    private boolean shouldCatchUpToScout(
            PlayerState player,
            Set<Species> remainingHunterSpecies,
            Optional<Dinosaur> trackingTarget,
            Optional<Dinosaur> huntTarget
    ) {
        if (remainingHunterSpecies.isEmpty()) return false;
        if (trackingTarget.isPresent() || huntTarget.isPresent()) return false;

        int distance = hunterPathDistance(player.hunterRanger.position(), player.scoutRanger.position());
        return distance != UNREACHABLE_DISTANCE && distance > NEAR_SCOUT_DISTANCE;
    }

    /**
     * Проверяет, что охотник уже находится рядом с разведчиком.
     *
     * Это низкоприоритетная ситуация ожидания: целей охотника пока нет, но охотник
     * уже занял нормальную позицию рядом с местом будущих открытий.
     *
     * @param player игрок, чей охотник оценивается
     * @param remainingHunterSpecies оставшиеся виды задания, которые ловит охотник
     * @param trackingTarget ближайшая цель выслеживания, если она есть
     * @param huntTarget ближайшая цель охоты, если она есть
     * @return true, если охотник уже достаточно близко к разведчику
     */
    private boolean isHunterAlreadyNearScout(
            PlayerState player,
            Set<Species> remainingHunterSpecies,
            Optional<Dinosaur> trackingTarget,
            Optional<Dinosaur> huntTarget
    ) {
        if (remainingHunterSpecies.isEmpty()) return false;
        if (trackingTarget.isPresent() || huntTarget.isPresent()) return false;

        int distance = hunterPathDistance(player.hunterRanger.position(), player.scoutRanger.position());
        return distance <= NEAR_SCOUT_DISTANCE;
    }

    /**
     * Рассчитывает вес видимой цели выслеживания, если до неё нельзя дойти прямо сейчас.
     *
     * Чем ближе M-травоядное по реальному пути, тем выше вес. Если путь не найден,
     * охотник получает низкую оценку: цель видна, но он не понимает, как к ней пройти.
     *
     * @param player игрок, чей охотник оценивается
     * @param dinosaur видимая цель выслеживания
     * @return оценка полезности движения к цели выслеживания
     */
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

    /**
     * Рассчитывает вес видимой цели охоты, если охота не может начаться прямо сейчас.
     *
     * Чем ближе хищник по реальному пути и чем больше шансов выйти в радиус приманки,
     * тем выше вес охотника.
     *
     * @param player игрок, чей охотник оценивается
     * @param dinosaur видимая цель охоты
     * @return оценка полезности движения к цели охоты
     */
    private AiScore scoreVisibleHuntTarget(PlayerState player, Dinosaur dinosaur) {
        int distance = hunterPathDistance(player.hunterRanger.position(), dinosaur.position);

        if (distance == UNREACHABLE_DISTANCE) {
            return new AiScore(
                    25.0,
                    "цель охоты видна, но путь до неё не найден: " + dinosaur.species.displayName
            );
        }

        double score = 70.0 - Math.min(35.0, distance * 4.0);
        return new AiScore(
                score,
                "видимая цель охоты: "
                        + dinosaur.species.displayName
                        + ", расстояние по пути: " + distance
                        + ", приманки: " + player.hunterBait
        );
    }

    /**
     * Рассчитывает вес движения охотника к разведчику.
     *
     * Чем дальше охотник от разведчика, тем выше вес: иначе охотник будет стоять
     * на базе, пока разведчик открывает карту и находит цели за пол-острова.
     *
     * @param player игрок, чей охотник оценивается
     * @return оценка полезности сближения с разведчиком
     */
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

    /**
     * Возвращает непойманные виды из задания игрока, которые должен ловить охотник.
     *
     * Учитываются только CaptureMethod.TRACKING и CaptureMethod.HUNT.
     * TRAP-цели остаются инженеру и не должны поднимать вес охотника.
     *
     * @param player игрок, чьё задание анализируется
     * @return виды задания, которые ещё не пойманы и относятся к работе охотника
     */
    private Set<Species> remainingNeededHunterSpecies(PlayerState player) {
        EnumSet<Species> result = EnumSet.noneOf(Species.class);

        for (Species species : player.task) {
            if (!player.captured.contains(species) && isHunterCaptureMethod(species.captureMethod)) {
                result.add(species);
            }
        }

        return result;
    }

    /**
     * Ищет ближайшую живую и непойманную цель выслеживания, нужную игроку.
     *
     * @param player игрок, для которого ищется цель
     * @return ближайшая TRACKING-цель или Optional.empty(), если такой цели нет
     */
    private Optional<Dinosaur> nearestTrackingTarget(PlayerState player) {
        return nearestNeededDinosaur(player, player.hunterRanger.position(), CaptureMethod.TRACKING);
    }

    /**
     * Ищет ближайшую живую и непойманную цель охоты, нужную игроку.
     *
     * @param player игрок, для которого ищется цель
     * @return ближайшая HUNT-цель или Optional.empty(), если такой цели нет
     */
    private Optional<Dinosaur> nearestHuntTarget(PlayerState player) {
        return nearestNeededDinosaur(player, player.hunterRanger.position(), CaptureMethod.HUNT);
    }

    /**
     * Ищет ближайшего нужного игроку динозавра с одним из указанных способов поимки.
     *
     * Расстояние считается по реальному пути охотника. Если путь не найден,
     * цель получает самый низкий приоритет среди целей такого же типа.
     *
     * @param player игрок, для которого ищется цель
     * @param from позиция охотника
     * @param methods допустимые способы поимки
     * @return ближайшая подходящая цель или Optional.empty(), если цели нет
     */
    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        EnumSet<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        for (CaptureMethod method : methods) {
            allowedMethods.add(method);
        }

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> normalizedPathDistance(from, d.position)));
    }

    /**
     * Проверяет, относится ли способ поимки к задачам охотника.
     *
     * @param captureMethod способ поимки вида
     * @return true, если вид ловится охотником
     */
    private boolean isHunterCaptureMethod(CaptureMethod captureMethod) {
        return captureMethod == CaptureMethod.TRACKING
                || captureMethod == CaptureMethod.HUNT;
    }

    /**
     * Возвращает расстояние по реальному пути охотника или большое число для сортировки.
     *
     * @param from стартовая клетка
     * @param target целевая клетка
     * @return длина пути или большое число, если цель недостижима
     */
    private int normalizedPathDistance(Point from, Point target) {
        int distance = hunterPathDistance(from, target);
        return distance == UNREACHABLE_DISTANCE ? Integer.MAX_VALUE - 1 : distance;
    }

    /**
     * Считает кратчайшее расстояние по уже открытой карте для обычного движения охотника.
     *
     * Используется BFS, потому что стоимость шага между соседними тайлами сейчас одинаковая.
     * Охотник ходит только по выложенным и проходимым тайлам.
     *
     * @param from стартовая клетка
     * @param target целевая клетка
     * @return количество шагов или UNREACHABLE_DISTANCE, если путь не найден
     */
    private int hunterPathDistance(Point from, Point target) {
        if (from == null || target == null) return UNREACHABLE_DISTANCE;
        if (!simulation.map.isPlaced(from) || !simulation.map.isPlaced(target)) return UNREACHABLE_DISTANCE;
        if (from.equals(target)) return 0;
        if (!canHunterEnter(target)) return UNREACHABLE_DISTANCE;

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Integer> distanceByPoint = new HashMap<>();

        queue.add(from);
        distanceByPoint.put(from, 0);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int currentDistance = distanceByPoint.get(current);

            for (Point neighbor : simulation.map.placedNeighbors(current)) {
                if (distanceByPoint.containsKey(neighbor)) continue;
                if (!canHunterEnter(neighbor)) continue;

                int nextDistance = currentDistance + 1;
                if (neighbor.equals(target)) {
                    return nextDistance;
                }

                distanceByPoint.put(neighbor, nextDistance);
                queue.addLast(neighbor);
            }
        }

        return UNREACHABLE_DISTANCE;
    }

    /**
     * Проверяет, может ли охотник войти на указанный тайл при обычном движении.
     *
     * Охотник использует общие правила наземных рейнджеров: он может стоять только
     * на тех клетках, у которых конкретный Tile сейчас проходим. Поэтому река или
     * озеро могут стать доступными после строительства моста, но сам биом при этом
     * не меняется. Специальное движение по следу можно будет расширить отдельно,
     * когда в модели появится состояние активной цепочки выслеживания.
     *
     * @param point клетка, которую нужно проверить
     * @return true, если охотник может войти на клетку
     */
    private boolean canHunterEnter(Point point) {
        return simulation.map.canGroundRangerStandOn(point);
    }
}
