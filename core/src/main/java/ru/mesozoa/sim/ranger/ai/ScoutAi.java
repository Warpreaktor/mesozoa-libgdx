package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI-оценка полезности разведчика для очередной активации игрока.
 *
 * Разведчик оценивает только пользу исследования новых тайлов. Он не знает,
 * как работают ловушки инженера, охота охотника или маршруты водителя.
 */
public class ScoutAi {

    private static final double SCORE_IMPOSSIBLE = -100.0;
    private static final double SCORE_FIRST_EXPLORATION = 100.0;
    private static final double SCORE_FULL_SEARCH_PRIORITY = 90.0;
    /** Вес разведки вокруг видимого хищника, которому не хватает биомов для нормальной засады. */
    private static final double SCORE_HUNT_BIOME_SUPPORT = 84.0;

    /** Вес разведки вокруг видимой S-цели, для которой пока нет хорошего ловушечного маршрута. */
    private static final double SCORE_TRAP_BIOME_SUPPORT = 82.0;
    /** Вес разведки, когда все оставшиеся цели уже видны и новые тайлы не приближают победу. */
    private static final double SCORE_ALL_TARGETS_VISIBLE = -10.0;
    private static final double MIXED_SEARCH_BASE_SCORE = 15.0;
    private static final double MIXED_SEARCH_MISSING_RATIO_WEIGHT = 75.0;

    private final GameSimulation simulation;
    private final HunterAi hunterAi;

    public ScoutAi(GameSimulation gameSimulation) {
        this.simulation = gameSimulation;
        this.hunterAi = new HunterAi(gameSimulation);
    }

    /**
     * Рассчитывает вес активации разведчика для текущего игрока.
     *
     * Метод последовательно проверяет ключевые игровые ситуации:
     * невозможность разведки, завершённое задание, первый ход разведки,
     * наличие или отсутствие обнаруженных целей и долю ещё не найденных целей.
     *
     * @param player игрок, для которого оценивается полезность разведчика
     * @return оценка полезности разведчика и причина этой оценки
     */
    public AiScore scoreScout(PlayerState player) {
        Set<Species> remainingNeededSpecies = remainingNeededSpecies(player);
        Set<Species> visibleNeededSpecies = visibleNeededSpecies(player, remainingNeededSpecies);
        Set<Species> missingNeededSpecies = missingNeededSpecies(remainingNeededSpecies, visibleNeededSpecies);

        if (isMainTileBagEmpty()) {
            return new AiScore(
                    SCORE_IMPOSSIBLE,
                    "мешочек основных тайлов пуст, разведка невозможна"
            );
        }

        if (hasNoRemainingNeededSpecies(remainingNeededSpecies)) {
            return new AiScore(
                    SCORE_IMPOSSIBLE,
                    "все динозавры из задания уже пойманы, разведчик не нужен"
            );
        }

        if (isFirstExplorationSituation()) {
            return new AiScore(
                    SCORE_FIRST_EXPLORATION,
                    "первый ход разведки: на столе только база, нужно открыть остров"
            );
        }

        if (areAllRemainingNeededSpeciesVisible(missingNeededSpecies)) {
            Optional<Dinosaur> huntTargetNeedingBiomes = hunterAi.bestVisibleHuntTargetNeedingScoutSupport(player);
            if (huntTargetNeedingBiomes.isPresent()) {
                Dinosaur dinosaur = huntTargetNeedingBiomes.get();
                return new AiScore(
                        SCORE_HUNT_BIOME_SUPPORT,
                        "все цели уже видны, но для охоты на " + dinosaur.displayName
                                + " нужны открытые биомы вокруг хищника"
                );
            }

            Optional<Dinosaur> trapTargetNeedingBiomes = bestVisibleTrapTargetNeedingScoutSupport(player);
            if (trapTargetNeedingBiomes.isPresent()) {
                Dinosaur dinosaur = trapTargetNeedingBiomes.get();
                return new AiScore(
                        SCORE_TRAP_BIOME_SUPPORT,
                        "все цели уже видны, но для ловушек на " + dinosaur.displayName
                                + " нужно раскрыть биомы вокруг S-цели"
                );
            }

            return new AiScore(
                    SCORE_ALL_TARGETS_VISIBLE,
                    "все оставшиеся цели уже обнаружены: "
                            + speciesNames(visibleNeededSpecies)
                            + ", разведка сейчас только тратит активацию"
            );
        }

        if (hasNoVisibleRemainingNeededSpecies(visibleNeededSpecies)) {
            return new AiScore(
                    SCORE_FULL_SEARCH_PRIORITY,
                    "на карте нет ни одной оставшейся цели, нужно искать: "
                            + speciesNames(missingNeededSpecies)
            );
        }

        double score = calculateMixedSearchScore(remainingNeededSpecies, missingNeededSpecies);

        return new AiScore(
                score,
                "часть целей уже видна (" + speciesNames(visibleNeededSpecies)
                        + "), но ещё нужно найти: " + speciesNames(missingNeededSpecies)
                        + "; осталось найти " + missingNeededSpecies.size()
                        + " из " + remainingNeededSpecies.size()
        );
    }


    /**
     * Выбирает видимую S-цель, для которой разведчик должен раскрывать окружение.
     *
     * Для ловушек одной видимости недостаточно: если нужный следующий биом ещё
     * не открыт или вокруг зверя мало рабочих выходов, инженер не сможет
     * построить хороший план. Тогда разведчик помогает именно своему игроку:
     * открывает клетки рядом с целью, чтобы динозавр вышел из тупика и снова
     * начал двигаться по прогнозируемому маршруту.
     *
     * @param player игрок, чьей ловушечной цели нужна разведка
     * @return S-динозавр, вокруг которого стоит раскрывать карту
     */
    public Optional<Dinosaur> bestVisibleTrapTargetNeedingScoutSupport(PlayerState player) {
        return visibleNeededTrapTargets(player)
                .filter(dinosaur -> trapTargetNeedsScoutSupport(dinosaur))
                .max(Comparator
                        .comparingInt((Dinosaur dinosaur) -> trapSupportUrgency(dinosaur))
                        .thenComparingInt(dinosaur -> -dinosaur.position.chebyshev(player.scoutRanger.position())));
    }

    /**
     * Проверяет, что видимый S-динозавр ещё не стал удобной ловушечной целью.
     *
     * @param dinosaur проверяемая S-цель
     * @return true, если вокруг цели стоит продолжать разведку
     */
    private boolean trapTargetNeedsScoutSupport(Dinosaur dinosaur) {
        boolean noPredictableBioTrail = simulation.dinosaurAi.predictDinosaurBioTrailDestination(dinosaur).isEmpty();
        boolean sparseLocalMap = openedNeighborsAround(dinosaur.position) < 6;
        boolean weakTrapCoverage = simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).size() < 2;
        return noPredictableBioTrail || sparseLocalMap || weakTrapCoverage;
    }

    /**
     * Считает срочность разведки вокруг S-цели.
     *
     * @param dinosaur динозавр, вокруг которого оценивается карта
     * @return чем больше число, тем сильнее разведчик должен помогать
     */
    private int trapSupportUrgency(Dinosaur dinosaur) {
        int urgency = 0;
        if (simulation.dinosaurAi.predictDinosaurBioTrailDestination(dinosaur).isEmpty()) urgency += 100;
        urgency += Math.max(0, 8 - openedNeighborsAround(dinosaur.position)) * 10;
        urgency += Math.max(0, 3 - simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).size()) * 15;
        return urgency;
    }

    /** Возвращает поток видимых непойманных S-целей игрока. */
    private java.util.stream.Stream<Dinosaur> visibleNeededTrapTargets(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.captureMethod == CaptureMethod.TRAP);
    }

    /** Считает количество открытых клеток вокруг указанной позиции. */
    private int openedNeighborsAround(ru.mesozoa.sim.model.Point point) {
        int count = 0;
        for (ru.mesozoa.sim.model.Point neighbor : point.neighbors8()) {
            if (simulation.map.isPlaced(neighbor)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Проверяет, закончились ли основные тайлы исследования.
     *
     * Если основной мешочек пуст, разведчик не может открыть новый участок карты,
     * поэтому его активация считается бесполезной.
     *
     * @return true, если основные тайлы закончились
     */
    private boolean isMainTileBagEmpty() {
        return simulation.tileBag.isEmpty();
    }

    /**
     * Проверяет, остались ли у игрока непойманные виды из задания.
     *
     * Если таких видов нет, игрок уже выполнил свою задачу, а значит разведка
     * больше не приближает его к победе.
     *
     * @param remainingNeededSpecies виды из задания игрока, которые ещё не пойманы
     * @return true, если непойманных нужных видов больше нет
     */
    private boolean hasNoRemainingNeededSpecies(Set<Species> remainingNeededSpecies) {
        return remainingNeededSpecies.isEmpty();
    }

    /**
     * Проверяет ситуацию первого исследования.
     *
     * На старте партии на столе находится только база. В такой ситуации разведчик
     * должен получить максимальный вес, потому что без него игра фактически не начнётся.
     *
     * @return true, если на карте открыт только стартовый тайл базы
     */
    private boolean isFirstExplorationSituation() {
        return simulation.map.openedCount() <= 1;
    }

    /**
     * Проверяет, что все оставшиеся нужные виды уже видны на карте.
     *
     * Если все цели обнаружены, разведка становится низким приоритетом: теперь
     * важнее активировать инженера, охотника или водителя.
     *
     * @param missingNeededSpecies оставшиеся нужные виды, которых ещё нет на карте
     * @return true, если ненайденных нужных видов не осталось
     */
    private boolean areAllRemainingNeededSpeciesVisible(Set<Species> missingNeededSpecies) {
        return missingNeededSpecies.isEmpty();
    }

    /**
     * Проверяет, что на карте нет ни одной оставшейся нужной цели.
     *
     * В этой ситуации разведчик получает высокий вес: другим специалистам пока
     * почти нечего делать, потому что цель ещё не обнаружена.
     *
     * @param visibleNeededSpecies оставшиеся нужные виды, которые уже видны на карте
     * @return true, если на карте нет ни одного нужного непойманного вида
     */
    private boolean hasNoVisibleRemainingNeededSpecies(Set<Species> visibleNeededSpecies) {
        return visibleNeededSpecies.isEmpty();
    }

    /**
     * Рассчитывает вес разведки в смешанной ситуации.
     *
     * Смешанная ситуация означает, что часть оставшихся целей уже видна на карте,
     * а часть всё ещё нужно найти. Чем выше доля ненайденных целей, тем выше вес
     * разведчика.
     *
     * @param remainingNeededSpecies все оставшиеся непойманные виды из задания игрока
     * @param missingNeededSpecies оставшиеся нужные виды, которых ещё нет на карте
     * @return вес разведки в диапазоне от 15 до 90
     */
    private double calculateMixedSearchScore(
            Set<Species> remainingNeededSpecies,
            Set<Species> missingNeededSpecies
    ) {
        double missingRatio = (double) missingNeededSpecies.size() / remainingNeededSpecies.size();
        return MIXED_SEARCH_BASE_SCORE + MIXED_SEARCH_MISSING_RATIO_WEIGHT * missingRatio;
    }

    /**
     * Возвращает виды из задания игрока, которые ещё не пойманы.
     *
     * Пойманные виды исключаются из оценки разведчика: если цель уже закрыта,
     * искать её повторно не нужно. Вот до чего дошли, даже AI приходится объяснять,
     * что пойманный динозавр уже пойман.
     *
     * @param player игрок, чьё задание анализируется
     * @return набор непойманных видов из задания игрока
     */
    private Set<Species> remainingNeededSpecies(PlayerState player) {
        EnumSet<Species> result = EnumSet.noneOf(Species.class);
        result.addAll(player.task);
        result.removeAll(player.captured);
        return result;
    }

    /**
     * Возвращает оставшиеся нужные виды, которые уже представлены на карте живыми динозаврами.
     *
     * Учитываются только динозавры, которые не пойманы и не удалены с карты.
     * Способ поимки не важен: разведчику нужно понимать только то, видна ли цель.
     *
     * @param player игрок, для которого проверяются видимые цели
     * @param remainingNeededSpecies непойманные виды из задания игрока
     * @return набор оставшихся нужных видов, уже обнаруженных на карте
     */
    private Set<Species> visibleNeededSpecies(
            PlayerState player,
            Set<Species> remainingNeededSpecies
    ) {
        EnumSet<Species> result = EnumSet.noneOf(Species.class);

        for (Dinosaur dinosaur : simulation.dinosaurs) {
            if (isVisibleNeededDinosaur(dinosaur, player, remainingNeededSpecies)) {
                result.add(dinosaur.species);
            }
        }

        return result;
    }

    /**
     * Проверяет, является ли конкретный динозавр видимой нужной целью игрока.
     *
     * Динозавр считается подходящим, если он живёт на карте, ещё не пойман,
     * не удалён и относится к одному из оставшихся видов задания.
     *
     * @param dinosaur динозавр, которого нужно проверить
     * @param player игрок, для которого проверяется цель
     * @param remainingNeededSpecies непойманные виды из задания игрока
     * @return true, если динозавр является видимой нужной целью
     */
    private boolean isVisibleNeededDinosaur(
            Dinosaur dinosaur,
            PlayerState player,
            Set<Species> remainingNeededSpecies
    ) {
        return !dinosaur.captured
                && !dinosaur.removed
                && player.needs(dinosaur.species)
                && remainingNeededSpecies.contains(dinosaur.species);
    }

    /**
     * Возвращает оставшиеся нужные виды, которых ещё нет на карте.
     *
     * Именно этот набор сильнее всего влияет на вес разведчика: если цель не найдена,
     * её нельзя поймать ни охотником, ни инженером, ни водительскими мечтами о дороге.
     *
     * @param remainingNeededSpecies все оставшиеся непойманные виды из задания игрока
     * @param visibleNeededSpecies оставшиеся нужные виды, которые уже видны на карте
     * @return набор оставшихся нужных видов, которые ещё нужно найти
     */
    private Set<Species> missingNeededSpecies(
            Set<Species> remainingNeededSpecies,
            Set<Species> visibleNeededSpecies
    ) {
        EnumSet<Species> result = EnumSet.noneOf(Species.class);
        result.addAll(remainingNeededSpecies);
        result.removeAll(visibleNeededSpecies);
        return result;
    }

    /**
     * Возвращает читаемый список названий видов для объяснения оценки AI.
     *
     * @param species виды динозавров
     * @return строка с русскими названиями видов или "нет", если набор пуст
     */
    private String speciesNames(Set<Species> species) {
        if (species.isEmpty()) {
            return "нет";
        }

        return species.stream()
                .map(value -> value.displayName)
                .collect(Collectors.joining(", "));
    }
}
