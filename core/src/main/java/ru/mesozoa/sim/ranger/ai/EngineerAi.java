package ru.mesozoa.sim.ranger.ai;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.simulation.GameMap;
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

    /** Вес срочной логистики для динозавра в ловушке без дорожного доступа. */
    private static final double SCORE_CAPTURED_DINO_EXTRACTION = 100.0;

    /** Вес ситуации, когда инженер уже рядом с S-целью и может ставить ловушки. */
    private static final double SCORE_IMMEDIATE_TRAP_PLACEMENT = 100.0;

    /** Вес видимой S-цели, когда инженер ещё должен подойти или подготовить ловушки. */
    private static final double SCORE_VISIBLE_TRAP_TARGET = 90.0;

    /** Вес ситуации, когда текущие ловушки уже заняли весь лимит и не помогают. */
    private static final double SCORE_BAD_TRAP_LAYOUT = 78.0;

    /** Вес поддержки уже начатого выслеживания дорогой для будущего вывоза. */
    private static final double SCORE_ACTIVE_TRACKING_ROAD_SUPPORT = 38.0;

    /** Вес строительства первой дороги из базы после старта разведки. */
    private static final double SCORE_FIRST_BASE_ROAD = 12.0;

    /** Вес движения инженера вслед за разведкой без срочной инженерной задачи. */
    private static final double SCORE_FOLLOW_SCOUT = -5.0;

    /** Низкий вес ожидания, если инженер уже стоит рядом с разведчиком. */
    private static final double SCORE_WAIT_NEAR_SCOUT = -10.0;

    /** Дистанция, на которой инженер считается рядом с разведчиком. */
    private static final int NEAR_SCOUT_DISTANCE = 2;

    private final GameSimulation simulation;

    public EngineerAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Рассчитывает вес активации инженера для текущего игрока.
     *
     * Инженер получает положительную оценку только тогда, когда за этой
     * оценкой стоит исполнимый план: поставить ловушку, построить следующий
     * шаг дорожной сети или хотя бы реально сдвинуться к точке будущей работы.
     * Если исполнимого действия нет, планировщик должен пропустить активацию,
     * а не посылать инженера постоять с умным видом.
     *
     * @param player игрок, для которого оценивается полезность инженера
     * @return оценка полезности инженера и причина этой оценки
     */
    public AiScore scoreEngineer(PlayerState player) {
        return bestEngineerPlan(player)
                .map(plan -> new AiScore(plan.score(), plan.reason()))
                .orElseGet(() -> new AiScore(
                        SCORE_IMPOSSIBLE,
                        "у инженера нет исполнимой ловушечной, дорожной или мостовой задачи"
                ));
    }

    /**
     * Возвращает целевую клетку для выбранного плана инженера.
     *
     * Этот метод намеренно использует тот же расчёт, что и scoreEngineer():
     * планировщик и исполнитель больше не должны жить в разных реальностях,
     * где один выбрал инженера, а второй не понял, зачем его разбудили.
     *
     * @param player игрок, чей инженер планируется
     * @return целевая клетка исполнимого инженерного плана
     */
    public Optional<Point> chooseEngineerTarget(PlayerState player) {
        return bestEngineerPlan(player).map(EngineerPlanCandidate::target);
    }

    /**
     * Выбирает лучший реально исполнимый инженерный план.
     *
     * @param player игрок, чей инженер анализируется
     * @return план с оценкой, причиной и целью или пустой результат
     */
    private Optional<EngineerPlanCandidate> bestEngineerPlan(PlayerState player) {
        Optional<Dinosaur> capturedWithoutRoad = nearestValuableDinosaurWithoutDriverAccess(player);
        if (capturedWithoutRoad.isPresent()) {
            Dinosaur dinosaur = capturedWithoutRoad.get();
            if (canExecuteInfrastructurePlan(player, dinosaur.position)) {
                return Optional.of(new EngineerPlanCandidate(
                        SCORE_CAPTURED_DINO_EXTRACTION,
                        "динозавр в ловушке ждёт вывоза, но водитель не имеет дороги: "
                                + dinosaur.displayName + " на " + dinosaur.position,
                        dinosaur.position
                ));
            }
        }

        Optional<Dinosaur> trapTarget = nearestNeededTrapTarget(player);
        Optional<Point> trapAmbushPoint = bestTrapAmbushPoint(player, player.engineerRanger.position());
        if (trapTarget.isPresent()
                && trapAmbushPoint.isPresent()
                && activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer
                && canExecuteTrapPlan(player, trapAmbushPoint.get())) {
            Dinosaur dinosaur = trapTarget.get();
            Point trapPoint = trapAmbushPoint.get();

            if (isAdjacentOrSame(player.engineerRanger.position(), trapPoint)) {
                return Optional.of(new EngineerPlanCandidate(
                        SCORE_IMMEDIATE_TRAP_PLACEMENT,
                        "инженер рядом с легальной клеткой ловушки для цели: " + dinosaur.displayName,
                        trapPoint
                ));
            }

            int activeTraps = activeTrapCount(player);
            int distance = player.engineerRanger.position().chebyshev(trapPoint);
            double score = SCORE_VISIBLE_TRAP_TARGET - Math.min(35.0, distance * 4.0);
            return Optional.of(new EngineerPlanCandidate(
                    score,
                    "видимая S-цель для ловушек: "
                            + dinosaur.displayName
                            + ", расстояние до клетки засады: " + distance
                            + ", ловушек: " + activeTraps + "/" + simulation.inventoryConfig.maxTrapsPerPlayer,
                    trapPoint
            ));
        }

        Optional<Point> staleTrapTarget = staleTrapRecoveryTarget(player);
        if (staleTrapTarget.isPresent() && canExecuteTrapRecoveryPlan(player, staleTrapTarget.get())) {
            return Optional.of(new EngineerPlanCandidate(
                    SCORE_BAD_TRAP_LAYOUT,
                    "лимит ловушек занят, но раскладка не перекрывает актуальную S-цель; инженер переставляет ловушку",
                    staleTrapTarget.get()
            ));
        }

        Optional<Dinosaur> activeTrackingTargetWithoutRoad = activeTrackingTargetWithoutDriverAccess(player);
        if (activeTrackingTargetWithoutRoad.isPresent()) {
            Dinosaur dinosaur = activeTrackingTargetWithoutRoad.get();
            if (canExecuteInfrastructurePlan(player, dinosaur.position)) {
                return Optional.of(new EngineerPlanCandidate(
                        SCORE_ACTIVE_TRACKING_ROAD_SUPPORT,
                        "охотник уже ведёт след, инженер готовит дорогу для будущего вывоза: "
                                + dinosaur.displayName + " на " + dinosaur.position,
                        dinosaur.position
                ));
            }
        }

        Optional<Point> firstBaseRoadTarget = firstBaseRoadTarget(player);
        if (firstBaseRoadTarget.isPresent()
                && canExecuteInfrastructurePlan(player, firstBaseRoadTarget.get())) {
            return Optional.of(new EngineerPlanCandidate(
                    SCORE_FIRST_BASE_ROAD,
                    "карта уже раскрывается, но из базы ещё не построена ни одна дорога",
                    firstBaseRoadTarget.get()
            ));
        }


        if (shouldFollowScoutForFutureConstruction(player)) {
            int distance = player.engineerRanger.position().chebyshev(player.scoutRanger.position());
            double score = SCORE_FOLLOW_SCOUT + Math.min(18.0, distance * 3.0);
            return Optional.of(new EngineerPlanCandidate(
                    score,
                    "нет срочной инженерной задачи, инженер подтягивается к разведчику; расстояние: " + distance,
                    player.scoutRanger.position()
            ));
        }

        return Optional.empty();
    }

    /**
     * Проверяет, может ли инженер исполнить инфраструктурный план: построить
     * следующий шаг сети уже сейчас или сдвинуться к рабочей клетке этого шага.
     *
     * @param player игрок, чей инженер проверяется
     * @param target цель дорожной сети
     * @return true, если активация инженера приведёт к стройке или движению к ней
     */
    private boolean canExecuteInfrastructurePlan(PlayerState player, Point target) {
        Optional<GameMap.DriverNetworkBuildStep> step = simulation.map.bestDriverNetworkBuildStepToward(target);
        if (step.isEmpty()) return false;

        Point workerPosition = step.get().workerPosition();
        Point engineerPosition = player.engineerRanger.position();
        if (engineerPosition.equals(workerPosition)) {
            return simulation.map.canBuildDriverNetworkStep(step.get());
        }

        Point next = simulation.map.stepGroundRangerToward(engineerPosition, workerPosition);
        return !next.equals(engineerPosition);
    }

    /**
     * Проверяет, может ли инженер поставить ловушку или реально приблизиться к
     * клетке, куда он сможет её поставить позже.
     *
     * @param player игрок, чей инженер проверяется
     * @param trapPoint клетка будущей ловушки
     * @return true, если активация не будет пустой
     */
    private boolean canExecuteTrapPlan(PlayerState player, Point trapPoint) {
        Point engineerPosition = player.engineerRanger.position();
        if (isAdjacentOrSame(engineerPosition, trapPoint)) {
            return true;
        }

        Point next = simulation.map.stepGroundRangerToward(engineerPosition, trapPoint);
        return !next.equals(engineerPosition);
    }

    /**
     * Проверяет, может ли инженер снять устаревшую ловушку сейчас или реально
     * приблизиться к ней. Перестановка нужна, когда все ловушки стоят на карте,
     * но ни одна не перекрывает прогнозный маршрут нужного S-динозавра.
     *
     * @param player игрок, чей инженер проверяет старую ловушку
     * @param trapPoint клетка ловушки, которую надо вернуть в инвентарь
     * @return true, если активация приведёт к снятию ловушки или движению к ней
     */
    private boolean canExecuteTrapRecoveryPlan(PlayerState player, Point trapPoint) {
        Point engineerPosition = player.engineerRanger.position();
        if (isAdjacentOrSame(engineerPosition, trapPoint)) {
            return true;
        }

        Point next = simulation.map.stepGroundRangerToward(engineerPosition, trapPoint);
        return !next.equals(engineerPosition);
    }

    /**
     * Цель для первой дороги из базы.
     *
     * Первая дорога строится только если рядом с базой уже есть открытый тайл,
     * на который реально можно протянуть дорожную связь.
     *
     * @param player игрок, для которого проверяется старт дорожной сети
     * @return сосед базы, к которому можно строить первую дорогу
     */
    private Optional<Point> firstBaseRoadTarget(PlayerState player) {
        if (!hasNoRoadOutOfBase(player)) return Optional.empty();

        return simulation.map.base.neighbors8().stream()
                .filter(point -> simulation.map.canBuildRoadBetween(simulation.map.base, point))
                .min(Comparator.comparingInt(point -> player.engineerRanger.position().chebyshev(point)));
    }

    /** Исполнимый инженерный план с оценкой, причиной и целевой клеткой. */
    private record EngineerPlanCandidate(double score, String reason, Point target) {
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
     * Проверяет, есть ли нужный динозавр в ловушке без дорожного доступа.
     *
     * Динозавра в ловушке ещё нужно вывезти. Если водитель не может доехать
     * до его клетки по дорогам или мостам, инженер получает максимальный приоритет:
     * иначе добыча просто лежит на острове и делает вид, что это склад.
     *
     * @param capturedWithoutRoad ближайший динозавр в ловушке без маршрута водителя
     * @return true, если есть срочная задача на дорожный вывоз
     */
    private boolean hasCapturedDinosaurWithoutRoadAccess(Optional<Dinosaur> capturedWithoutRoad) {
        return capturedWithoutRoad.isPresent();
    }

    /**
     * Проверяет, может ли инженер прямо сейчас поставить полезные ловушки.
     * Установка ловушек полезна, если на карте есть нужная S-цель, инженер стоит
     * рядом с прогнозной клеткой её прихода, а лимит активных ловушек ещё не достигнут.
     *
     * @param player игрок, чей инженер оценивается
     * @param trapTarget ближайшая нужная ловушечная цель
     * @return true, если инженер уже в зоне установки ловушек
     */
    private boolean canPlaceUsefulTrapNow(PlayerState player, Optional<Point> trapAmbushPoint) {
        return trapAmbushPoint.isPresent()
                && activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer
                && isAdjacentOrSame(player.engineerRanger.position(), trapAmbushPoint.get());
    }

    /**
     * Проверяет, есть ли видимая S-цель и свободный лимит ловушек.
     * Даже если инженер ещё далеко, видимая цель с CaptureMethod.TRAP должна
     * заметно поднимать его вес: это его работа, а не охотничья самодеятельность.
     *
     * @param player игрок, чей инженер оценивается
     * @param trapTarget ближайшая нужная ловушечная цель
     * @return true, если есть цель для ловушек и можно выставлять новые ловушки
     */
    private boolean hasVisibleTrapTargetAndAvailableTraps(
            PlayerState player,
            Optional<Dinosaur> trapTarget,
            Optional<Point> trapAmbushPoint
    ) {
        return trapTarget.isPresent()
                && trapAmbushPoint.isPresent()
                && activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer;
    }

    /**
     * Проверяет, заняты ли ловушки бесполезной раскладкой.
     * Если все ловушки уже активны, но ни одна не стоит на прогнозной клетке
     * прихода S-динозавра, инженер должен получить вес на перестановку
     * в будущей версии действия. Сейчас это хотя бы не даст AI считать ситуацию нормальной.
     *
     * @param player игрок, чей инженер оценивается
     * @param trapTarget ближайшая нужная ловушечная цель
     * @return true, если ловушки заняли лимит, но не перекрывают цель
     */
    private boolean hasBadActiveTrapLayout(PlayerState player, Optional<Dinosaur> trapTarget) {
        return false;
    }

    /**
     * Проверяет, есть ли видимая цель охотника без дорожной поддержки.
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
     * База сама по себе не является дорогой. Если карта уже начала раскрываться,
     * но из базы нет ни одной дорожной связи, водитель обречён стоять и грустить.
     *
     * @return true, если после первых открытий из базы ещё нет дороги
     */
    private boolean hasNoRoadOutOfBase(PlayerState player) {
        return simulation.map.openedCount() > 1
                && !simulation.map.hasRoadOutOfBase()
                && hasRemainingRoadRelevantGoal(player);
    }

    /**
     * Проверяет, остались ли у игрока цели, для которых дорожная сеть реально нужна.
     * HUNT-хищники сюда не входят: охотник сам идёт в засаду, а водитель после
     * транквилизатора не требуется. Иначе инженер опять будет строить автобан к
     * кусту, где человек просто лежит с приманкой. Удобно, но бессмысленно.
     *
     * @param player игрок, чьи оставшиеся цели анализируются
     * @return true, если есть TRAP/TRACKING-цели или динозавр в ловушке без вывоза
     */
    private boolean hasRemainingRoadRelevantGoal(PlayerState player) {
        if (nearestValuableDinosaurWithoutDriverAccess(player).isPresent()) {
            return true;
        }

        return simulation.dinosaurs.stream()
                .filter(d -> simulation.isWorthCapturing(player, d))
                .anyMatch(d -> d.captureMethod != CaptureMethod.HUNT);
    }

    /**
     * Проверяет, есть ли открытый нужный биом без дорожной связи с базой.
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
     * Если конкретной стройки или ловушек пока нет, инженер всё равно не обязан
     * сидеть на базе. Разведчик открывает будущие зоны задач, и инженер должен
     * быть достаточно близко, чтобы быстро начать строить дорогу, мост или ловушки.
     *
     * @param player игрок, чей инженер оценивается
     * @return true, если инженер далеко от разведчика и ему полезно приблизиться
     */
    private boolean shouldFollowScoutForFutureConstruction(PlayerState player) {
        return false;
    }

    /**
     * Проверяет, что инженер уже находится рядом с разведчиком.
     * Это низкоприоритетная ситуация ожидания: инженер занял нормальную позицию,
     * но пока нет точной задачи на ловушки, мост или дорогу.
     *
     * @param player игрок, чей инженер оценивается
     * @return true, если инженер рядом с разведчиком
     */
    private boolean isEngineerAlreadyNearScout(PlayerState player) {
        return player.engineerRanger.position().chebyshev(player.scoutRanger.position()) <= NEAR_SCOUT_DISTANCE;
    }

    /**
     * Ищет ближайшего нужного динозавра в ловушке, к которому водитель не имеет пути.
     * Пока водитель не вывез добычу, динозавр и ловушка остаются на карте.
     * Эту позицию можно использовать как цель дорожной инфраструктуры.
     *
     * @param player игрок, для которого ищется задача вывоза
     * @return ближайший динозавр в ловушке без водительского маршрута
     */
    private Optional<Dinosaur> nearestValuableDinosaurWithoutDriverAccess(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(simulation::isAwaitingPickup)
                .filter(dinosaur -> !simulation.canDriverExtractTrappedDinosaur(player, dinosaur))
                .min(Comparator
                        .comparingInt((Dinosaur dinosaur) -> player.needs(dinosaur.species) ? 0 : 1)
                        .thenComparing(Comparator.comparingInt((Dinosaur dinosaur) -> simulation.capturePointsForPlayer(player, dinosaur)).reversed())
                        .thenComparingInt(dinosaur -> player.engineerRanger.position().chebyshev(dinosaur.position)));
    }

    /**
     * Ищет ближайшую непойманную нужную цель, которая ловится ловушками.
     * @param player игрок, для которого ищется S-цель
     * @return ближайший живой нужный динозавр с CaptureMethod.TRAP
     */
    private Optional<Dinosaur> nearestNeededTrapTarget(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> simulation.isWorthCapturing(player, d))
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .filter(dinosaur -> simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).stream()
                        .anyMatch(point -> isUsableTrapPoint(player, point)))
                .min(Comparator.comparingInt(d -> d.position.chebyshev(player.engineerRanger.position())));
    }

    /**
     * Ищет ближайшую цель охотника, к которой водитель пока не имеет дороги.
     * Это не значит, что инженер должен бросать всё и строить туда магистраль.
     * Но если охотник уже работает в этом районе, отсутствие дороги становится
     * заметной логистической проблемой.
     *
     * @param player игрок, для которого ищется зона поддержки охотника
     * @return ближайшая TRACKING/HUNT-цель без водительского маршрута
     */
    private Optional<Dinosaur> activeTrackingTargetWithoutDriverAccess(PlayerState player) {
        if (player.activeTracking == null) {
            return Optional.empty();
        }

        return simulation.dinosaurs.stream()
                .filter(dinosaur -> dinosaur.id == player.activeTracking.dinosaurId)
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.trapped && !dinosaur.removed)
                .filter(dinosaur -> simulation.isWorthCapturing(player, dinosaur))
                .filter(dinosaur -> dinosaur.captureMethod == CaptureMethod.TRACKING)
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .findFirst();
    }

    /**
     * Ищет открытую клетку нужного биома, которую ещё не связали с дорожной сетью.
     *
     * В отличие от старого метода с Optional<Biome>, этот вариант возвращает
     * конкретную клетку. Планировщику нужна не абстрактная «пойма где-то там»,
     * а координата, к которой инженер сможет строить дорогу или мост.
     *
     * @param player игрок, для которого анализируются нужные биомы
     * @return ближайшая клетка нужного биома без водительского маршрута
     */
    private Optional<Point> nearestUnconnectedNeededBiomePoint(PlayerState player) {
        Set<Biome> neededBiomes = remainingNeededBiomes(player);

        return simulation.map.entries().stream()
                .filter(entry -> neededBiomes.contains(entry.getValue().biome))
                .filter(entry -> !simulation.map.hasDriverPath(simulation.map.base, entry.getKey()))
                .filter(entry -> canExecuteInfrastructurePlan(player, entry.getKey()))
                .min(Comparator.comparingInt(entry -> player.engineerRanger.position().chebyshev(entry.getKey())))
                .map(java.util.Map.Entry::getKey);
    }

    /**
     * Ищет открытый биом, связанный с оставшимися целями игрока, но не подключённый к дороге.
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
                .min(Comparator.comparingInt(entry -> player.engineerRanger.position().chebyshev(entry.getKey())))
                .map(entry -> entry.getValue().biome);
    }

    /**
     * Проверяет, может ли инженер реально продвинуться к указанной цели.
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
        Point next = simulation.map.stepGroundRangerToward(player.engineerRanger.position(), target);
        return !next.equals(player.engineerRanger.position()) || canEngineerBuildNearCurrentPositionToward(player, target);
    }

    /**
     * Проверяет, может ли инженер не двигаясь выполнить стройку рядом с собой.
     * Это не даёт AI занизить инженера в ситуации, когда путь до цели ещё не
     * существует, но рядом можно поставить мост или проложить следующую дорогу.
     *
     * @param player игрок, чей инженер оценивается
     * @param target цель, в сторону которой нужна инфраструктура
     * @return true, если рядом есть полезная стройка
     */
    private boolean canEngineerBuildNearCurrentPositionToward(PlayerState player, Point target) {
        Point direct = player.engineerRanger.position().stepToward(target);
        if (simulation.map.canBuildBridgeFrom(player.engineerRanger.position(), direct)) return true;

        return player.engineerRanger.position().neighbors8().stream()
                .anyMatch(point -> simulation.map.canBuildRoadBetween(player.engineerRanger.position(), point)
                        || simulation.map.canBuildBridgeFrom(player.engineerRanger.position(), point));
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
            if (!player.needs(species)) continue;
            if (Dinosaur.captureMethodOf(species) == CaptureMethod.HUNT) continue;
            result.add(Dinosaur.spawnBiomeOf(species));
            result.addAll(Dinosaur.bioTrailOf(species));
        }

        if (result.isEmpty()) {
            simulation.dinosaurs.stream()
                    .filter(d -> simulation.isWorthCapturing(player, d))
                    .filter(d -> d.captureMethod != CaptureMethod.HUNT)
                    .map(d -> d.species)
                    .forEach(species -> {
                        result.add(Dinosaur.spawnBiomeOf(species));
                        result.addAll(Dinosaur.bioTrailOf(species));
                    });
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
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> simulation.isWorthCapturing(player, d))
                .filter(d -> allowedMethods.contains(d.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.chebyshev(from)));
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
     * Ищет активную пустую ловушку, которую пора переставить.
     *
     * Ловушка считается полезной, если она стоит на одной из прогнозных клеток
     * прихода нужного S-динозавра. Если лимит ловушек забит, а полезных ловушек
     * нет, инженер получает конкретную физическую задачу: дойти до ближайшей
     * старой ловушки и вернуть её в инвентарь.
     *
     * @param player игрок, чьи ловушки анализируются
     * @return позиция ловушки для снятия или пустой результат
     */
    private Optional<Point> staleTrapRecoveryTarget(PlayerState player) {
        if (activeTrapCount(player) < simulation.inventoryConfig.maxTrapsPerPlayer) {
            return Optional.empty();
        }

        Set<Point> usefulTrapPositions = usefulTrapPositions(player);
        if (usefulTrapPositions.isEmpty()) {
            return Optional.empty();
        }

        boolean hasUsefulTrap = player.traps.stream()
                .filter(trap -> trap.active && !trap.hasDinosaur())
                .anyMatch(trap -> usefulTrapPositions.contains(trap.position));
        if (hasUsefulTrap) {
            return Optional.empty();
        }

        return player.traps.stream()
                .filter(trap -> trap.active && !trap.hasDinosaur())
                .min(Comparator.comparingInt(trap -> player.engineerRanger.position().chebyshev(trap.position)))
                .map(trap -> trap.position);
    }

    /**
     * Собирает прогнозные клетки, где ловушки сейчас действительно помогают
     * поймать оставшихся нужных S-динозавров.
     *
     * @param player игрок, чьи цели и ловушки проверяются
     * @return множество полезных клеток ловушек
     */
    private Set<Point> usefulTrapPositions(PlayerState player) {
        Set<Point> result = new java.util.HashSet<>();

        simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> simulation.isWorthCapturing(player, d))
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .forEach(dinosaur -> result.addAll(simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur)));

        return result;
    }

    /**
     * Проверяет, соседствуют ли две клетки с учётом диагоналей.
     * Ловушки инженер может ставить в свою клетку и в любые соседние клетки,
     * включая диагональные.
     *
     * @param a первая клетка
     * @param b вторая клетка
     * @return true, если клетки совпадают или соседствуют
     */
    private boolean isAdjacentOrSame(Point a, Point b) {
        return a != null && a.isSameOrAdjacent8(b);
    }

    /**
     * Выбирает ближайшую клетку засады для нужных ловушечных целей.
     * Если такой клетки нет, инженер вообще не должен получать высокий вес на
     * ловушки. Иначе он снова будет стоять рядом с Галлимимоном и изображать
     * красный значок беспомощности.
     *
     * @param player игрок, чей инженер оценивается
     * @param from позиция, от которой считается близость
     * @return ближайшая легальная клетка ловушки
     */
    private Optional<Point> bestTrapAmbushPoint(PlayerState player, Point from) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> simulation.isWorthCapturing(player, d))
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .flatMap(dinosaur -> simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).stream())
                .filter(point -> isUsableTrapPoint(player, point))
                .min(Comparator.comparingInt(point -> from == null ? 0 : point.chebyshev(from)));
    }

    /**
     * Проверяет, может ли эта клетка стать новой активной ловушкой игрока.
     *
     * @param player игрок-владелец ловушек
     * @param point проверяемая клетка
     * @return true, если клетка открыта, свободна от ловушки и живого динозавра
     */
    private boolean isUsableTrapPoint(PlayerState player, Point point) {
        return simulation.map.canPlaceTrap(point)
                && simulation.players.stream()
                .flatMap(owner -> owner.traps.stream())
                .noneMatch(trap -> trap.active && trap.position.equals(point))
                && simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .noneMatch(dinosaur -> dinosaur.position.equals(point));
    }


}
