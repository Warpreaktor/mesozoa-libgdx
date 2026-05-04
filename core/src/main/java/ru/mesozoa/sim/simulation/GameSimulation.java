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
     *
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
     * Во время хода рейнджеров это активный игрок. Во время фазы динозавров или
     * до старта раунда показывается первый игрок, чтобы панель инвентаря не
     * исчезала и не превращалась в драматическую пустоту справа от карты.
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

        return players.get(0);
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
            if (dinosaur.captured || dinosaur.removed) continue;

            if (dinosaur.species == Species.VELOCITAURUS) {
                huntWithVelocitaurus(dinosaur);
            }

            Point before = dinosaur.position;
            dinosaur.lastPosition = before;
            moveByBioTrail(dinosaur);

            if (!before.equals(dinosaur.position)) {
                log(dinosaur.species.displayName + " #" + dinosaur.id + " " + before + " -> " + dinosaur.position);
                checkTrapCapture(dinosaur);
            }

            if (dinosaur.species == Species.CRYPTOGNATH) {
                stealBaitIfPossible(dinosaur);
            }

            if (dinosaur.species == Species.VELOCITAURUS) {
                attackRangerIfPossible(dinosaur);
            }
        }
    }

    private void huntWithVelocitaurus(Dinosaur hunter) {
        Optional<Dinosaur> prey = dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> d.species.size == SizeClass.S)
                .filter(d -> d.position.manhattan(hunter.position) <= hunter.species.huntRadius)
                .findFirst();

        if (prey.isPresent() && random.nextDouble() < 0.55) {
            prey.get().removed = true;
            result.predatorKills++;
            log("Велоцитаурус съел " + prey.get().species.displayName + " #" + prey.get().id);
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

    private void checkTrapCapture(Dinosaur dinosaur) {
        if (dinosaur.species.captureMethod != CaptureMethod.TRAP) return;

        for (PlayerState player : players) {
            if (!player.needs(dinosaur.species)) continue;

            for (Trap trap : player.traps) {
                if (trap.active && trap.position.equals(dinosaur.position)) {
                    trap.active = false;
                    dinosaur.captured = true;
                    player.captured.add(dinosaur.species);
                    result.trapCaptures++;
                    log("ПОЙМАН: игрок " + player.id + " поймал "
                            + dinosaur.species.displayName + " (ловушка)");
                    return;
                }
            }
        }
    }

    private void stealBaitIfPossible(Dinosaur dinosaur) {
        for (PlayerState player : players) {
            if (player.hunterBait > 0 && player.hunter.equals(dinosaur.position)) {
                player.hunterBait--;
                log("Криптогнат украл приманку у игрока " + player.id);
            }
        }
    }

    private void attackRangerIfPossible(Dinosaur dinosaur) {
        for (PlayerState player : players) {
            boolean hunterNearby = player.hunter.manhattan(dinosaur.position) <= dinosaur.species.huntRadius;
            boolean engineerNearby = player.engineer.manhattan(dinosaur.position) <= dinosaur.species.huntRadius;
            boolean driverNearby = player.driver.manhattan(dinosaur.position) <= dinosaur.species.huntRadius;

            if ((hunterNearby || engineerNearby || driverNearby) && random.nextDouble() < 0.20) {
                player.turnsSkipped = 1;
                player.returnTeamToBase(map.base);
                log("Велоцитаурус напал на команду игрока " + player.id);
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
