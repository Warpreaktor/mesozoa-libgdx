package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.Biome;
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
 * AI-оценка полезности инженера для очередной активации игрока.
 *
 * Инженер отвечает не только за ловушки. Он создаёт инфраструктуру экспедиции:
 * ставит ловушки на S-динозавров, прокладывает дороги к зонам поимки, строит
 * мосты и заранее соединяет нужные биомы с будущей дорожной сетью.
 */
public class EngineerAi {

    /** Вес невозможного или уже не нужного действия. */
    private static final double SCORE_IMPOSSIBLE = -100.0;

    /** Вес срочной логистики для уже пойманного динозавра без дорожного доступа. */
    private static final double SCORE_CAPTURED_DINO_EXTRACTION = 100.0;

    /** Вес ситуации, когда инженер уже рядом с S-целью и может ставить ловушки. */
    private static final double SCORE_IMMEDIATE_TRAP_PLACEMENT = 100.0;

    /** Вес видимой S-цели, когда инженер ещё должен подойти или подготовить ловушки. */
    private static final double SCORE_VISIBLE_TRAP_TARGET = 90.0;

    /** Вес ситуации, когда текущие ловушки уже заняли весь лимит и не помогают. */
    private static final double SCORE_BAD_TRAP_LAYOUT = 78.0;

    /** Вес поддержки охотника дорогой к зоне охоты или выслеживания. */
    private static final double SCORE_HUNTER_SUPPORT_ROAD = 82.0;

    /** Вес строительства первой дороги из базы после старта разведки. */
    private static final double SCORE_FIRST_BASE_ROAD = 75.0;

    /** Вес подключения уже открытого нужного биома к дорожной сети. */
    private static final double SCORE_NEEDED_BIOME_INFRASTRUCTURE = 62.0;

    /** Вес движения инженера вслед за разведкой без срочной инженерной задачи. */
    private static final double SCORE_FOLLOW_SCOUT = 22.0;

    /** Низкий вес ожидания, если инженер уже стоит рядом с разведчиком. */
    private static final double SCORE_WAIT_NEAR_SCOUT = 8.0;

    /** Дистанция, на которой инженер считается рядом с разведчиком. */
    private static final int NEAR_SCOUT_DISTANCE = 2;

    private final GameSimulation simulation;

    public EngineerAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Рассчитывает вес активации инженера для текущего игрока.
     *
     * Проверки идут от самых срочных и конкретных задач к более мягкой
     * профилактической инфраструктуре. Да, это длинная лестница if'ов, зато
     * каждый пролёт подписан, а не как обычно: "магическое число 37 спасёт игру".
     *
     * @param player игрок, для которого оценивается полезность инженера
     * @return оценка полезности инженера и причина этой оценки
     */
    public AiScore scoreEngineer(PlayerState player) {
        Optional<Dinosaur> capturedWithoutRoad = nearestCapturedNeededDinosaurWithoutDriverAccess(player);
        Optional<Dinosaur> trapTarget = nearestNeededTrapTarget(player);
        Optional<Dinosaur> hunterTargetWithoutRoad = nearestNeededHunterTargetWithoutDriverAccess(player);
        Optional<Biome> unconnectedNeededBiome = nearestUnconnectedNeededBiome(player);

        if (isPlayerTaskComplete(player)) {
            return new AiScore(
                    SCORE_IMPOSSIBLE,
                    "все динозавры из задания уже пойманы, инженер не нужен"
            );
        }

        if (hasCapturedDinosaurWithoutRoadAccess(capturedWithoutRoad)) {
            Dinosaur dinosaur = capturedWithoutRoad.get();
            return new AiScore(
                    SCORE_CAPTURED_DINO_EXTRACTION,
                    "пойманный динозавр ждёт вывоза, но водитель не имеет дороги: "
                            + dinosaur.species.displayName
                            + " на " + dinosaur.position
            );
        }

        if (canPlaceUsefulTrapNow(player, trapTarget)) {
            Dinosaur dinosaur = trapTarget.get();
            return new AiScore(
                    SCORE_IMMEDIATE_TRAP_PLACEMENT,
                    "инженер рядом с ловушечной целью и может ставить ловушки: "
                            + dinosaur.species.displayName
            );
        }

        if (hasVisibleTrapTargetAndAvailableTraps(player, trapTarget)) {
            Dinosaur dinosaur = trapTarget.get();
            int activeTraps = activeTrapCount(player);
            int distance = player.engineer.manhattan(dinosaur.position);
            double score = SCORE_VISIBLE_TRAP_TARGET - Math.min(35.0, distance * 4.0);
            return new AiScore(
                    score,
                    "видимая S-цель для ловушек: "
                            + dinosaur.species.displayName
                            + ", расстояние: " + distance
                            + ", ловушек: " + activeTraps + "/" + simulation.inventoryConfig.maxTrapsPerPlayer
            );
        }

        if (hasBadActiveTrapLayout(player, trapTarget)) {
            Dinosaur dinosaur = trapTarget.get();
            return new AiScore(
                    SCORE_BAD_TRAP_LAYOUT,
                    "лимит ловушек занят, но ловушки не перекрывают путь S-цели: "
                            + dinosaur.species.displayName
            );
        }

        if (hasHunterCaptureZoneWithoutRoadSupport(hunterTargetWithoutRoad)) {
            Dinosaur dinosaur = hunterTargetWithoutRoad.get();
            return new AiScore(
                    SCORE_HUNTER_SUPPORT_ROAD,
                    "есть цель охотника без дорожной поддержки водителя: "
                            + dinosaur.species.displayName
                            + " на " + dinosaur.position
            );
        }

        if (hasNoRoadOutOfBase()) {
            return new AiScore(
                    SCORE_FIRST_BASE_ROAD,
                    "карта уже раскрывается, но из базы ещё не построена ни одна дорога"
            );
        }

        if (hasUnconnectedNeededBiome(unconnectedNeededBiome)) {
            Biome biome = unconnectedNeededBiome.get();
            return new AiScore(
                    SCORE_NEEDED_BIOME_INFRASTRUCTURE,
                    "нужный биом уже открыт, но не подключён к дорожной сети: " + biome.displayName
            );
        }

        if (shouldFollowScoutForFutureConstruction(player)) {
            int distance = player.engineer.manhattan(player.scout);
            double score = SCORE_FOLLOW_SCOUT + Math.min(18.0, distance * 3.0);
            return new AiScore(
                    score,
                    "нет срочной инженерной задачи, инженер подтягивается к разведчику; расстояние: " + distance
            );
        }

        if (isEngineerAlreadyNearScout(player)) {
            return new AiScore(
                    SCORE_WAIT_NEAR_SCOUT,
                    "инженер уже рядом с разведчиком и ждёт понятной задачи"
            );
        }

        return new AiScore(
                5.0,
                "для инженера сейчас нет срочной ловушечной или инфраструктурной задачи"
        );
    }

    /**
     * Проверяет, завершил ли игрок своё задание.
     *
     * Если все нужные виды уже пойманы, инженер не должен отбирать активацию
     * у других ролей. Победную дорогу в закат можно построить после партии.
     *
     * @param player игрок, чей инженер оценивается
     * @return true, если задание игрока полностью выполнено
     */
    private boolean isPlayerTaskComplete(PlayerState player) {
        return player.isComplete();
    }

    /**
     * Проверяет, есть ли уже пойманный нужный динозавр без дорожного доступа.
     *
     * Пойманного динозавра всё ещё нужно вывезти. Если водитель не может доехать
     * до его клетки по дорогам или мостам, инженер получает максимальный приоритет:
     * иначе добыча просто лежит на острове и делает вид, что это склад.
     *
     * @param capturedWithoutRoad ближайший пойманный нужный динозавр без маршрута водителя
     * @return true, если есть срочная задача на дорожный вывоз
     */
    private boolean hasCapturedDinosaurWithoutRoadAccess(Optional<Dinosaur> capturedWithoutRoad) {
        return capturedWithoutRoad.isPresent();
    }

    /**
     * Проверяет, может ли инженер прямо сейчас поставить полезные ловушки.
     *
     * Установка ловушек полезна, если на карте есть нужная S-цель, инженер стоит
     * на её клетке или рядом, а лимит активных ловушек ещё не достигнут.
     *
     * @param player игрок, чей инженер оценивается
     * @param trapTarget ближайшая нужная ловушечная цель
     * @return true, если инженер уже в зоне установки ловушек
     */
    private boolean canPlaceUsefulTrapNow(PlayerState player, Optional<Dinosaur> trapTarget) {
        return trapTarget.isPresent()
                && activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer
                && isAdjacentOrSame(player.engineer, trapTarget.get().position);
    }

    /**
     * Проверяет, есть ли видимая S-цель и свободный лимит ловушек.
     *
     * Даже если инженер ещё далеко, видимая цель с CaptureMethod.TRAP должна
     * заметно поднимать его вес: это его работа, а не охотничья самодеятельность.
     *
     * @param player игрок, чей инженер оценивается
     * @param trapTarget ближайшая нужная ловушечная цель
     * @return true, если есть цель для ловушек и можно выставлять новые ловушки
     */
    private boolean hasVisibleTrapTargetAndAvailableTraps(PlayerState player, Optional<Dinosaur> trapTarget) {
        return trapTarget.isPresent()
                && activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer;
    }

    /**
     * Проверяет, заняты ли ловушки бесполезной раскладкой.
     *
     * Если все ловушки уже активны, но ни одна не стоит на текущей или следующей
     * прогнозной клетке S-динозавра, инженер должен получить вес на перестановку
     * в будущей версии действия. Сейчас это хотя бы не даст AI считать ситуацию нормальной.
     *
     * @param player игрок, чей инженер оценивается
     * @param trapTarget ближайшая нужная ловушечная цель
     * @return true, если ловушки заняли лимит, но не перекрывают цель
     */
    private boolean hasBadActiveTrapLayout(PlayerState player, Optional<Dinosaur> trapTarget) {
        if (trapTarget.isEmpty()) return false;
        if (activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer) return false;

        Dinosaur dinosaur = trapTarget.get();
        Point predicted = predictNextBioStep(dinosaur);

        return player.traps.stream()
                .filter(trap -> trap.active)
                .noneMatch(trap -> trap.position.equals(dinosaur.position)
                        || (predicted != null && trap.position.equals(predicted)));
    }

    /**
     * Проверяет, есть ли видимая цель охотника без дорожной поддержки.
     *
     * Если охотник уже имеет цель для выслеживания или охоты, инженер может быть
     * полезен не меньше охотника: он заранее тянет дорогу к зоне будущей поимки,
     * чтобы водитель потом не изображал бесполезную машинку на базе.
     *
     * @param hunterTargetWithoutRoad ближайшая цель охотника без маршрута водителя
     * @return true, если нужна инфраструктура к зоне работы охотника
     */
    private boolean hasHunterCaptureZoneWithoutRoadSupport(Optional<Dinosaur> hunterTargetWithoutRoad) {
        return hunterTargetWithoutRoad.isPresent();
    }

    /**
     * Проверяет, выведена ли дорожная сеть из базы.
     *
     * База сама по себе не является дорогой. Если карта уже начала раскрываться,
     * но из базы нет ни одной дорожной связи, водитель обречён стоять и грустить.
     *
     * @return true, если после первых открытий из базы ещё нет дороги
     */
    private boolean hasNoRoadOutOfBase() {
        return simulation.map.openedCount() > 1 && !simulation.map.hasRoadOutOfBase();
    }

    /**
     * Проверяет, есть ли открытый нужный биом без дорожной связи с базой.
     *
     * Инженер должен строить инфраструктуру не куда попало, а к биомам, где
     * появляются или ходят нужные игроку динозавры.
     *
     * @param unconnectedNeededBiome ближайший нужный биом без дороги
     * @return true, если такой биом найден
     */
    private boolean hasUnconnectedNeededBiome(Optional<Biome> unconnectedNeededBiome) {
        return unconnectedNeededBiome.isPresent();
    }

    /**
     * Проверяет, стоит ли инженеру подтягиваться к разведчику.
     *
     * Если конкретной стройки или ловушек пока нет, инженер всё равно не обязан
     * сидеть на базе. Разведчик открывает будущие зоны задач, и инженер должен
     * быть достаточно близко, чтобы быстро начать строить дорогу, мост или ловушки.
     *
     * @param player игрок, чей инженер оценивается
     * @return true, если инженер далеко от разведчика и ему полезно приблизиться
     */
    private boolean shouldFollowScoutForFutureConstruction(PlayerState player) {
        if (player.engineer.manhattan(player.scout) <= NEAR_SCOUT_DISTANCE) {
            return false;
        }

        return canEngineerMakeProgressToward(player, player.scout);
    }

    /**
     * Проверяет, что инженер уже находится рядом с разведчиком.
     *
     * Это низкоприоритетная ситуация ожидания: инженер занял нормальную позицию,
     * но пока нет точной задачи на ловушки, мост или дорогу.
     *
     * @param player игрок, чей инженер оценивается
     * @return true, если инженер рядом с разведчиком
     */
    private boolean isEngineerAlreadyNearScout(PlayerState player) {
        return player.engineer.manhattan(player.scout) <= NEAR_SCOUT_DISTANCE;
    }

    /**
     * Ищет ближайшего пойманного нужного динозавра, к которому водитель не имеет пути.
     *
     * Пока модель вывоза упрощена, пойманный динозавр остаётся на своей клетке.
     * Эту позицию можно использовать как цель дорожной инфраструктуры.
     *
     * @param player игрок, для которого ищется задача вывоза
     * @return ближайший пойманный нужный динозавр без водительского маршрута
     */
    private Optional<Dinosaur> nearestCapturedNeededDinosaurWithoutDriverAccess(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> dinosaur.captured)
                .filter(dinosaur -> player.captured.contains(dinosaur.species))
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineer.manhattan(dinosaur.position)));
    }

    /**
     * Ищет ближайшую непойманную нужную цель, которая ловится ловушками.
     *
     * @param player игрок, для которого ищется S-цель
     * @return ближайший живой нужный динозавр с CaptureMethod.TRAP
     */
    private Optional<Dinosaur> nearestNeededTrapTarget(PlayerState player) {
        return nearestNeededDinosaur(player, player.engineer, CaptureMethod.TRAP);
    }

    /**
     * Ищет ближайшую цель охотника, к которой водитель пока не имеет дороги.
     *
     * Это не значит, что инженер должен бросать всё и строить туда магистраль.
     * Но если охотник уже работает в этом районе, отсутствие дороги становится
     * заметной логистической проблемой.
     *
     * @param player игрок, для которого ищется зона поддержки охотника
     * @return ближайшая TRACKING/HUNT-цель без водительского маршрута
     */
    private Optional<Dinosaur> nearestNeededHunterTargetWithoutDriverAccess(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> dinosaur.species.captureMethod == CaptureMethod.TRACKING
                        || dinosaur.species.captureMethod == CaptureMethod.HUNT)
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineer.manhattan(dinosaur.position)));
    }

    /**
     * Ищет открытый биом, связанный с оставшимися целями игрока, но не подключённый к дороге.
     *
     * Для каждого непойманного вида учитывается его spawnBiome и элементы bioTrail.
     * Если такой биом уже открыт, но водитель не может до него доехать, инженер
     * получает инфраструктурную задачу на будущее.
     *
     * @param player игрок, для которого анализируются нужные биомы
     * @return ближайший нужный биом без водительского маршрута
     */
    private Optional<Biome> nearestUnconnectedNeededBiome(PlayerState player) {
        Set<Biome> neededBiomes = remainingNeededBiomes(player);

        return simulation.map.entries().stream()
                .filter(entry -> neededBiomes.contains(entry.getValue().biome))
                .filter(entry -> !simulation.map.hasDriverPath(simulation.map.base, entry.getKey()))
                .filter(entry -> canEngineerMakeProgressToward(player, entry.getKey()))
                .min(Comparator.comparingInt(entry -> player.engineer.manhattan(entry.getKey())))
                .map(entry -> entry.getValue().biome);
    }

    /**
     * Проверяет, может ли инженер реально продвинуться к указанной цели.
     *
     * Метод использует общую навигацию наземных рейнджеров.
     * Если цель недостижима напрямую, карта всё равно может вернуть шаг к
     * ближайшей достижимой клетке, откуда инженер сможет построить мост или
     * продолжить дорогу.
     *
     * @param player игрок, чей инженер оценивается
     * @param target цель инфраструктурного движения
     * @return true, если инженер может сделать хотя бы один полезный шаг
     */
    private boolean canEngineerMakeProgressToward(PlayerState player, Point target) {
        Point next = simulation.map.stepGroundRangerToward(player.engineer, target);
        return !next.equals(player.engineer) || canEngineerBuildNearCurrentPositionToward(player, target);
    }

    /**
     * Проверяет, может ли инженер не двигаясь выполнить стройку рядом с собой.
     *
     * Это не даёт AI занизить инженера в ситуации, когда путь до цели ещё не
     * существует, но рядом можно поставить мост или проложить следующую дорогу.
     *
     * @param player игрок, чей инженер оценивается
     * @param target цель, в сторону которой нужна инфраструктура
     * @return true, если рядом есть полезная стройка
     */
    private boolean canEngineerBuildNearCurrentPositionToward(PlayerState player, Point target) {
        Point direct = player.engineer.stepToward(target);
        if (simulation.map.canBuildBridgeFrom(player.engineer, direct)) return true;

        return player.engineer.neighbors4().stream()
                .anyMatch(point -> simulation.map.canBuildRoadBetween(player.engineer, point)
                        || simulation.map.canBuildBridgeFrom(player.engineer, point));
    }

    /**
     * Собирает биомы, связанные с оставшимися непойманными целями игрока.
     *
     * @param player игрок, чьё задание анализируется
     * @return набор биомов спауна и био-троп оставшихся целей
     */
    private Set<Biome> remainingNeededBiomes(PlayerState player) {
        EnumSet<Biome> result = EnumSet.noneOf(Biome.class);

        for (Species species : player.task) {
            if (player.captured.contains(species)) continue;
            result.add(species.spawnBiome);
            result.addAll(species.bioTrail);
        }

        return result;
    }

    /**
     * Ищет ближайшего нужного динозавра с одним из указанных способов поимки.
     *
     * @param player игрок, для которого ищется цель
     * @param from позиция, от которой измеряется расстояние
     * @param methods допустимые способы поимки
     * @return ближайший подходящий динозавр
     */
    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        EnumSet<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        allowedMethods.addAll(List.of(methods));

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.manhattan(from)));
    }

    /**
     * Считает активные ловушки игрока.
     *
     * @param player игрок, чьи ловушки проверяются
     * @return количество активных ловушек на карте
     */
    private int activeTrapCount(PlayerState player) {
        return (int) player.traps.stream()
                .filter(trap -> trap.active)
                .count();
    }

    /**
     * Проверяет, соседствуют ли две клетки с учётом диагоналей.
     *
     * Ловушки инженер может ставить в свою клетку и в любые соседние клетки,
     * включая диагональные.
     *
     * @param a первая клетка
     * @param b вторая клетка
     * @return true, если клетки совпадают или соседствуют
     */
    private boolean isAdjacentOrSame(Point a, Point b) {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        return dx <= 1 && dy <= 1;
    }

    /**
     * Прогнозирует следующий шаг динозавра по его био-тропе.
     *
     * @param dinosaur динозавр, для которого строится прогноз
     * @return следующая клетка или null, если цель био-тропы пока не открыта
     */
    private Point predictNextBioStep(Dinosaur dinosaur) {
        Point target = predictNextBioTarget(dinosaur);
        if (target == null) return null;
        return simulation.stepTowardPlaced(dinosaur.position, target);
    }

    /**
     * Находит следующий биом био-тропы динозавра на открытой карте.
     *
     * @param dinosaur динозавр, для которого ищется цель
     * @return ближайшая клетка следующего биома или null, если такого биома нет
     */
    private Point predictNextBioTarget(Dinosaur dinosaur) {
        Biome nextBiome = dinosaur.species.bioTrail.get((dinosaur.trailIndex + 1) % dinosaur.species.bioTrail.size());
        return simulation.map.nearestPlacedBiome(dinosaur.position, nextBiome);
    }
}
