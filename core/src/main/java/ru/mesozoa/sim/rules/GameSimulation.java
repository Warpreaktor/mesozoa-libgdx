package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.action.RangerActionExecutor;
import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.config.GameMechanicConfig;
import ru.mesozoa.sim.config.InventoryConfig;
import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.report.GameResult;
import ru.mesozoa.sim.tile.Tile;
import ru.mesozoa.sim.tile.TileBag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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

    private boolean roundStarted = false;
    private int activeRangerIndex = 0;

    /**
     * Роли, выбранные AI для текущего игрока.
     *
     * Теперь ход игрока не исполняется целиком за одно нажатие N:
     * первое N активирует первого рейнджера, второе N активирует второго.
     */
    private List<RangerRole> activePlayerRoles = List.of();

    /**
     * Индекс следующей роли из activePlayerRoles.
     */
    private int activePlayerRoleIndex = 0;

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
        activePlayerRoles = List.of();
        activePlayerRoleIndex = 0;
        gameOver = false;
        dinosaurs.clear();
        players.clear();
        log.clear();
        nextDinoId = 1;

        map = GameMap.createWithLanding();
        tileBag = TileBag.createDefault(gameConfig, random);

        RangerTurnPlanner rangerTurnPlanner = new RangerTurnPlanner(this);
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

            if (activePlayerRoles.isEmpty()) {
                activePlayerRoles = rangerActionExecutor.startTurn(player);
                activePlayerRoleIndex = 0;

                if (activePlayerRoles.isEmpty()) {
                    finishCurrentPlayerTurn();
                    updateResult();
                    checkGameOverAfterPartialStep();
                    return;
                }
            }

            RangerRole role = activePlayerRoles.get(activePlayerRoleIndex);
            log("Действие игрока " + player.id + ": " + roleToText(role));
            rangerActionExecutor.playRole(player, role, 2);

            activePlayerRoleIndex++;

            if (activePlayerRoleIndex >= activePlayerRoles.size()) {
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

    public String nextStepLabel() {
        if (gameOver) {
            return "игра завершена";
        }

        if (!roundStarted) {
            return "новый раунд";
        }

        if (activeRangerIndex < players.size()) {
            PlayerState player = players.get(activeRangerIndex);

            if (!activePlayerRoles.isEmpty() && activePlayerRoleIndex < activePlayerRoles.size()) {
                return "игрок " + player.id + ": " + roleToText(activePlayerRoles.get(activePlayerRoleIndex));
            }

            return "игрок " + player.id + " (" + player.color.assetSuffix + ")";
        }

        return "динозавры";
    }

    private void startRoundIfNeeded() {
        if (roundStarted) return;

        round++;
        activeRangerIndex = 0;
        activePlayerRoles = List.of();
        activePlayerRoleIndex = 0;
        roundStarted = true;
        log("Раунд " + round);
    }

    private void finishCurrentPlayerTurn() {
        activeRangerIndex++;
        activePlayerRoles = List.of();
        activePlayerRoleIndex = 0;
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
        activePlayerRoles = List.of();
        activePlayerRoleIndex = 0;
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

    private void moveByBioTrail(Dinosaur dinosaur) {
        Point target = predictNextBioTarget(dinosaur);

        if (target == null) {
            Point frontier = map.nearestUnexploredFrontier(dinosaur.position);
            if (frontier == null) return;

            Point next = stepTowardFrontier(dinosaur.position, frontier);
            if (!map.isPlaced(next) && next.equals(frontier)) {
                dinosaur.removed = true;
                log(dinosaur.species.displayName + " #" + dinosaur.id + " ушёл в неизвестную часть острова " + frontier);
            } else {
                dinosaur.position = next;
            }
            return;
        }

        int steps = Math.max(1, Math.min(dinosaur.species.agility, 3));
        for (int i = 0; i < steps; i++) {
            if (dinosaur.position.equals(target)) break;
            dinosaur.position = stepTowardPlaced(dinosaur.position, target);
        }

        Tile current = map.tile(dinosaur.position);
        Biome nextBiome = dinosaur.species.bioTrail.get((dinosaur.trailIndex + 1) % dinosaur.species.bioTrail.size());
        if (current != null && current.biome == nextBiome) {
            dinosaur.trailIndex = (dinosaur.trailIndex + 1) % dinosaur.species.bioTrail.size();
        }
    }

    private Point predictNextBioTarget(Dinosaur dinosaur) {
        Biome nextBiome = dinosaur.species.bioTrail.get((dinosaur.trailIndex + 1) % dinosaur.species.bioTrail.size());
        return map.nearestPlacedBiome(dinosaur.position, nextBiome);
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
