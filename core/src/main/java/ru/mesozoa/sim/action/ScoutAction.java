package ru.mesozoa.sim.action;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.ranger.ai.HunterAi;
import ru.mesozoa.sim.ranger.ai.ScoutAi;
import ru.mesozoa.sim.simulation.GameSimulation;
import ru.mesozoa.sim.tile.ExtraTile;
import ru.mesozoa.sim.tile.MainTile;
import ru.mesozoa.sim.tile.Tile;

import java.util.Comparator;
import java.util.Optional;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Выполнение действия разведчика.
 *
 * Разведчик открывает основные тайлы и автоматически достраивает дополнительные
 * тайлы по переходам. Он не тратит очки на перемещение: вертолёт может оказаться
 * у любого фронтира карты, но AI всё равно выбирает полезную кромку для команды,
 * а не устраивает одиночный туризм за горной стеной.
 */
public class ScoutAction {

    /** Базовый бонус для клетки, которую команда может поддержать с земли. */
    private static final double SUPPORTED_FRONTIER_BONUS = 1_000.0;

    /** Штраф для фронтира, который не касается достижимой области команды. */
    private static final double UNSUPPORTED_FRONTIER_PENALTY = -1_000.0;

    /** Бонус за новый проходимый тайл, который расширяет рабочую область команды. */
    private static final double PASSABLE_TILE_BONUS = 240.0;

    /** Штраф за непроходимый основной тайл на фронтире команды. */
    private static final double BLOCKING_TILE_PENALTY = -160.0;

    /** Бонус за совпадение биома вытянутого тайла с ещё ненайденной целью. */
    private static final double MISSING_SPAWN_BIOME_BONUS = 120.0;

    /** Бонус за каждый переход, который достраивает дополнительный тайл возле команды. */
    private static final double SUPPORTED_EXPANSION_BONUS = 55.0;

    /** Штраф за переход, который улетает в область, не связанную с командой. */
    private static final double UNSUPPORTED_EXPANSION_PENALTY = -35.0;

    /** Штраф за каждый шаг удаления фронтира от базы. */
    private static final double BASE_DISTANCE_PENALTY = 4.0;

    /** Бонус разведки вокруг видимого HUNT-хищника без хорошей клетки засады. */
    private static final double HUNT_SUPPORT_NEAR_TARGET_BONUS = 1_250.0;

    /** Бонус за вытянутый биом, который нужен следующему шагу био-тропы HUNT-хищника. */
    private static final double HUNT_SUPPORT_NEXT_BIOME_BONUS = 320.0;

    /** Бонус разведки вокруг видимой S-цели, которая застряла без хорошего ловушечного маршрута. */
    private static final double TRAP_SUPPORT_NEAR_TARGET_BONUS = 1_350.0;

    /** Бонус за вытянутый биом следующего шага био-тропы S-динозавра. */
    private static final double TRAP_SUPPORT_NEXT_BIOME_BONUS = 360.0;

    private final GameSimulation simulation;
    private final DinosaurAction dinosaurAction;
    private final HunterAi hunterAi;
    private final ScoutAi scoutAi;

    public ScoutAction(GameSimulation simulation,
                       DinosaurAction dinosaurAction) {

        this.simulation = simulation;
        this.dinosaurAction = dinosaurAction;
        this.hunterAi = new HunterAi(simulation);
        this.scoutAi = new ScoutAi(simulation);
    }

    /**
     * Выполняет единственное действие разведчика: открывает новый участок карты.
     *
     * У разведчика больше нет очков движения. Его фигурка после разведки просто
     * оказывается на открытом тайле, потому что вертолёт, как ни странно, умеет
     * летать, а не просить у болота разрешение пройти.
     *
     * @param player игрок, чей разведчик активируется
     * @param plan выбранный AI план разведчика
     */
    public void action(PlayerState player, RangerPlan plan) {
        action(player);
    }

    /**
     * Открывает один основной тайл, если мешочек исследования ещё не пуст.
     *
     * @param player игрок, чей разведчик действует
     */
    public void action(PlayerState player) {
        if (simulation.tileBag.isEmpty()) {
            simulation.log("Разведчик игрока " + player.id + " не может разведать новый участок: основные тайлы закончились");
            return;
        }

        exploreOneTile(player);
    }

    /**
     * Открывает один основной тайл и все его дополнительные переходы.
     *
     * Место и поворот основного тайла выбираются до выкладки: AI старается не
     * создавать изолированные островки разведки, потому что динозавр за стеной
     * гор — это не цель, а музейный экспонат для очень одинокого разведчика.
     *
     * @param player игрок, выполняющий разведку
     */
    private void exploreOneTile(PlayerState player) {
        MainTile drawn = simulation.tileBag.draw();
        if (drawn == null) return;

        Point placement = choosePlacementPoint(player, drawn);
        if (placement == null) return;

        int rotationQuarterTurns = chooseRotationQuarterTurns(player, drawn, placement);
        Tile placedTile = placeDrawnTile(player, drawn, placement, false, rotationQuarterTurns);
        if (placedTile == null) return;

        for (Direction direction : placedTile.expansionDirections()) {
            Point extraPoint = direction.from(placement);

            if (!simulation.map.canPlaceExpansion(extraPoint)) {
                continue;
            }

            ExtraTile extra = simulation.tileBag.drawExtraBiome(placedTile.biome);
            if (extra == null) {
                simulation.log("Нет доп. тайла для биома " + placedTile.biome.displayName);
                continue;
            }

            placeDrawnTile(player, extra, extraPoint, true, simulation.random.nextInt(4));
        }
    }

    /**
     * Выбирает лучшую свободную клетку для нового основного тайла.
     *
     * Приоритет теперь не "где ближе к разведчику", а "где это поможет всей
     * экспедиции". Поэтому клетка рядом с наземно достижимой областью базы почти
     * всегда лучше дальнего края карты за горами.
     *
     * @param player игрок, чьё задание учитывается
     * @param drawn вытянутый основной тайл
     * @return лучшая клетка выкладки или null, если выкладка невозможна
     */
    private Point choosePlacementPoint(PlayerState player, MainTile drawn) {
        List<Point> candidates = simulation.map.availablePlacementPoints();
        if (candidates.isEmpty()) return null;

        Set<Species> missingSpecies = missingNeededSpecies(player);
        Optional<Dinosaur> huntSupportTarget = hunterAi.bestVisibleHuntTargetNeedingScoutSupport(player);
        Optional<Dinosaur> trapSupportTarget = scoutAi.bestVisibleTrapTargetNeedingScoutSupport(player);

        return candidates.stream()
                .max(Comparator
                        .comparingDouble((Point point) -> scoutPlacementScore(player, drawn, point, missingSpecies, huntSupportTarget, trapSupportTarget))
                        .thenComparingInt(point -> -point.chebyshev(simulation.map.base))
                        .thenComparingInt(point -> -point.chebyshev(player.engineerRanger.position()))
                        .thenComparingInt(point -> -point.chebyshev(player.hunterRanger.position())))
                .orElse(candidates.get(simulation.random.nextInt(candidates.size())));
    }

    /**
     * Рассчитывает полезность клетки для выкладки основного тайла разведчиком.
     *
     * @param player игрок, для которого строится разведка
     * @param drawn вытянутый основной тайл
     * @param point проверяемая свободная клетка
     * @param missingSpecies виды задания, которых ещё нет на карте
     * @return численная полезность клетки
     */
    private double scoutPlacementScore(
            PlayerState player,
            MainTile drawn,
            Point point,
            Set<Species> missingSpecies,
            Optional<Dinosaur> huntSupportTarget,
            Optional<Dinosaur> trapSupportTarget
    ) {
        int supportCount = simulation.map.groundNetworkSupportCount(point);
        int distanceToGroundNetwork = simulation.map.distanceToGroundNetworkFromBase(point);

        double score = supportCount > 0
                ? SUPPORTED_FRONTIER_BONUS + supportCount * 45.0
                : UNSUPPORTED_FRONTIER_PENALTY - distancePenalty(distanceToGroundNetwork, 75.0);

        score += drawn.isGroundPassable() ? PASSABLE_TILE_BONUS : BLOCKING_TILE_PENALTY;
        score += missingSpawnBiomeScore(drawn, missingSpecies);
        score += huntSupportPlacementScore(drawn, point, huntSupportTarget);
        score += trapSupportPlacementScore(drawn, point, trapSupportTarget);
        score += bestExpansionScore(player, drawn, point, missingSpecies, huntSupportTarget, trapSupportTarget);
        score -= point.chebyshev(simulation.map.base) * BASE_DISTANCE_PENALTY;
        score -= Math.min(point.chebyshev(player.engineerRanger.position()), 12) * 1.5;
        score -= Math.min(point.chebyshev(player.hunterRanger.position()), 12) * 1.5;
        return score;
    }

    /**
     * Превращает расстояние до команды в ограниченный штраф.
     *
     * @param distance расстояние до наземной сети базы
     * @param multiplier множитель штрафа
     * @return штраф, безопасный для Integer.MAX_VALUE
     */
    private double distancePenalty(int distance, double multiplier) {
        if (distance == Integer.MAX_VALUE) return 10_000.0;
        return Math.min(distance, 20) * multiplier;
    }

    /**
     * Даёт бонус тайлу, если его биом может вытянуть дополнительный спаун нужного вида.
     *
     * @param drawn вытянутый основной тайл
     * @param missingSpecies виды задания, которых ещё не видно на карте
     * @return бонус за поисковый биом
     */
    private double missingSpawnBiomeScore(MainTile drawn, Set<Species> missingSpecies) {
        for (Species species : missingSpecies) {
            if (Dinosaur.spawnBiomeOf(species) == drawn.biome) {
                return drawn.hasExpansion()
                        ? MISSING_SPAWN_BIOME_BONUS
                        : MISSING_SPAWN_BIOME_BONUS * 0.35;
            }
        }
        return 0.0;
    }

    /**
     * Даёт бонус разведке вокруг видимого HUNT-хищника, которому не хватает карты.
     *
     * Если Велоцитаурус уже найден, но охотник не может построить нормальную
     * засаду, разведчик открывает клетки рядом с ним и особенно ценит биом
     * следующего шага био-тропы. Так цель становится не просто видимой, а ловимой.
     *
     * @param drawn вытянутый тайл
     * @param point клетка выкладки
     * @param huntSupportTarget цель, которой нужна разведка окружения
     * @return бонус за поддержку охоты
     */
    private double huntSupportPlacementScore(
            MainTile drawn,
            Point point,
            Optional<Dinosaur> huntSupportTarget
    ) {
        if (huntSupportTarget.isEmpty()) {
            return 0.0;
        }

        Dinosaur dinosaur = huntSupportTarget.get();
        int distance = point.chebyshev(dinosaur.position);
        double score = Math.max(0.0, HUNT_SUPPORT_NEAR_TARGET_BONUS - distance * 115.0);

        Tile currentTile = simulation.map.tile(dinosaur.position);
        if (currentTile != null && drawn.biome == dinosaur.nextBioTrailBiome(currentTile.biome)) {
            score += HUNT_SUPPORT_NEXT_BIOME_BONUS;
        }

        if (dinosaur.canEnter(drawn.biome)) {
            score += 90.0;
        }
        if (drawn.hasExpansion()) {
            score += 45.0;
        }

        return score;
    }


    /**
     * Даёт бонус разведке вокруг видимой S-цели, у которой нет хорошего ловушечного маршрута.
     *
     * Разведчик должен открывать не абстрактные красивые края карты, а клетки
     * рядом с Галлимимоном/Дриорнисом, если инженер не может построить рабочий
     * план ловушек. Особенно ценится следующий биом био-тропы: так динозавр
     * перестаёт стоять в тупике и снова начинает ходить прогнозируемо.
     *
     * @param drawn вытянутый тайл
     * @param point клетка выкладки
     * @param trapSupportTarget S-цель, которой нужна разведка окружения
     * @return бонус за поддержку ловушек
     */
    private double trapSupportPlacementScore(
            MainTile drawn,
            Point point,
            Optional<Dinosaur> trapSupportTarget
    ) {
        if (trapSupportTarget.isEmpty()) {
            return 0.0;
        }

        Dinosaur dinosaur = trapSupportTarget.get();
        int distance = point.chebyshev(dinosaur.position);
        double score = Math.max(0.0, TRAP_SUPPORT_NEAR_TARGET_BONUS - distance * 170.0);

        Tile currentTile = simulation.map.tile(dinosaur.position);
        if (currentTile != null && drawn.biome == dinosaur.nextBioTrailBiome(currentTile.biome)) {
            score += TRAP_SUPPORT_NEXT_BIOME_BONUS;
        }

        if (dinosaur.canEnter(drawn.biome)) {
            score += 120.0;
        }
        if (drawn.hasExpansion()) {
            score += 70.0;
        }

        return score;
    }

    /**
     * Выбирает поворот основного тайла, который лучше всего поддерживает командную разведку.
     *
     * @param player игрок, чьё задание учитывается
     * @param drawn вытянутый основной тайл
     * @param placement выбранная клетка выкладки
     * @return количество поворотов по 90 градусов по часовой стрелке
     */
    private int chooseRotationQuarterTurns(PlayerState player, MainTile drawn, Point placement) {
        Set<Species> missingSpecies = missingNeededSpecies(player);
        Optional<Dinosaur> huntSupportTarget = hunterAi.bestVisibleHuntTargetNeedingScoutSupport(player);
        Optional<Dinosaur> trapSupportTarget = scoutAi.bestVisibleTrapTargetNeedingScoutSupport(player);
        int bestRotation = 0;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int rotation = 0; rotation < 4; rotation++) {
            double score = expansionScoreForRotation(drawn, placement, missingSpecies, huntSupportTarget, trapSupportTarget, rotation);
            if (score > bestScore) {
                bestScore = score;
                bestRotation = rotation;
            }
        }

        return bestRotation;
    }

    /**
     * Возвращает лучшую возможную оценку переходов основного тайла при любом повороте.
     *
     * @param player игрок, чьё задание учитывается
     * @param drawn вытянутый основной тайл
     * @param placement проверяемая клетка выкладки
     * @param missingSpecies виды задания, которых ещё не видно на карте
     * @return лучшая оценка переходов
     */
    private double bestExpansionScore(
            PlayerState player,
            MainTile drawn,
            Point placement,
            Set<Species> missingSpecies,
            Optional<Dinosaur> huntSupportTarget,
            Optional<Dinosaur> trapSupportTarget
    ) {
        double best = Double.NEGATIVE_INFINITY;
        for (int rotation = 0; rotation < 4; rotation++) {
            best = Math.max(best, expansionScoreForRotation(drawn, placement, missingSpecies, huntSupportTarget, trapSupportTarget, rotation));
        }
        return best == Double.NEGATIVE_INFINITY ? 0.0 : best;
    }

    /**
     * Оценивает, насколько переходы тайла при конкретном повороте останутся полезными команде.
     *
     * @param drawn вытянутый основной тайл
     * @param placement клетка выкладки основного тайла
     * @param missingSpecies виды задания, которых ещё не видно на карте
     * @param rotationQuarterTurns проверяемый поворот
     * @return оценка переходов при этом повороте
     */
    private double expansionScoreForRotation(
            MainTile drawn,
            Point placement,
            Set<Species> missingSpecies,
            Optional<Dinosaur> huntSupportTarget,
            Optional<Dinosaur> trapSupportTarget,
            int rotationQuarterTurns
    ) {
        if (drawn.baseExpansionDirections.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        boolean missingBiome = missingSpecies.stream().anyMatch(species -> Dinosaur.spawnBiomeOf(species) == drawn.biome);

        for (Direction baseDirection : drawn.baseExpansionDirections) {
            Direction direction = baseDirection.rotateClockwiseQuarterTurns(rotationQuarterTurns);
            Point extraPoint = direction.from(placement);

            if (!simulation.map.canPlaceExpansion(extraPoint)) {
                score -= 70.0;
                continue;
            }

            boolean cardinalToMainTile = Math.abs(direction.dx) + Math.abs(direction.dy) == 1;
            boolean supportedByMainTile = cardinalToMainTile && drawn.isGroundPassable();
            boolean supportedByExistingTeam = simulation.map.touchesGroundNetworkFromBase(extraPoint);

            if (supportedByMainTile || supportedByExistingTeam) {
                score += SUPPORTED_EXPANSION_BONUS;
                if (missingBiome) {
                    score += 35.0;
                }
                score += huntSupportExpansionScore(extraPoint, huntSupportTarget);
                score += trapSupportExpansionScore(extraPoint, trapSupportTarget);
            } else {
                score += UNSUPPORTED_EXPANSION_PENALTY;
            }
        }

        return score;
    }

    /**
     * Даёт бонус дополнительному тайлу, если он раскрывает область рядом с HUNT-целью.
     *
     * @param extraPoint клетка потенциального дополнительного тайла
     * @param huntSupportTarget цель охоты, которой нужна разведка окружения
     * @return бонус за полезное расширение вокруг хищника
     */
    private double huntSupportExpansionScore(Point extraPoint, Optional<Dinosaur> huntSupportTarget) {
        if (huntSupportTarget.isEmpty()) {
            return 0.0;
        }

        int distance = extraPoint.chebyshev(huntSupportTarget.get().position);
        return Math.max(0.0, 160.0 - distance * 55.0);
    }


    /**
     * Даёт бонус дополнительному тайлу, если он раскрывает выходы вокруг S-цели.
     *
     * @param extraPoint клетка потенциального дополнительного тайла
     * @param trapSupportTarget ловушечная цель, которой нужна разведка окружения
     * @return бонус за полезное расширение вокруг S-динозавра
     */
    private double trapSupportExpansionScore(Point extraPoint, Optional<Dinosaur> trapSupportTarget) {
        if (trapSupportTarget.isEmpty()) {
            return 0.0;
        }

        int distance = extraPoint.chebyshev(trapSupportTarget.get().position);
        return Math.max(0.0, 220.0 - distance * 70.0);
    }

    /**
     * Размещает вытянутый тайл на карте.
     *
     * @param player игрок, выполняющий разведку
     * @param tile физический тайл
     * @param placement координата размещения
     * @param automaticExpansion true для автоматического дополнительного тайла
     * @param rotationQuarterTurns поворот тайла по часовой стрелке
     * @return размещённый тайл или null, если выкладка не удалась
     */
    private Tile placeDrawnTile(
            PlayerState player,
            Tile tile,
            Point placement,
            boolean automaticExpansion,
            int rotationQuarterTurns
    ) {
        tile.place(placement, rotationQuarterTurns);

        boolean placed = automaticExpansion
                ? simulation.map.placeExpansionTile(placement, tile)
                : simulation.map.placeTile(placement, tile);

        if (!placed) return null;

        if (!automaticExpansion) {
            player.setPosition(RangerRole.SCOUT, scoutPositionAfterExploration(player, tile, placement));
        }

        if (automaticExpansion && tile.hasSpawn() && !tile.isSpawnUsed()) {
            dinosaurAction.spawnDinosaur(tile.spawnSpecies(), placement);
            tile.markSpawnUsed();
        }

        return tile;
    }

    /**
     * Выбирает позицию фигурки разведчика после открытия основного тайла.
     *
     * Разведчик летает на любое расстояние и может зависнуть над любым открытым
     * участком, включая горы и озёра. Его позиция больше не ограничивает выбор
     * следующего фронтира, но визуально показывает, где он только что разведал карту.
     *
     * @param player игрок, чей разведчик перемещается
     * @param tile открытый тайл
     * @param placement клетка открытого тайла
     * @return новая позиция разведчика
     */
    private Point scoutPositionAfterExploration(PlayerState player, Tile tile, Point placement) {
        return placement;
    }

    /**
     * Возвращает виды из задания игрока, которых ещё нет на карте и которые не пойманы.
     *
     * @param player игрок, чьё задание анализируется
     * @return набор пока ненайденных видов
     */
    private Set<Species> missingNeededSpecies(PlayerState player) {
        EnumSet<Species> result = EnumSet.noneOf(Species.class);
        result.addAll(player.task);
        result.removeAll(player.captured);

        for (var dinosaur : simulation.dinosaurs) {
            if (!dinosaur.captured && !dinosaur.removed && player.needs(dinosaur.species)) {
                result.remove(dinosaur.species);
            }
        }

        return result;
    }
}
