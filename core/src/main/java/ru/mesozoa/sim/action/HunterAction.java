package ru.mesozoa.sim.action;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.HuntAmbush;
import ru.mesozoa.sim.model.HuntCard;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.model.TrackingTrail;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.ranger.ai.HunterAi;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Исполнение действий охотника.
 *
 * Охотник разделён на два режима: засада на M-хищников и цепочка следов для
 * M-травоядных. Активный режим имеет приоритет над новым выбором цели, чтобы AI
 * не метался между целями и не сбрасывал уже начатую механику без результата.
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
    private final HunterAi hunterAi;

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
        this.hunterAi = new HunterAi(simulation);
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
        action(player, hunterAi.chooseHunterTarget(player).orElse(null), movementPoints);
    }

    /**
     * Центральная логика охотника: сначала обязательные активные режимы, затем
     * новый выбор между выслеживанием и охотой с приманкой.
     */
    private void action(PlayerState player, Point plannedTarget, int movementPoints) {
        boolean hadActiveHunt = player.activeHunt != null;
        if (continueActiveHunt(player)) {
            return;
        }
        if (hadActiveHunt && player.activeHunt == null) {
            plannedTarget = hunterAi.chooseHunterTarget(player).orElse(null);
        }

        if (continueActiveTracking(player, movementPoints)) {
            return;
        }

        if (plannedTarget == null) {
            plannedTarget = hunterAi.chooseHunterTarget(player).orElse(null);
        }

        Optional<Dinosaur> trackingTarget = trackingTargetAt(player, plannedTarget);
        if (trackingTarget.isPresent()) {
            executeTrackingPlan(player, trackingTarget.get(), movementPoints);
            return;
        }

        if (refillOrReturnForBait(player, movementPoints)) {
            return;
        }

        Optional<HunterAi.HuntPlan> huntPlan = bestHuntPlan(player);
        if (huntPlan.isPresent() && plannedTarget != null && plannedTarget.equals(huntPlan.get().baitPosition())) {
            executeHuntPlan(player, huntPlan.get(), movementPoints);
            return;
        }

        Optional<Dinosaur> fallbackTrackingTarget = hunterAi.bestTrackingTarget(player);
        if (fallbackTrackingTarget.isPresent()) {
            executeTrackingPlan(player, fallbackTrackingTarget.get(), movementPoints);
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
        if (hunterAi.bestTrackingTarget(player).isPresent()) {
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
                .anyMatch(dinosaur -> dinosaur.captureMethod == CaptureMethod.HUNT);
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
                .map(dinosaur -> simulation.dinosaurAi.estimateDinosaurTurnsTo(dinosaur, hunt.baitPosition))
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
     * Продолжает уже начатую цепочку выслеживания M-травоядного.
     *
     * @param player игрок, чей охотник идёт по следу
     * @param movementPoints очки движения охотника
     * @return true, если действие занято выслеживанием
     */
    private boolean continueActiveTracking(PlayerState player, int movementPoints) {
        TrackingTrail trail = player.activeTracking;
        if (trail == null) {
            return false;
        }

        Optional<Dinosaur> target = dinosaurById(trail.dinosaurId);
        if (target.isEmpty() || !player.needs(trail.species)) {
            player.activeTracking = null;
            simulation.log("След " + trail.species.displayName
                    + " для игрока " + player.id
                    + " оборвался: целевой динозавр больше не подходит");
            return false;
        }

        Dinosaur dinosaur = target.get();
        if (!player.hunterRanger.position().equals(dinosaur.position)) {
            moveHunterByTracking(player, dinosaur.position, movementPoints);
            simulation.log("Охотник игрока " + player.id
                    + " идёт по следу " + dinosaur.displayName
                    + " #" + dinosaur.id
                    + " к " + dinosaur.position
                    + "; карты " + trail.preparationScore() + " / 10"
                    + ", следов " + trail.trailTokens().size());
            return true;
        }

        resolveTrackingAttempt(player, dinosaur);
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
        player.registerFailedHuntAttempt(hunt.dinosaurId);
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
        player.registerFailedHuntAttempt(hunt.dinosaurId);
        player.activeHunt = null;
        player.hunterRanger.setPosition(simulation.map.base);
        simulation.log("ПРОВАЛ ОХОТЫ: охотник игрока " + player.id
                + " набрал " + hunt.preparationScore()
                + " подготовки и перемудрил. Приманку съели, охотник вернулся на базу");
    }

    /**
     * Исполняет план выслеживания: идёт к М-травоядному или начинает/продолжает
     * попытку поимки, если охотник уже стоит с ним на одной клетке.
     *
     * @param player игрок, чей охотник действует
     * @param dinosaur целевой М-травоядный
     * @param movementPoints очки движения охотника
     */
    private void executeTrackingPlan(PlayerState player, Dinosaur dinosaur, int movementPoints) {
        if (!player.hunterRanger.position().equals(dinosaur.position)) {
            moveHunterByTracking(player, dinosaur.position, movementPoints);
            if (!player.hunterRanger.position().equals(dinosaur.position)) {
                return;
            }
        }

        if (player.activeTracking == null || player.activeTracking.dinosaurId != dinosaur.id) {
            player.activeTracking = new TrackingTrail(
                    player.id,
                    dinosaur.id,
                    dinosaur.species,
                    HuntCard.createShuffledDeck(simulation.random)
            );
            simulation.log("Охотник игрока " + player.id
                    + " начал выслеживание " + dinosaur.displayName
                    + " #" + dinosaur.id
                    + " на " + dinosaur.position);
        }

        resolveTrackingAttempt(player, dinosaur);
    }

    /**
     * Выполняет одну попытку поимки М-травоядного по активной цепочке следов.
     *
     * @param player игрок, чей охотник совершает попытку
     * @param dinosaur целевой М-травоядный
     */
    private void resolveTrackingAttempt(PlayerState player, Dinosaur dinosaur) {
        TrackingTrail trail = player.activeTracking;
        if (trail == null || trail.dinosaurId != dinosaur.id) {
            return;
        }

        if (!trail.canStartAttempt()) {
            failTracking(player, trail, "нет свободного жетона следа или карты Охота для новой попытки");
            return;
        }

        int attempt = trail.startAttempt();
        Optional<HuntCard> card = trail.drawAttemptCard();
        simulation.log("Выслеживание " + dinosaur.displayName
                + " #" + dinosaur.id
                + ": попытка " + attempt
                + ", карта " + cardText(card)
                + ", сумма " + trail.preparationScore()
                + ", жетонов осталось " + trail.remainingTrailTokens());

        if (trail.isOverPrepared()) {
            moveTrackingTargetAway(player, trail, dinosaur);
            failTracking(player, trail, "охотник набрал " + trail.preparationScore()
                    + " очков карт и зверь ушёл от него");
            return;
        }

        int dinosaurRoll = simulation.random.nextInt(6) + 1;
        int dinosaurScore = dinosaur.agility + dinosaurRoll;
        int hunterScore = trail.preparationScore();

        if (hunterScore > dinosaurScore) {
            captureByTracking(player, dinosaur, trail, dinosaurRoll, dinosaurScore, hunterScore);
            return;
        }

        moveTrackingTargetAway(player, trail, dinosaur);

        if (attempt >= TrackingTrail.MAX_ATTEMPTS) {
            failTracking(player, trail, "после третьей попытки след простыл; карты сброшены");
            return;
        }

        simulation.log("СЛЕД: " + dinosaur.displayName
                + " #" + dinosaur.id
                + " ушёл от охотника игрока " + player.id
                + "; охотник " + hunterScore
                + " против " + dinosaurScore
                + " (ловк. " + dinosaur.agility + " + 1d6=" + dinosaurRoll + ")"
                + "; цепочка " + trail.trailTokens().size() + " / " + (TrackingTrail.MAX_ATTEMPTS - 1));
    }

    /**
     * Обездвиживает М-травоядного после успешного выслеживания.
     *
     * @param player игрок, чей охотник поймал зверя
     * @param dinosaur пойманный динозавр
     * @param trail завершённая цепочка следов
     * @param dinosaurRoll бросок 1d6 динозавра
     * @param dinosaurScore итог динозавра
     * @param hunterScore итог охотника
     */
    private void captureByTracking(
            PlayerState player,
            Dinosaur dinosaur,
            TrackingTrail trail,
            int dinosaurRoll,
            int dinosaurScore,
            int hunterScore
    ) {
        dinosaur.trapped = true;
        dinosaur.trappedByPlayerId = player.id;
        clearTrackingTrailTokens(player, trail);
        player.trailTokens.add(trail.createTrailToken(dinosaur.position, Direction.NORTH, true));
        player.activeTracking = null;
        player.clearCaptureFailures(dinosaur.id);
        simulation.result.trackingCaptures++;
        simulation.log("ПОЙМАН ПО СЛЕДУ: охотник игрока " + player.id
                + " обездвижил " + dinosaur.displayName
                + " #" + dinosaur.id
                + "; карты " + hunterScore
                + " против " + dinosaurScore
                + " (ловк. " + dinosaur.agility + " + 1d6=" + dinosaurRoll + ")"
                + "; жетон следа остаётся на клетке до вывоза водителем");
    }

    /**
     * Завершает цепочку выслеживания провалом и даёт AI мягкий штраф выбора.
     *
     * @param player игрок, чей след сорвался
     * @param trail активная цепочка следов
     * @param reason причина провала для лога
     */
    private void failTracking(PlayerState player, TrackingTrail trail, String reason) {
        player.registerFailedTrackingChain(trail.dinosaurId);
        clearTrackingTrailTokens(player, trail);
        player.activeTracking = null;
        simulation.log("ПРОВАЛ ВЫСЛЕЖИВАНИЯ: " + trail.species.displayName
                + " для игрока " + player.id
                + " — " + reason);
    }

    /**
     * Уводит М-травоядного на следующую клетку био-тропы и кладёт жетон следа.
     *
     * @param trail активная цепочка следов
     * @param dinosaur целевой динозавр
     */
    private void moveTrackingTargetAway(PlayerState player, TrackingTrail trail, Dinosaur dinosaur) {
        Point before = dinosaur.position;
        dinosaur.lastPosition = before;
        simulation.dinosaurAi.moveByBioTrail(dinosaur);
        Point after = dinosaur.position;

        if (before.equals(after)) {
            simulation.log(dinosaur.displayName + " #" + dinosaur.id
                    + " сорвался с места, но не нашёл доступный шаг био-тропы и остался на " + after);
            return;
        }

        Direction direction = directionBetween(before, after);
        player.trailTokens.add(trail.createTrailToken(before, direction, false));
        simulation.log(dinosaur.displayName + " #" + dinosaur.id
                + " оставил след " + direction
                + ": " + before + " -> " + after);
    }

    /**
     * Снимает с карты все жетоны текущей цепочки выслеживания.
     *
     * При срыве следа или успешной поимке старая цепочка больше не должна
     * занимать физические жетоны игрока. На успешной поимке после этого
     * создаётся один отдельный маркер на клетке обездвиженного динозавра.
     *
     * @param player владелец жетонов
     * @param trail цепочка выслеживания, которую нужно очистить
     */
    private void clearTrackingTrailTokens(PlayerState player, TrackingTrail trail) {
        if (trail == null) {
            return;
        }

        player.trailTokens.removeIf(token -> token.dinosaurId == trail.dinosaurId && !token.captureMarker);
    }

    /**
     * Исполняет план охоты: идёт к точке приманки или начинает засаду на месте.
     *
     * @param player игрок, чей охотник действует
     * @param huntPlan выбранная клетка и хищник
     * @param movementPoints очки движения охотника
     */
    private void executeHuntPlan(PlayerState player, HunterAi.HuntPlan huntPlan, int movementPoints) {
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
                + " начал засаду на " + dinosaur.displayName
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
    private Optional<HunterAi.HuntPlan> bestHuntPlan(PlayerState player) {
        return hunterAi.bestHuntPlan(player);
    }

    /** Ищет ближайшего нужного динозавра с одним из указанных способов поимки. */
    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        List<CaptureMethod> allowedMethods = List.of(methods);
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.captureMethod))
                .sorted(Comparator.comparingInt(d -> d.position.chebyshev(from)))
                .findFirst();
    }

    /** Ищет нужную цель выслеживания на запланированной клетке. */
    private Optional<Dinosaur> trackingTargetAt(PlayerState player, Point point) {
        if (point == null) {
            return Optional.empty();
        }

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.captureMethod == CaptureMethod.TRACKING)
                .filter(d -> d.position.equals(point))
                .findFirst()
                .or(() -> nearestNeededDinosaur(player, player.hunterRanger.position(), CaptureMethod.TRACKING)
                        .filter(dinosaur -> dinosaur.position.equals(point)));
    }

    /** Двигает охотника по особому режиму следов через любые открытые клетки. */
    private void moveHunterByTracking(PlayerState player, Point target, int movementPoints) {
        Point position = player.hunterRanger.position();
        for (int i = 0; i < movementPoints; i++) {
            if (position.equals(target)) break;
            Point next = simulation.map.stepHunterTrackingToward(position, target);
            if (next.equals(position)) break;
            position = next;
        }
        player.setPosition(RangerRole.HUNTER, position);
    }

    /** Ищет динозавра по ID. */
    private Optional<Dinosaur> dinosaurById(int dinosaurId) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> dinosaur.id == dinosaurId)
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.trapped && !dinosaur.removed)
                .findFirst();
    }

    /** Определяет направление следа по смещению между клетками. */
    private Direction directionBetween(Point from, Point to) {
        int dx = Integer.compare(to.x, from.x);
        int dy = Integer.compare(to.y, from.y);

        for (Direction direction : Direction.values()) {
            if (direction.dx == dx && direction.dy == dy) {
                return direction;
            }
        }

        return Direction.NORTH;
    }

    /** Форматирует карту охоты для лога. */
    private String cardText(Optional<HuntCard> card) {
        return card
                .map(value -> value.displayName + " (+" + value.points + ")")
                .orElse("нет карты");
    }

    /** Форматирует список карт охоты для лога. */
    private String cardsText(List<HuntCard> cards) {
        if (cards.isEmpty()) {
            return "нет карт";
        }

        return cards.stream()
                .map(card -> card.displayName + " (+" + card.points + ")")
                .collect(Collectors.joining(", "));
    }

    /** Форматирует оценку ожидания для лога. */
    private String turnsText(int turnsUntilArrival) {
        if (turnsUntilArrival == Integer.MAX_VALUE) {
            return "неизвестно";
        }
        return turnsUntilArrival + " ход.";
    }
}
