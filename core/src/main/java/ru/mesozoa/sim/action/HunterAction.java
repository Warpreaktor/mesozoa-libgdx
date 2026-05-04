package ru.mesozoa.sim.action;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.HuntAmbush;
import ru.mesozoa.sim.model.HuntCard;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Исполнение действий охотника.
 *
 * Охотник разделён на два режима: старое выслеживание M-травоядных и новая
 * засада на M-хищников. Хищник больше не ловится мгновенной проверкой рядом с
 * фигуркой: охотник выбирает клетку био-тропы, кладёт приманку, берёт стартовые
 * карты и дальше каждый свой ход либо добирает подготовку, либо ждёт.
 */
public class HunterAction {

    /** Нижняя сумма, до которой AI почти всегда добирает карты охоты. */
    private static final int SAFE_PREPARATION_TARGET = 8;

    /** Смелая сумма, к которой AI стремится, если до прихода хищника ещё есть время. */
    private static final int STRONG_PREPARATION_TARGET = 9;

    /** Максимум активаций в одной засаде без контакта с хищником. */
    private static final int MAX_AMBUSH_TURNS_WITHOUT_CONTACT = 5;

    /** Максимум приманки, который охотник может хранить. */
    private static final int MAX_HUNTER_BAIT = 3;

    private final GameSimulation simulation;
    private final RangerActionExecutor rangerActionExecutor;

    /**
     * Создаёт исполнитель действий охотника.
     *
     * @param simulation текущая симуляция
     * @param rangerActionExecutor общий исполнитель перемещений рейнджеров
     */
    public HunterAction(GameSimulation simulation,
                        RangerActionExecutor rangerActionExecutor) {

        this.simulation = simulation;
        this.rangerActionExecutor = rangerActionExecutor;
    }

    /**
     * Выполняет выбранный AI-план охотника.
     *
     * @param player игрок, чей охотник активируется
     * @param plan выбранный план рейнджера
     */
    public void action(PlayerState player, RangerPlan plan) {
        int movementPoints = plan.ranger().currentActionPoints();
        action(player, plan.target(), movementPoints);
        plan.ranger().spendActionPoints(movementPoints);
    }

    /**
     * Выполняет действие охотника без заранее выбранной цели.
     *
     * @param player игрок, чей охотник активируется
     * @param movementPoints очки движения/действия охотника
     */
    public void action(PlayerState player, int movementPoints) {
        action(player, null, movementPoints);
    }

    /**
     * Центральная логика охотника: сначала обязательная активная засада, затем
     * точечное выслеживание, затем подготовка новой охоты на хищника.
     */
    private void action(PlayerState player, Point plannedTarget, int movementPoints) {
        boolean hadActiveHunt = player.activeHunt != null;
        if (continueActiveHunt(player)) {
            return;
        }
        if (hadActiveHunt && player.activeHunt == null) {
            plannedTarget = null;
        }

        if (refillOrReturnForBait(player, movementPoints)) {
            return;
        }

        Optional<HuntPlan> huntPlan = bestHuntPlan(player);
        if (huntPlan.isPresent() && plannedTarget != null && plannedTarget.equals(huntPlan.get().baitPosition())) {
            executeHuntPlan(player, huntPlan.get(), movementPoints);
            return;
        }

        if (attemptTrackingCaptureOrMove(player)) {
            return;
        }

        if (huntPlan.isPresent()) {
            executeHuntPlan(player, huntPlan.get(), movementPoints);
            return;
        }

        if (plannedTarget != null) {
            rangerActionExecutor.moveRoleToward(player, RangerRole.HUNTER, plannedTarget, movementPoints);
            return;
        }

        rangerActionExecutor.moveRoleToward(player, RangerRole.HUNTER, player.scoutRanger.position(), movementPoints);
    }

    /**
     * Пополняет приманку на базе или возвращает охотника к базе, если приманка закончилась.
     *
     * @param player игрок, чей охотник активируется
     * @param movementPoints очки движения охотника
     * @return true, если действие потрачено на пополнение или возврат
     */
    private boolean refillOrReturnForBait(PlayerState player, int movementPoints) {
        if (player.hunterBait > 0) {
            return false;
        }
        if (!hasVisibleNeededHuntTarget(player)) {
            return false;
        }

        if (player.hunterRanger.position().equals(simulation.map.base)) {
            player.hunterBait = MAX_HUNTER_BAIT;
            player.rejectedHuntBaitPositions.clear();
            simulation.log("Охотник игрока " + player.id
                    + " пополнил приманку на базе: " + player.hunterBait + " / " + MAX_HUNTER_BAIT);
            return true;
        }

        rangerActionExecutor.moveRoleToward(player, RangerRole.HUNTER, simulation.map.base, movementPoints);
        simulation.log("Охотник игрока " + player.id + " возвращается на базу за приманкой");
        return true;
    }

    /** Проверяет, есть ли на карте нужная цель для охоты с приманкой. */
    private boolean hasVisibleNeededHuntTarget(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.trapped && !dinosaur.removed)
                .filter(dinosaur -> player.needs(dinosaur.species))
                .anyMatch(dinosaur -> dinosaur.species.captureMethod == CaptureMethod.HUNT);
    }

    /**
     * Продолжает уже начатую засаду: добирает карту или ждёт хищника.
     *
     * @param player игрок, чей охотник лежит в засаде
     * @return true, если активная засада заняла действие охотника
     */
    private boolean continueActiveHunt(PlayerState player) {
        HuntAmbush hunt = player.activeHunt;
        if (hunt == null) {
            return false;
        }

        player.hunterRanger.setPosition(hunt.baitPosition);
        hunt.advanceAmbushTurn();

        Optional<Dinosaur> target = dinosaurById(hunt.dinosaurId);
        int turnsUntilArrival = target
                .map(dinosaur -> simulation.estimateDinosaurTurnsTo(dinosaur, hunt.baitPosition))
                .orElse(Integer.MAX_VALUE);

        if (shouldRelocateAmbush(player, hunt, turnsUntilArrival)) {
            abandonAmbushForRelocation(player, hunt, turnsUntilArrival);
            return false;
        }

        if (shouldDrawHuntCard(hunt, turnsUntilArrival)) {
            Optional<HuntCard> card = hunt.drawCard();
            if (card.isPresent()) {
                simulation.log("Охотник игрока " + player.id
                        + " добрал карту охоты: " + card.get().displayName
                        + " (+" + card.get().points + "), подготовка " + hunt.preparationScore());
            } else {
                simulation.log("Охотник игрока " + player.id
                        + " ждёт в засаде: колода охоты пуста, подготовка " + hunt.preparationScore());
            }

            if (hunt.isOverPrepared()) {
                failOverPreparedHunt(player);
            }
            return true;
        }

        simulation.log("Охотник игрока " + player.id
                + " ждёт в засаде на " + hunt.species.displayName
                + "; подготовка " + hunt.preparationScore()
                + ", ожидаемый приход через " + turnsText(turnsUntilArrival));
        return true;
    }

    /**
     * Проверяет, пора ли бросить текущую засаду и перенести приманку.
     *
     * Засада считается плохой, если хищник больше не имеет понятного пути к
     * приманке или охотник уже слишком долго ждёт без контакта. Подготовка при
     * переносе сбрасывается, но приманка возвращается в инвентарь.
     *
     * @param player игрок, чей охотник ждёт в засаде
     * @param hunt активная засада
     * @param turnsUntilArrival текущая оценка прихода хищника
     * @return true, если засаду пора переносить
     */
    private boolean shouldRelocateAmbush(PlayerState player, HuntAmbush hunt, int turnsUntilArrival) {
        if (dinosaurById(hunt.dinosaurId).isEmpty()) {
            return true;
        }

        if (turnsUntilArrival == Integer.MAX_VALUE) {
            return true;
        }

        return hunt.ambushTurns() >= MAX_AMBUSH_TURNS_WITHOUT_CONTACT
                && turnsUntilArrival > 1;
    }

    /**
     * Сбрасывает текущую засаду и помечает её клетку как неудачную.
     *
     * @param player игрок, чей охотник переносит засаду
     * @param hunt активная засада
     * @param turnsUntilArrival текущая оценка прихода хищника
     */
    private void abandonAmbushForRelocation(PlayerState player, HuntAmbush hunt, int turnsUntilArrival) {
        player.rejectedHuntBaitPositions.add(hunt.baitPosition);
        player.activeHunt = null;
        player.hunterBait = Math.min(MAX_HUNTER_BAIT, player.hunterBait + 1);

        simulation.log("Охотник игрока " + player.id
                + " переносит засаду на " + hunt.species.displayName
                + ": ждал " + hunt.ambushTurns()
                + " активаций, ожидаемый приход " + turnsText(turnsUntilArrival)
                + ". Подготовка сброшена, приманка возвращена");
    }

    /**
     * Решает, стоит ли добирать ещё одну карту охоты.
     *
     * @param hunt активная засада
     * @param turnsUntilArrival оценка времени до прихода хищника
     * @return true, если AI готов рискнуть добором
     */
    private boolean shouldDrawHuntCard(HuntAmbush hunt, int turnsUntilArrival) {
        if (hunt.remainingCards() <= 0) return false;
        if (hunt.preparationScore() < SAFE_PREPARATION_TARGET) return true;
        return hunt.preparationScore() < STRONG_PREPARATION_TARGET && turnsUntilArrival > 1;
    }

    /**
     * Срывает засаду из-за подготовки выше лимита.
     *
     * @param player игрок, чей охотник перестарался
     */
    private void failOverPreparedHunt(PlayerState player) {
        HuntAmbush hunt = player.activeHunt;
        player.activeHunt = null;
        player.hunterRanger.setPosition(simulation.map.base);
        simulation.log("ПРОВАЛ ОХОТЫ: охотник игрока " + player.id
                + " набрал " + hunt.preparationScore()
                + " подготовки и перемудрил. Приманку съели, охотник вернулся на базу");
    }

    /**
     * Пытается поймать или приблизиться к цели выслеживания.
     *
     * @param player игрок, чей охотник активируется
     * @return true, если действие было потрачено на выслеживание
     */
    private boolean attemptTrackingCaptureOrMove(PlayerState player) {
        Optional<Dinosaur> target = nearestNeededDinosaur(
                player,
                player.hunterRanger.position(),
                CaptureMethod.TRACKING
        );

        if (target.isEmpty()) {
            return false;
        }

        Dinosaur dinosaur = target.get();
        if (player.hunterRanger.position().manhattan(dinosaur.position) <= 1) {
            double chance = simulation.gameMechanicConfig.trackingBaseSuccess
                    + simulation.gameMechanicConfig.trackingStepBonus
                    * simulation.random.nextInt(simulation.gameMechanicConfig.trackingMaxSteps);

            if (simulation.random.nextDouble() < chance) {
                capture(player, dinosaur, "выслеживание");
                simulation.result.trackingCaptures++;
            }
            return true;
        }

        player.setPosition(
                RangerRole.HUNTER,
                simulation.map.stepGroundRangerToward(player.hunterRanger.position(), dinosaur.position)
        );
        return true;
    }

    /**
     * Исполняет план охоты: идёт к точке приманки или начинает засаду на месте.
     *
     * @param player игрок, чей охотник действует
     * @param huntPlan выбранная клетка и хищник
     * @param movementPoints очки движения охотника
     */
    private void executeHuntPlan(PlayerState player, HuntPlan huntPlan, int movementPoints) {
        Point hunterPosition = player.hunterRanger.position();
        Point baitPosition = huntPlan.baitPosition();

        if (!hunterPosition.equals(baitPosition)) {
            rangerActionExecutor.moveRoleToward(player, RangerRole.HUNTER, baitPosition, movementPoints);
            if (!player.hunterRanger.position().equals(baitPosition)) {
                return;
            }
        }

        startHuntAmbush(player, huntPlan.dinosaur(), baitPosition);
    }

    /**
     * Начинает фазу охоты: кладёт приманку и берёт две стартовые карты.
     *
     * @param player игрок, чей охотник начинает засаду
     * @param dinosaur целевой M-хищник
     * @param baitPosition клетка с приманкой
     */
    private void startHuntAmbush(PlayerState player, Dinosaur dinosaur, Point baitPosition) {
        if (player.hunterBait <= 0) {
            return;
        }
        if (!simulation.map.canPlaceBait(baitPosition)) {
            return;
        }

        player.hunterBait--;
        player.activeHunt = new HuntAmbush(
                player.id,
                dinosaur.id,
                dinosaur.species,
                baitPosition,
                HuntCard.createShuffledDeck(simulation.random)
        );

        Optional<HuntCard> first = player.activeHunt.drawCard();
        Optional<HuntCard> second = player.activeHunt.drawCard();

        simulation.log("Охотник игрока " + player.id
                + " начал засаду на " + dinosaur.species.displayName
                + " #" + dinosaur.id
                + " в клетке " + baitPosition
                + "; стартовые карты: " + cardText(first) + ", " + cardText(second)
                + "; подготовка " + player.activeHunt.preparationScore());
    }

    /**
     * Выбирает лучшую видимую HUNT-цель и клетку засады для неё.
     *
     * @param player игрок, чей охотник планирует охоту
     * @return план охоты или Optional.empty(), если подходящей охоты нет
     */
    private Optional<HuntPlan> bestHuntPlan(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.HUNT)
                .map(dinosaur -> simulation.bestHuntAmbushPointFor(player, dinosaur)
                        .map(point -> new HuntPlan(dinosaur, point)))
                .flatMap(Optional::stream)
                .min(Comparator
                        .comparingInt((HuntPlan plan) -> simulation.estimateDinosaurTurnsTo(plan.dinosaur(), plan.baitPosition()))
                        .thenComparingInt(plan -> player.hunterRanger.position().manhattan(plan.baitPosition())));
    }

    /** Ищет ближайшего нужного динозавра с одним из указанных способов поимки. */
    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        List<CaptureMethod> allowedMethods = List.of(methods);
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .sorted(Comparator.comparingInt(d -> d.position.manhattan(from)))
                .findFirst();
    }

    /** Ищет динозавра по ID. */
    private Optional<Dinosaur> dinosaurById(int dinosaurId) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> dinosaur.id == dinosaurId)
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .findFirst();
    }

    /** Засчитывает динозавра игроку. */
    private void capture(PlayerState player, Dinosaur dinosaur, String method) {
        dinosaur.captured = true;
        player.captured.add(dinosaur.species);
        simulation.log("ПОЙМАН: игрок " + player.id + " поймал "
                + dinosaur.species.displayName + " (" + method + ")");
    }

    /** Форматирует карту охоты для лога. */
    private String cardText(Optional<HuntCard> card) {
        return card
                .map(value -> value.displayName + " (+" + value.points + ")")
                .orElse("нет карты");
    }

    /** Форматирует оценку ожидания для лога. */
    private String turnsText(int turnsUntilArrival) {
        if (turnsUntilArrival == Integer.MAX_VALUE) {
            return "неизвестно";
        }
        return turnsUntilArrival + " ход.";
    }

    /** План охотничьей засады: целевой динозавр и клетка с приманкой. */
    private record HuntPlan(Dinosaur dinosaur, Point baitPosition) {
    }
}
