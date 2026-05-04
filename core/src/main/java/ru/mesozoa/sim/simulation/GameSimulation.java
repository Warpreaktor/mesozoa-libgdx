package ru.mesozoa.sim.simulation;

import ru.mesozoa.sim.action.RangerActionExecutor;
import ru.mesozoa.sim.ranger.ai.RangerTurnPlanner;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.config.GameMechanicConfig;
import ru.mesozoa.sim.config.InventoryConfig;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.dinosaur.profile.DinosaurProfile;
import ru.mesozoa.sim.dinosaur.profile.DinosaurProfiles;
import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.report.GameResult;
import ru.mesozoa.sim.tile.Tile;
import ru.mesozoa.sim.tile.TileBag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;

public final class GameSimulation {

    public final GameConfig gameConfig;
    public final InventoryConfig inventoryConfig;
    public final GameMechanicConfig gameMechanicConfig;
    public final Random random;
    public GameMap map;
    public TileBag tileBag;

    public final ArrayList<Dinosaur> dinosaurs = new ArrayList<>();
    public final ArrayList<PlayerState> players = new ArrayList<>();
    public final ArrayDeque<String> log = new ArrayDeque<>();

    public int nextDinoId = 1;
    public int round = 0;
    public boolean gameOver = false;
    public final GameResult result = new GameResult();

    private RangerActionExecutor rangerActionExecutor;
    private RangerTurnPlanner rangerTurnPlanner;

    private boolean roundStarted = false;
    private int activeRangerIndex = 0;

    /**
     * Роли, уже активированные текущим игроком в рамках его хода.
     * Ход игрока состоит из двух отдельных нажатий N. Перед каждым нажатием
     * AI заново взвешивает ситуацию на карте и выбирает следующую роль.
     */
    private Set<RangerRole> activePlayerUsedRoles = EnumSet.noneOf(RangerRole.class);

    /**
     * Количество уже выполненных активаций текущего игрока.
     */
    private int activePlayerActionIndex = 0;

    public GameSimulation(GameConfig gameConfig,
                          InventoryConfig inventoryConfig,
                          GameMechanicConfig gameMechanicConfig,
                          long seed) {

        this.gameConfig = gameConfig;
        this.inventoryConfig = inventoryConfig;
        this.gameMechanicConfig = gameMechanicConfig;
        this.random = new Random(seed);
        reset();
    }

    public void reset() {
        round = 0;
        roundStarted = false;
        activeRangerIndex = 0;
        activePlayerUsedRoles = EnumSet.noneOf(RangerRole.class);
        activePlayerActionIndex = 0;
        gameOver = false;
        dinosaurs.clear();
        players.clear();
        log.clear();
        nextDinoId = 1;

        map = GameMap.createWithBase();
        tileBag = TileBag.createDefault(gameConfig, random);

        rangerTurnPlanner = new RangerTurnPlanner(this);
        rangerActionExecutor = new RangerActionExecutor(this, rangerTurnPlanner);

        for (int i = 0; i < gameConfig.players; i++) {
            int playerId = i + 1;
            PlayerState player = new PlayerState(playerId, RangerColor.forPlayerId(playerId), map.base);
            assignTask(player);
            players.add(player);
            log("Игрок " + player.id + " (" + player.color.assetSuffix + ") задание: " + taskToText(player));
        }

        updateResult();
        log("Партия создана. На столе только точка высадки " + map.base);
    }

    /**
     * Один микрошаг симуляции:
     * - активация одного рейнджера текущего игрока;
     * - или фаза динозавров.
     */
    public void stepOneTurn() {
        if (gameOver) return;

        startRoundIfNeeded();

        if (activeRangerIndex < players.size()) {
            PlayerState player = players.get(activeRangerIndex);

            if (activePlayerActionIndex == 0 && activePlayerUsedRoles.isEmpty()) {
                if (!prepareCurrentPlayerTurn(player)) {
                    finishCurrentPlayerTurn();
                    updateResult();
                    checkGameOverAfterPartialStep();
                    return;
                }
            }

            RangerPlan plan = rangerTurnPlanner.chooseNextPlanForTurn(player, activePlayerUsedRoles);

            if (plan == null) {
                log("Игрок " + player.id + ": нет полезной активации, ход игрока завершён");
                finishCurrentPlayerTurn();
                updateResult();
                checkGameOverAfterPartialStep();
                return;
            }

            log("Действие игрока " + player.id + ": " + roleToText(plan.role()));
            rangerActionExecutor.executePlan(player, plan);

            activePlayerUsedRoles.add(plan.role());
            activePlayerActionIndex++;

            if (activePlayerActionIndex >= 2) {
                finishCurrentPlayerTurn();
            }

            updateResult();
            checkGameOverAfterPartialStep();
            return;
        }

        log("Ход динозавров");
        dinosaurPhase();
        updateResult();
        finishRound();
    }

    /**
     * Полный раунд:
     * все игроки выполняют обе активации, затем ходят динозавры.
     */
    public void stepRound() {
        if (gameOver) return;

        startRoundIfNeeded();

        while (!gameOver && roundStarted) {
            stepOneTurn();
        }
    }

    /**
     * Возвращает игрока, чьи данные сейчас логичнее всего показывать в HUD.
     *
     * @return игрок для правой UI-панели или null, если игроки ещё не созданы
     */
    public PlayerState playerForHud() {
        if (players.isEmpty()) {
            return null;
        }

        if (activeRangerIndex >= 0 && activeRangerIndex < players.size()) {
            return players.get(activeRangerIndex);
        }

        return players.getFirst();
    }

    /**
     * Возвращает подпись следующего шага для HUD.
     */
    public String nextStepLabel() {
        if (gameOver) {
            return "игра завершена";
        }

        if (!roundStarted) {
            return "новый раунд";
        }

        if (activeRangerIndex < players.size()) {
            PlayerState player = players.get(activeRangerIndex);
            return "игрок " + player.id
                    + " (" + player.color.assetSuffix + ")"
                    + ", действие " + (activePlayerActionIndex + 1) + "/2";
        }

        return "динозавры";
    }

    private void startRoundIfNeeded() {
        if (roundStarted) return;

        round++;
        activeRangerIndex = 0;
        activePlayerUsedRoles = EnumSet.noneOf(RangerRole.class);
        activePlayerActionIndex = 0;
        roundStarted = true;
        log("Раунд " + round);
    }

    private boolean prepareCurrentPlayerTurn(PlayerState player) {
        if (player.isComplete()) {
            log("Игрок " + player.id + " уже выполнил задание.");
            return false;
        }

        if (player.turnsSkipped > 0) {
            player.turnsSkipped--;
            log("Игрок " + player.id + " пропускает ход.");
            return false;
        }

        log("Ход игрока " + player.id + " (" + player.color.assetSuffix + ")");
        return true;
    }

    private void finishCurrentPlayerTurn() {
        activeRangerIndex++;
        activePlayerUsedRoles = EnumSet.noneOf(RangerRole.class);
        activePlayerActionIndex = 0;
    }

    private void finishRound() {
        if (round >= gameConfig.maxRounds
                || players.stream().allMatch(PlayerState::isComplete)
                || tileBag.isEmpty()) {
            gameOver = true;
            log("Партия завершена.");
            return;
        }

        roundStarted = false;
        activeRangerIndex = 0;
        activePlayerUsedRoles = EnumSet.noneOf(RangerRole.class);
        activePlayerActionIndex = 0;
    }

    private void checkGameOverAfterPartialStep() {
        if (players.stream().allMatch(PlayerState::isComplete)) {
            gameOver = true;
            log("Партия завершена.");
        }
    }

    private void assignTask(PlayerState player) {
        Species[] sTargets = {Species.GALLIMIMON, Species.DRIORNIS, Species.CRYPTOGNATH};
        Species[] mHerbTargets = {Species.MONOCERATUS, Species.VOCAREZAUROLOPH};

        player.task.add(sTargets[random.nextInt(sTargets.length)]);
        player.task.add(mHerbTargets[random.nextInt(mHerbTargets.length)]);
        player.task.add(Species.VELOCITAURUS);
    }

    private String taskToText(PlayerState player) {
        ArrayList<String> names = new ArrayList<>();
        for (Species species : player.task) names.add(species.displayName);
        return String.join(", ", names);
    }

    private String roleToText(RangerRole role) {
        return switch (role) {
            case SCOUT -> "разведчик";
            case DRIVER -> "водитель";
            case ENGINEER -> "инженер";
            case HUNTER -> "охотник";
        };
    }

    private void dinosaurPhase() {
        for (Dinosaur dinosaur : new ArrayList<>(dinosaurs)) {
            if (dinosaur.captured || dinosaur.trapped || dinosaur.removed) continue;

            Point before = dinosaur.position;
            dinosaur.lastPosition = before;
            moveByBioTrail(dinosaur);

            if (!before.equals(dinosaur.position)) {
                log(dinosaur.species.displayName + " #" + dinosaur.id + " " + before + " -> " + dinosaur.position);
                checkTrapCapture(dinosaur);
                resolveHuntAmbush(dinosaur);
            }

            if (dinosaur.trapped || dinosaur.captured) {
                continue;
            }

            if (dinosaur.species == Species.CRYPTOGNATH) {
                stealBaitIfPossible(dinosaur);
            }
        }
    }

    /**
     * Прогнозирует клетку, в которую динозавр придёт по био-тропе на ближайшем
     * ходу, если такой маршрут уже существует на открытой карте.
     * Метод намеренно не прогнозирует случайный шаг: ловушка должна ставиться
     * на понятную клетку маршрута, а не на «авось он туда споткнётся», потому
     *
     * @param dinosaur динозавр, для которого строится прогноз
     * @return конечная клетка ближайшего движения по био-тропе или Optional.empty()
     */
    public Optional<Point> predictDinosaurBioTrailDestination(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.captured || dinosaur.trapped || dinosaur.removed) {
            return Optional.empty();
        }

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = map.tile(dinosaur.position);

        if (currentTile == null) {
            return Optional.empty();
        }

        Biome nextBiome = profile.nextBiomeAfter(currentTile.biome, dinosaur.trailIndex);
        Optional<List<Point>> path = findDinosaurPathToReachableBiome(
                dinosaur.position,
                nextBiome,
                profile
        );

        if (path.isEmpty() || path.get().size() < 2) {
            return Optional.empty();
        }

        return Optional.of(path.get().get(path.get().size() - 1));
    }

    /**
     * Возвращает упорядоченный список клеток, куда имеет смысл ставить ловушку
     * для указанного динозавра.
     *
     * Сначала берётся точный прогноз по био-тропе, если следующий биом уже
     * достижим. Если точного прогноза нет, используются соседние клетки, куда
     * динозавр реально может шагнуть случайным ходом в рамках своего маршрута.
     * Последним запасным вариантом идут открытые клетки следующего биома: это
     * даёт инженеру понятную цель для будущей засады, но не заставляет его
     * ставить капкан под лапы динозавру, потому что мы всё-таки делаем игру,
     * а не симулятор канцелярского обмана.
     *
     * @param dinosaur динозавр, для которого планируется засада
     * @return клетки для ловушки в порядке убывания полезности
     */
    public List<Point> trapAmbushCandidatesFor(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.captured || dinosaur.trapped || dinosaur.removed) {
            return List.of();
        }

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = map.tile(dinosaur.position);
        if (currentTile == null) {
            return List.of();
        }

        Optional<Point> exactBioTrailDestination = predictDinosaurBioTrailDestination(dinosaur)
                .filter(point -> !point.equals(dinosaur.position))
                .filter(map::canPlaceTrap);

        if (exactBioTrailDestination.isPresent()) {
            return List.of(exactBioTrailDestination.get());
        }

        LinkedHashSet<Point> result = new LinkedHashSet<>();

        for (Point neighbor : dinosaur.position.neighbors4()) {
            if (neighbor.equals(dinosaur.position)) {
                continue;
            }
            if (!map.canPlaceTrap(neighbor)) {
                continue;
            }
            if (!canDinosaurStandOn(neighbor, profile)) {
                continue;
            }
            result.add(neighbor);
        }

        if (!result.isEmpty()) {
            return new ArrayList<>(result);
        }

        Biome nextBiome = profile.nextBiomeAfter(currentTile.biome, dinosaur.trailIndex);
        map.entries().stream()
                .filter(entry -> entry.getValue().biome == nextBiome)
                .map(entry -> entry.getKey())
                .filter(point -> !point.equals(dinosaur.position))
                .filter(map::canPlaceTrap)
                .sorted(Comparator.comparingInt(point -> dinosaur.position.manhattan(point)))
                .forEach(result::add);

        return new ArrayList<>(result);
    }

    /**
     * Выбирает лучшую клетку для охотничьей засады на конкретного M-хищника.
     *
     * AI старается не бросать приманку прямо под нос хищнику. Лучше выбрать
     * клетку био-тропы, куда он придёт через пару ходов: охотник успеет добрать
     * карты подготовки, но не устроит недельный фестиваль лежания в траве.
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
                        .thenComparingInt(point -> map.groundRangerPathDistance(player.hunterRanger.position(), point))
                        .thenComparingInt(point -> point.manhattan(dinosaur.position)));
    }

    /**
     * Возвращает клетки, где охотник может ждать M-хищника с приманкой.
     *
     * Список включает не только ближайший шаг, но и более поздние клетки био-тропы.
     * Для охоты это важно: если ставить приманку на следующий ход хищника, охотник
     * часто успевает взять только стартовые карты и снова героически позорится.
     *
     * @param dinosaur хищник, под маршрут которого ищутся клетки
     * @return список возможных клеток засады
     */
    public List<Point> huntAmbushCandidatesFor(Dinosaur dinosaur) {
        if (dinosaur == null || dinosaur.captured || dinosaur.trapped || dinosaur.removed) {
            return List.of();
        }
        if (dinosaur.species.captureMethod != CaptureMethod.HUNT) {
            return List.of();
        }

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = map.tile(dinosaur.position);
        if (currentTile == null) {
            return List.of();
        }

        LinkedHashSet<Point> result = new LinkedHashSet<>();

        predictDinosaurBioTrailDestination(dinosaur)
                .filter(point -> !point.equals(dinosaur.position))
                .filter(map::canPlaceBait)
                .ifPresent(result::add);

        map.entries().stream()
                .filter(entry -> profile.canEnter(entry.getValue().biome))
                .map(entry -> entry.getKey())
                .filter(point -> !point.equals(dinosaur.position))
                .filter(map::canPlaceBait)
                .filter(point -> canDinosaurStandOn(point, profile))
                .sorted(Comparator
                        .comparingInt((Point point) -> estimateDinosaurTurnsTo(dinosaur, point))
                        .thenComparingInt(point -> dinosaur.position.manhattan(point)))
                .forEach(result::add);

        if (!result.isEmpty()) {
            return new ArrayList<>(result);
        }

        for (Point neighbor : dinosaur.position.neighbors4()) {
            if (!map.canPlaceBait(neighbor)) continue;
            if (!canDinosaurStandOn(neighbor, profile)) continue;
            result.add(neighbor);
        }

        return new ArrayList<>(result);
    }

    /**
     * Примерно оценивает, через сколько фаз динозавров хищник дойдёт до клетки.
     *
     * Учитывается не только расстояние, но и порядок био-тропы. Велоцитаурус из
     * Луга не должен считаться готовым прийти в Хвойный лес за один ход только
     * потому, что клетка рядом: сначала у него по маршруту Лиственный лес.
     *
     * @param dinosaur хищник
     * @param target клетка с приманкой
     * @return число фаз динозавров или Integer.MAX_VALUE, если путь не найден
     */
    public int estimateDinosaurTurnsTo(Dinosaur dinosaur, Point target) {
        if (dinosaur == null || target == null) return Integer.MAX_VALUE;
        if (dinosaur.position.equals(target)) return 0;

        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        int distance = dinosaurPathDistance(dinosaur.position, target, profile);
        if (distance == Integer.MAX_VALUE) return Integer.MAX_VALUE;

        Tile currentTile = map.tile(dinosaur.position);
        Tile targetTile = map.tile(target);
        if (currentTile == null || targetTile == null) return Integer.MAX_VALUE;

        int currentIndex = profile.trailIndexOf(currentTile.biome);
        int targetIndex = profile.trailIndexOf(targetTile.biome);
        if (currentIndex < 0 || targetIndex < 0) return Integer.MAX_VALUE;

        int routeSteps = Math.floorMod(targetIndex - currentIndex, dinosaur.species.bioTrail.size());
        if (routeSteps == 0) {
            routeSteps = 1;
        }

        int agility = Math.max(1, profile.agility());
        int distanceTurns = Math.max(1, (int) Math.ceil(distance / (double) agility));
        return Math.max(routeSteps, distanceTurns);
    }

    /**
     * Проверяет, есть ли у игрока активная охота на указанный вид.
     *
     * @param player игрок
     * @param species вид из задания
     * @return true, если охотник уже лежит в засаде на этот вид
     */
    public boolean hasActiveHuntForSpecies(PlayerState player, Species species) {
        return player != null
                && player.activeHunt != null
                && player.activeHunt.species == species;
    }

    /** Проверяет, может ли указанная клетка быть клеткой охотничьей засады. */
    private boolean isLegalHuntAmbushPoint(PlayerState player, Point point) {
        if (point == null || !map.canPlaceBait(point)) return false;
        if (!map.canGroundRangerStandOn(point)) return false;
        if (map.groundRangerPathDistance(player.hunterRanger.position(), point) == Integer.MAX_VALUE) return false;

        return dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .noneMatch(dinosaur -> dinosaur.position.equals(point));
    }

    /** Считает штраф тайминга для клетки засады. */
    private int huntAmbushTimingPenalty(Dinosaur dinosaur, Point point) {
        int turns = estimateDinosaurTurnsTo(dinosaur, point);
        if (turns == Integer.MAX_VALUE) return Integer.MAX_VALUE;

        int desiredTurns = 2;
        int earlyPenalty = turns < 1 ? 1000 : 0;
        return earlyPenalty + Math.abs(turns - desiredTurns) * 10;
    }

    /** Считает расстояние по биомам, доступным конкретному динозавру. */
    private int dinosaurPathDistance(Point start, Point target, DinosaurProfile profile) {
        if (!canDinosaurStandOn(start, profile) || !canDinosaurStandOn(target, profile)) {
            return Integer.MAX_VALUE;
        }

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Integer> distance = new HashMap<>();

        queue.add(start);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int currentDistance = distance.get(current);

            if (current.equals(target)) {
                return currentDistance;
            }

            for (Point neighbor : current.neighbors4()) {
                if (distance.containsKey(neighbor)) continue;
                if (!canDinosaurStandOn(neighbor, profile)) continue;

                distance.put(neighbor, currentDistance + 1);
                queue.addLast(neighbor);
            }
        }

        return Integer.MAX_VALUE;
    }

    /**
     * Проверяет, вошёл ли M-хищник на клетку активной приманки и чем закончилась охота.
     *
     * @param dinosaur только что переместившийся динозавр
     */
    private void resolveHuntAmbush(Dinosaur dinosaur) {
        if (dinosaur.species.captureMethod != CaptureMethod.HUNT) return;
        if (dinosaur.lastPosition == null || dinosaur.lastPosition.equals(dinosaur.position)) return;

        for (PlayerState player : players) {
            if (player.activeHunt == null) continue;
            if (player.activeHunt.dinosaurId != dinosaur.id) continue;
            if (!player.activeHunt.baitPosition.equals(dinosaur.position)) continue;
            if (!player.needs(dinosaur.species)) {
                player.activeHunt = null;
                continue;
            }

            int dinosaurRoll = random.nextInt(6) + 1;
            int dinosaurScore = dinosaurRoll + dinosaur.species.agility;
            int hunterScore = player.activeHunt.preparationScore();

            if (hunterScore > dinosaurScore) {
                dinosaur.captured = true;
                player.captured.add(dinosaur.species);
                player.activeHunt = null;
                result.huntCaptures++;
                log("ПОЙМАН: охотник игрока " + player.id
                        + " усыпил " + dinosaur.species.displayName
                        + " #" + dinosaur.id
                        + " в засаде; подготовка " + hunterScore
                        + " против " + dinosaurScore
                        + " (1d6=" + dinosaurRoll + ")");
                return;
            }

            player.hunterRanger.setPosition(map.base);
            player.activeHunt = null;
            log("ПРОВАЛ ОХОТЫ: " + dinosaur.species.displayName
                    + " #" + dinosaur.id
                    + " обошёл засаду игрока " + player.id
                    + "; подготовка " + hunterScore
                    + " против " + dinosaurScore
                    + " (1d6=" + dinosaurRoll + "). Охотник вернулся на базу");
            return;
        }
    }

    /**
     * Перемещает динозавра по его биологической тропе.
     *
     * Новая логика намеренно не уводит динозавра в туман войны. Если целевой
     * биом маршрута не открыт или недостижим за один ход по уже открытой карте,
     * динозавр делает один случайный шаг. Неизведанная область при этом считается
     * краем острова: если случайное направление ведёт в закрытую или непроходимую
     * клетку, динозавр остаётся на месте. Да, зверь наконец-то перестал исчезать
     * сразу после появления, это был не динозавр, а фокусник-шарлатан.
     */
    private void moveByBioTrail(Dinosaur dinosaur) {
        DinosaurProfile profile = DinosaurProfiles.profile(dinosaur.species);
        Tile currentTile = map.tile(dinosaur.position);

        if (currentTile == null) {
            return;
        }

        Biome nextBiome = profile.nextBiomeAfter(currentTile.biome, dinosaur.trailIndex);
        Optional<List<Point>> path = findDinosaurPathToReachableBiome(
                dinosaur.position,
                nextBiome,
                profile
        );

        if (path.isPresent()) {
            moveDinosaurAlongBioTrailPath(dinosaur, profile, nextBiome, path.get());
            return;
        }

        moveDinosaurInRandomDirection(dinosaur, profile, nextBiome);
    }

    /**
     * Перемещает динозавра в найденный целевой биом био-тропы.
     *
     * @param dinosaur динозавр, который перемещается
     * @param profile видовой профиль динозавра
     * @param nextBiome целевой биом био-тропы
     * @param path кратчайший путь до подходящего тайла, включая стартовую клетку
     */
    private void moveDinosaurAlongBioTrailPath(
            Dinosaur dinosaur,
            DinosaurProfile profile,
            Biome nextBiome,
            List<Point> path
    ) {
        if (path.size() < 2) {
            return;
        }

        dinosaur.position = path.get(path.size() - 1);
        int newTrailIndex = profile.trailIndexOf(nextBiome);
        if (newTrailIndex >= 0) {
            dinosaur.trailIndex = newTrailIndex;
        }
    }

    /**
     * Ищет ближайший тайл следующего биома, до которого динозавр может дойти
     * за один ход в пределах своей ловкости.
     *
     * Поиск идёт только по уже открытым клеткам и только по биомам, входящим
     * в био-тропу конкретного вида.
     */
    private Optional<List<Point>> findDinosaurPathToReachableBiome(
            Point start,
            Biome targetBiome,
            DinosaurProfile profile
    ) {
        if (!canDinosaurStandOn(start, profile)) {
            return Optional.empty();
        }

        ArrayDeque<Point> queue = new ArrayDeque<>();
        HashMap<Point, Point> previous = new HashMap<>();
        HashMap<Point, Integer> distance = new HashMap<>();

        queue.add(start);
        previous.put(start, null);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            int currentDistance = distance.get(current);

            if (!current.equals(start) && tileBiome(current) == targetBiome) {
                return Optional.of(restorePath(previous, current));
            }

            if (currentDistance >= profile.agility()) {
                continue;
            }

            for (Point neighbor : current.neighbors4()) {
                if (previous.containsKey(neighbor)) {
                    continue;
                }

                if (!canDinosaurStandOn(neighbor, profile)) {
                    continue;
                }

                previous.put(neighbor, current);
                distance.put(neighbor, currentDistance + 1);
                queue.addLast(neighbor);
            }
        }

        return Optional.empty();
    }

    /**
     * Восстанавливает путь BFS от старта до найденной цели.
     */
    private List<Point> restorePath(HashMap<Point, Point> previous, Point target) {
        ArrayList<Point> path = new ArrayList<>();
        Point step = target;

        while (step != null) {
            path.add(0, step);
            step = previous.get(step);
        }

        return path;
    }

    /**
     * Делает один случайный шаг динозавра, если следующий биом био-тропы сейчас
     * недостижим за один ход.
     *
     * Если случайное направление ведёт в закрытую клетку, базу или биом вне
     * маршрута этого вида, динозавр стоит на месте.
     */
    private void moveDinosaurInRandomDirection(Dinosaur dinosaur, DinosaurProfile profile, Biome nextBiome) {
        Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
        Point next = direction.from(dinosaur.position);

        if (canDinosaurStandOn(next, profile)) {
            dinosaur.position = next;
            Tile tile = map.tile(next);
            if (tile != null) {
                int newTrailIndex = profile.trailIndexOf(tile.biome);
                if (newTrailIndex >= 0) {
                    dinosaur.trailIndex = newTrailIndex;
                }
            }
            log(dinosaur.species.displayName + " #" + dinosaur.id
                    + " не нашёл рядом биом " + nextBiome.displayName
                    + " и шагнул " + direction + " на " + next);
            return;
        }

        log(dinosaur.species.displayName + " #" + dinosaur.id
                + " не нашёл доступный " + nextBiome.displayName
                + " и остался на месте: " + direction + " закрыт или непроходим");
    }

    /**
     * Проверяет, может ли динозавр стоять на клетке.
     *
     * Неизведанная область, база и биомы вне био-тропы вида считаются
     * непроходимыми. Вот это и убирает старую механику «вышел за край карты и
     * исчез», которая выглядела так, будто динозавров уносит бухгалтерия.
     */
    private boolean canDinosaurStandOn(Point point, DinosaurProfile profile) {
        if (point == null || !map.isPlaced(point) || map.isBase(point)) {
            return false;
        }

        Biome biome = tileBiome(point);
        return profile.canEnter(biome);
    }

    /**
     * Возвращает биом обычного тайла или null для базы/закрытой клетки.
     */
    private Biome tileBiome(Point point) {
        Tile tile = map.tile(point);
        return tile == null ? null : tile.biome;
    }

    public Point stepTowardPlaced(Point from, Point target) {
        Point direct = from.stepToward(target);
        if (map.isPlaced(direct) && isPassable(direct)) return direct;

        return map.placedNeighbors(from).stream()
                .filter(this::isPassable)
                .min(Comparator.comparingInt(p -> p.manhattan(target)))
                .orElse(from);
    }

    private Point stepTowardFrontier(Point from, Point frontier) {
        Point direct = from.stepToward(frontier);
        if (direct.equals(frontier) && map.canPlace(direct)) return direct;
        if (map.isPlaced(direct) && isPassable(direct)) return direct;

        return map.placedNeighbors(from).stream()
                .filter(this::isPassable)
                .min(Comparator.comparingInt(p -> p.manhattan(frontier)))
                .orElse(from);
    }

    private boolean isPassable(Point point) {
        if (map.isBase(point)) {
            return true;
        }

        Tile tile = map.tile(point);
        return tile != null && !tile.biome.blocksMostMovement();
    }

    /**
     * Проверяет срабатывание ловушки после перемещения динозавра.
     *
     * Ловушка только обездвиживает динозавра на карте. Вид засчитывается игроку
     * позже, когда водитель сможет приехать по дорожной сети, забрать клетку с
     * ловушкой и вернуться на базу. Иначе ловушка превращалась в телепорт добычи
     * в инвентарь, а это уже не настолка, а налоговая оптимизация.
     *
     * @param dinosaur динозавр, который только что переместился
     */
    private void checkTrapCapture(Dinosaur dinosaur) {
        if (dinosaur.species.captureMethod != CaptureMethod.TRAP) return;
        if (dinosaur.lastPosition == null || dinosaur.lastPosition.equals(dinosaur.position)) return;

        for (PlayerState player : players) {
            if (!player.needs(dinosaur.species)) continue;

            for (Trap trap : player.traps) {
                if (trap.canCatchDinosaur() && trap.position.equals(dinosaur.position)) {
                    dinosaur.trapped = true;
                    dinosaur.trappedByPlayerId = player.id;
                    trap.trappedDinosaurId = dinosaur.id;
                    result.trapCaptures++;
                    log("В ЛОВУШКЕ: игрок " + player.id + " поймал "
                            + dinosaur.species.displayName
                            + " #" + dinosaur.id
                            + "; нужен водитель для вывоза на базу");
                    return;
                }
            }
        }
    }

    /**
     * Ищет ближайшего нужного динозавра, который уже сидит в ловушке игрока и ждёт вывоза.
     *
     * @param player игрок, для которого ищется задача водителя
     * @param from клетка, от которой считается расстояние для сортировки
     * @param requireDriverRoute true, если нужно вернуть только цель с готовым водительским маршрутом
     * @return ближайший ожидающий вывоза динозавр
     */
    public Optional<Dinosaur> nearestTrappedNeededDinosaurAwaitingPickup(
            PlayerState player,
            Point from,
            boolean requireDriverRoute
    ) {
        return dinosaurs.stream()
                .filter(dinosaur -> isTrappedByPlayer(dinosaur, player))
                .filter(dinosaur -> player.needs(dinosaur.species))
                .filter(dinosaur -> !requireDriverRoute || canDriverExtractTrappedDinosaur(player, dinosaur))
                .min(Comparator.comparingInt(dinosaur -> from == null ? 0 : dinosaur.position.manhattan(from)));
    }

    /**
     * Проверяет, сидит ли динозавр в ловушке указанного игрока.
     *
     * @param dinosaur проверяемый динозавр
     * @param player игрок-владелец ловушки
     * @return true, если динозавр ждёт вывоза именно этим игроком
     */
    public boolean isTrappedByPlayer(Dinosaur dinosaur, PlayerState player) {
        return dinosaur != null
                && player != null
                && dinosaur.trapped
                && !dinosaur.captured
                && !dinosaur.removed
                && dinosaur.trappedByPlayerId == player.id;
    }

    /**
     * Проверяет, может ли водитель вывезти динозавра из ловушки за одну активацию.
     *
     * За первое очко действия водитель доезжает до любой точки связанной дорожной
     * сети, за второе возвращается на базу уже с динозавром. Длина дороги не важна:
     * это джип, а не пеший бухгалтер с линейкой.
     *
     * @param player игрок, чей водитель проверяется
     * @param dinosaur динозавр в ловушке
     * @return true, если есть путь от текущей позиции водителя до ловушки и путь обратно на базу
     */
    public boolean canDriverExtractTrappedDinosaur(PlayerState player, Dinosaur dinosaur) {
        return isTrappedByPlayer(dinosaur, player)
                && map.hasDriverPath(player.driverRanger.position(), dinosaur.position)
                && map.hasDriverPath(dinosaur.position, map.base);
    }

    /**
     * Доставляет динозавра из ловушки на базу и засчитывает его игроку.
     *
     * @param player игрок, которому засчитывается динозавр
     * @param dinosaur динозавр, сидящий в ловушке игрока
     * @return true, если вывоз выполнен
     */
    public boolean extractTrappedDinosaurToBase(PlayerState player, Dinosaur dinosaur) {
        if (!canDriverExtractTrappedDinosaur(player, dinosaur)) {
            return false;
        }

        Optional<Trap> trap = trapHoldingDinosaur(player, dinosaur);
        if (trap.isEmpty()) {
            return false;
        }

        dinosaur.trapped = false;
        dinosaur.trappedByPlayerId = 0;
        dinosaur.captured = true;
        player.captured.add(dinosaur.species);

        trap.get().trappedDinosaurId = 0;
        trap.get().active = false;

        log("ДОСТАВЛЕН: водитель игрока " + player.id
                + " вывез " + dinosaur.species.displayName
                + " #" + dinosaur.id + " на базу");
        return true;
    }

    /**
     * Ищет ловушку игрока, которая удерживает конкретного динозавра.
     *
     * @param player владелец ловушки
     * @param dinosaur динозавр в ловушке
     * @return ловушка с указанным динозавром
     */
    private Optional<Trap> trapHoldingDinosaur(PlayerState player, Dinosaur dinosaur) {
        return player.traps.stream()
                .filter(trap -> trap.active)
                .filter(trap -> trap.trappedDinosaurId == dinosaur.id)
                .findFirst();
    }

    private void stealBaitIfPossible(Dinosaur dinosaur) {
        for (PlayerState player : players) {
            if (player.hunterBait > 0 && player.hunterRanger.position().equals(dinosaur.position)) {
                player.hunterBait--;
                log("Криптогнат украл приманку у игрока " + player.id);
            }
        }
    }

    private void updateResult() {
        result.rounds = round;
        result.openedTiles = map.openedCount();
        result.spawnedDinosaurs = dinosaurs.size();
        result.capturedDinosaurs = (int) dinosaurs.stream().filter(d -> d.captured).count();
        result.completedPlayers = (int) players.stream().filter(PlayerState::isComplete).count();
    }

    public void log(String message) {
        log.addFirst(message);
        while (log.size() > 18) log.removeLast();
    }
}
