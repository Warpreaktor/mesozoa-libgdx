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

    /** Вес срочной логистики для динозавра в ловушке без дорожного доступа. */
    private static final double SCORE_CAPTURED_DINO_EXTRACTION = 100.0;

    /** Вес ситуации, когда инженер уже рядом с S-целью и может ставить ловушки. */
    private static final double SCORE_IMMEDIATE_TRAP_PLACEMENT = 100.0;

    /** Вес видимой S-цели, когда инженер ещё должен подойти или подготовить ловушки. */
    private static final double SCORE_VISIBLE_TRAP_TARGET = 90.0;

    /** Вес ситуации, когда текущие ловушки уже заняли весь лимит и не помогают. */
    private static final double SCORE_BAD_TRAP_LAYOUT = 78.0;

    /** Вес поддержки охотника дорогой к зоне выслеживания. */
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
        Optional<Point> trapAmbushPoint = bestTrapAmbushPoint(player, player.engineerRanger.position());
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
                    "динозавр в ловушке ждёт вывоза, но водитель не имеет дороги: "
                            + dinosaur.displayName
                            + " на " + dinosaur.position
            );
        }

        if (canPlaceUsefulTrapNow(player, trapAmbushPoint)) {
            Dinosaur dinosaur = trapTarget.get();
            return new AiScore(
                    SCORE_IMMEDIATE_TRAP_PLACEMENT,
                    "инженер рядом с легальной клеткой ловушки для цели: "
                            + dinosaur.displayName
            );
        }

        if (hasVisibleTrapTargetAndAvailableTraps(player, trapTarget, trapAmbushPoint)) {
            Dinosaur dinosaur = trapTarget.get();
            int activeTraps = activeTrapCount(player);
            int distance = player.engineerRanger.position().manhattan(trapAmbushPoint.get());
            double score = SCORE_VISIBLE_TRAP_TARGET - Math.min(35.0, distance * 4.0);
            return new AiScore(
                    score,
                    "видимая S-цель для ловушек: "
                            + dinosaur.displayName
                            + ", расстояние до клетки засады: " + distance
                            + ", ловушек: " + activeTraps + "/" + simulation.inventoryConfig.maxTrapsPerPlayer
            );
        }

        if (hasBadActiveTrapLayout(player, trapTarget)) {
            Dinosaur dinosaur = trapTarget.get();
            return new AiScore(
                    SCORE_BAD_TRAP_LAYOUT,
                    "лимит ловушек занят, но ловушки не перекрывают путь S-цели: "
                            + dinosaur.displayName
            );
        }

        if (hasHunterCaptureZoneWithoutRoadSupport(hunterTargetWithoutRoad)) {
            Dinosaur dinosaur = hunterTargetWithoutRoad.get();
            return new AiScore(
                    SCORE_HUNTER_SUPPORT_ROAD,
                    "есть цель охотника без дорожной поддержки водителя: "
                            + dinosaur.displayName
                            + " на " + dinosaur.position
            );
        }

        if (hasNoRoadOutOfBase(player)) {
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
            int distance = player.engineerRanger.position().manhattan(player.scoutRanger.position());
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
        if (nearestCapturedNeededDinosaurWithoutDriverAccess(player).isPresent()) {
            return true;
        }

        return player.task.stream()
                .filter(species -> !player.captured.contains(species))
                .anyMatch(species -> Dinosaur.captureMethodOf(species) != CaptureMethod.HUNT);
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
        if (player.engineerRanger.position().manhattan(player.scoutRanger.position()) <= NEAR_SCOUT_DISTANCE) {
            return false;
        }

        return canEngineerMakeProgressToward(player, player.scoutRanger.position());
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
        return player.engineerRanger.position().manhattan(player.scoutRanger.position()) <= NEAR_SCOUT_DISTANCE;
    }

    /**
     * Ищет ближайшего нужного динозавра в ловушке, к которому водитель не имеет пути.
     * Пока водитель не вывез добычу, динозавр и ловушка остаются на карте.
     * Эту позицию можно использовать как цель дорожной инфраструктуры.
     *
     * @param player игрок, для которого ищется задача вывоза
     * @return ближайший динозавр в ловушке без водительского маршрута
     */
    private Optional<Dinosaur> nearestCapturedNeededDinosaurWithoutDriverAccess(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> simulation.isTrappedByPlayer(dinosaur, player))
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineerRanger.position().manhattan(dinosaur.position)));
    }

    /**
     * Ищет ближайшую непойманную нужную цель, которая ловится ловушками.
     * @param player игрок, для которого ищется S-цель
     * @return ближайший живой нужный динозавр с CaptureMethod.TRAP
     */
    private Optional<Dinosaur> nearestNeededTrapTarget(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.trapped && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .filter(dinosaur -> simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).stream()
                        .anyMatch(point -> isUsableTrapPoint(player, point)))
                .min(Comparator.comparingInt(d -> d.position.manhattan(player.engineerRanger.position())));
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
    private Optional<Dinosaur> nearestNeededHunterTargetWithoutDriverAccess(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.trapped && !dinosaur.removed)
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> dinosaur.captureMethod == CaptureMethod.TRACKING)
                .filter(dinosaur -> !simulation.map.hasDriverPath(simulation.map.base, dinosaur.position))
                .min(Comparator.comparingInt(dinosaur -> player.engineerRanger.position().manhattan(dinosaur.position)));
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
                .min(Comparator.comparingInt(entry -> player.engineerRanger.position().manhattan(entry.getKey())))
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

        return player.engineerRanger.position().neighbors4().stream()
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
            if (player.captured.contains(species)) continue;
            if (Dinosaur.captureMethodOf(species) == CaptureMethod.HUNT) continue;
            result.add(Dinosaur.spawnBiomeOf(species));
            result.addAll(Dinosaur.bioTrailOf(species));
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
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.captureMethod))
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
                .filter(d -> player.needs(d.species))
                .filter(d -> d.captureMethod == CaptureMethod.TRAP)
                .flatMap(dinosaur -> simulation.dinosaurAi.trapAmbushCandidatesFor(dinosaur).stream())
                .filter(point -> isUsableTrapPoint(player, point))
                .min(Comparator.comparingInt(point -> from == null ? 0 : point.manhattan(from)));
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
                && player.traps.stream().noneMatch(trap -> trap.active && trap.position.equals(point))
                && simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .noneMatch(dinosaur -> dinosaur.position.equals(point));
    }


}
