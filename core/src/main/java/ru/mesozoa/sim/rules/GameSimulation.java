package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.report.GameResult;

import java.util.*;

public final class GameSimulation {
    public final SimulationConfig config;
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

    private boolean roundStarted = false;
    private int activeRangerIndex = 0;

    public GameSimulation(SimulationConfig config, long seed) {
        this.config = config;
        this.random = new Random(seed);
        reset();
    }

    public void reset() {
        round = 0;
        roundStarted = false;
        activeRangerIndex = 0;
        gameOver = false;
        dinosaurs.clear();
        players.clear();
        log.clear();
        nextDinoId = 1;

        map = GameMap.createWithLanding();
        tileBag = TileBag.createDefault(config, random);

        for (int i = 0; i < config.players; i++) {
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
     * - ход одного игрока, в котором он активирует до двух разных рейнджеров;
     * - или общая фаза динозавров.
     *
     * N вызывает этот метод.
     */
    public void stepOneTurn() {
        if (gameOver) return;

        startRoundIfNeeded();

        if (activeRangerIndex < players.size()) {
            PlayerState player = players.get(activeRangerIndex);
            activeRangerIndex++;

            rangerTurn(player);
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
     * все игроки по очереди + фаза динозавров.
     *
     * Ctrl+N вызывает этот метод.
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
            return "игрок " + player.id + " (" + player.color.assetSuffix + ")";
        }

        return "динозавры";
    }

    private void startRoundIfNeeded() {
        if (roundStarted) return;

        round++;
        activeRangerIndex = 0;
        roundStarted = true;
        log("Раунд " + round);
    }

    private void finishRound() {
        if (round >= config.maxRounds
                || players.stream().allMatch(PlayerState::isComplete)
                || tileBag.isEmpty()) {
            gameOver = true;
            log("Партия завершена.");
            return;
        }

        roundStarted = false;
        activeRangerIndex = 0;
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
        for (Species s : player.task) names.add(s.displayName);
        return String.join(", ", names);
    }

    private void rangerTurn(PlayerState player) {
        if (player.isComplete()) {
            log("Игрок " + player.id + " уже выполнил задание.");
            return;
        }

        if (player.turnsSkipped > 0) {
            player.turnsSkipped--;
            log("Игрок " + player.id + " пропускает ход.");
            return;
        }

        List<RangerRole> roles = chooseTwoRangersForTurn(player);

        log("Ход игрока " + player.id + " (" + player.color.assetSuffix + "): "
                + roleListToText(roles));

        for (RangerRole role : roles) {
            performRangerAction(player, role, 2);
        }
    }

    /**
     * Упрощённый AI выбора двух фигурок.
     *
     * Логика такая:
     * 1. разведчик почти всегда полезен, пока есть что открывать;
     * 2. если на карте есть нужная S-дичь, нужен инженер с ловушками;
     * 3. если есть нужная M-дичь или хищник, нужен охотник;
     * 4. водитель пока играет логистическую роль и подтягивается к команде.
     *
     * Это не финальный интеллект игрока, а сухая математика для симулятора.
     */
    private List<RangerRole> chooseTwoRangersForTurn(PlayerState player) {
        ArrayList<RangerRole> roles = new ArrayList<>(2);

        if (!tileBag.isEmpty()) {
            roles.add(RangerRole.SCOUT);
        }

        if (roles.size() < 2 && hasUsefulEngineerAction(player)) {
            roles.add(RangerRole.ENGINEER);
        }

        if (roles.size() < 2 && hasUsefulHunterAction(player)) {
            roles.add(RangerRole.HUNTER);
        }

        if (roles.size() < 2 && hasUsefulDriverAction(player)) {
            roles.add(RangerRole.DRIVER);
        }

        if (roles.size() < 2 && !roles.contains(RangerRole.HUNTER)) {
            roles.add(RangerRole.HUNTER);
        }

        if (roles.size() < 2 && !roles.contains(RangerRole.ENGINEER)) {
            roles.add(RangerRole.ENGINEER);
        }

        if (roles.size() < 2 && !roles.contains(RangerRole.DRIVER)) {
            roles.add(RangerRole.DRIVER);
        }

        return roles.subList(0, Math.min(2, roles.size()));
    }

    private String roleListToText(List<RangerRole> roles) {
        ArrayList<String> names = new ArrayList<>();
        for (RangerRole role : roles) {
            names.add(switch (role) {
                case SCOUT -> "разведчик";
                case DRIVER -> "водитель";
                case ENGINEER -> "инженер";
                case HUNTER -> "охотник";
            });
        }
        return String.join(" + ", names);
    }

    private boolean hasUsefulEngineerAction(PlayerState player) {
        if (player.traps.stream().filter(t -> t.active).count() >= config.maxTrapsPerPlayer) {
            return false;
        }

        return dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .anyMatch(d -> d.species.captureMethod == CaptureMethod.TRAP);
    }

    private boolean hasUsefulHunterAction(PlayerState player) {
        return dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .anyMatch(d -> d.species.captureMethod == CaptureMethod.TRACKING
                        || d.species.captureMethod == CaptureMethod.HUNT);
    }

    private boolean hasUsefulDriverAction(PlayerState player) {
        return !player.driver.equals(player.hunter)
                || !player.driver.equals(player.engineer)
                || !player.driver.equals(player.scout);
    }

    private void performRangerAction(PlayerState player, RangerRole role, int movementPoints) {
        switch (role) {
            case SCOUT -> scoutAction(player, movementPoints);
            case ENGINEER -> engineerAction(player, movementPoints);
            case HUNTER -> hunterAction(player, movementPoints);
            case DRIVER -> driverAction(player, movementPoints);
        }
    }

    /**
     * Разведчик:
     * - имеет 2 очка действий;
     * - спецдействие открытия тайла используется не более одного раза за активацию.
     *
     * Пока для симуляции оставляем старую абстракцию:
     * разведчик выбирает ближайшее доступное место, вытаскивает слепой тайл из мешка
     * и открывает его.
     */
    private void scoutAction(PlayerState player, int movementPoints) {
        if (tileBag.isEmpty()) {
            moveRoleTowardNearestFrontier(player, RangerRole.SCOUT, movementPoints);
            return;
        }

        exploreOneTile(player);
    }

    private void engineerAction(PlayerState player, int movementPoints) {
        boolean placedTrap = placeTrapsForNeededS(player);
        if (placedTrap) {
            return;
        }

        Optional<Dinosaur> target = nearestNeededDinosaur(player, player.engineer, CaptureMethod.TRAP);
        if (target.isPresent()) {
            moveRoleToward(player, RangerRole.ENGINEER, target.get().position, movementPoints);
            return;
        }

        moveRoleToward(player, RangerRole.ENGINEER, player.scout, movementPoints);
    }

    private void hunterAction(PlayerState player, int movementPoints) {
        boolean acted = attemptCapture(player);
        if (acted) {
            return;
        }

        Optional<Dinosaur> target = nearestNeededDinosaur(
                player,
                player.hunter,
                CaptureMethod.TRACKING,
                CaptureMethod.HUNT
        );

        if (target.isPresent()) {
            moveRoleToward(player, RangerRole.HUNTER, target.get().position, movementPoints);
            return;
        }

        /*
         * Если охотнику пока некого ловить, он всё равно тратит свою активацию:
         * подтягивается к разведчику. Иначе в логе написано "разведчик + охотник",
         * а на столе охотник стоит на базе и философски смотрит в пустоту.
         */
        moveRoleToward(player, RangerRole.HUNTER, player.scout, movementPoints);
    }

    private void driverAction(PlayerState player, int movementPoints) {
        Point target;

        if (!player.driver.equals(player.hunter)) {
            target = player.hunter;
        } else if (!player.driver.equals(player.engineer)) {
            target = player.engineer;
        } else {
            target = player.scout;
        }

        moveRoleToward(player, RangerRole.DRIVER, target, movementPoints);
    }

    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        Set<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        allowedMethods.addAll(Arrays.asList(methods));

        return dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.manhattan(from)));
    }

    private void moveRoleTowardNearestFrontier(PlayerState player, RangerRole role, int movementPoints) {
        Point start = player.positionOf(role);
        Point frontier = map.nearestUnexploredFrontier(start);
        if (frontier == null) return;

        moveRoleToward(player, role, frontier, movementPoints);
    }

    private void moveRoleToward(PlayerState player, RangerRole role, Point target, int movementPoints) {
        Point position = player.positionOf(role);

        for (int i = 0; i < movementPoints; i++) {
            if (position.equals(target)) break;

            Point next = stepTowardPlaced(position, target);
            if (next.equals(position)) break;

            position = next;
        }

        player.setPosition(role, position);
    }

    private void exploreOneTile(PlayerState player) {
        TileDefinition drawn = tileBag.draw();
        if (drawn == null) return;

        Point placement = choosePlacementPoint(player);
        if (placement == null) return;

        Tile placedTile = placeDrawnTile(player, drawn, placement, false);
        if (placedTile == null) return;

        for (Direction direction : placedTile.expansionDirections) {
            Point extraPoint = direction.from(placement);

            if (!map.canPlaceExpansion(extraPoint)) {
                log("Переход " + direction + " заблокирован: клетка занята " + extraPoint);
                continue;
            }

            TileDefinition extra = tileBag.drawExtraBiome(placedTile.biome);
            if (extra == null) {
                log("Нет доп. тайла для биома " + placedTile.biome.displayName);
                continue;
            }

            placeDrawnTile(player, extra, extraPoint, true);
        }
    }

    private Point choosePlacementPoint(PlayerState player) {
        List<Point> candidates = map.availablePlacementPoints();
        if (candidates.isEmpty()) return null;

        return candidates.stream()
                .min(Comparator.comparingInt(p -> p.manhattan(player.scout)))
                .orElse(candidates.get(random.nextInt(candidates.size())));
    }

    private Tile placeDrawnTile(PlayerState player, TileDefinition drawn, Point placement, boolean automaticExpansion) {
        int rotationQuarterTurns = random.nextInt(4);
        Tile tile = drawn.toPlacedTile(rotationQuarterTurns);
        boolean placed = automaticExpansion
                ? map.placeExpansionTile(placement, tile)
                : map.placeTile(placement, tile);

        if (!placed) return null;

        if (!automaticExpansion) {
            player.scout = placement;
        }

        String prefix = automaticExpansion ? "Автодостройка" : "Игрок " + player.id + " выложил";
        log(prefix + ": " + tile.biome.displayName + " " + placement + ", поворот " + (rotationQuarterTurns * 90) + "°");

        if (tile.hasSpawn() && !tile.spawnUsed) {
            spawnDinosaur(tile.spawnSpecies, placement);
            tile.spawnUsed = true;
        }

        return tile;
    }

    private void spawnDinosaur(Species species, Point position) {
        Dinosaur dino = new Dinosaur(nextDinoId++, species, position);
        dinosaurs.add(dino);
        result.firstSpawnRound.putIfAbsent(species, round);
        log("СПАУН: " + species.displayName + " на " + position);
    }

    private boolean placeTrapsForNeededS(PlayerState player) {
        if (player.traps.stream().filter(t -> t.active).count() >= config.maxTrapsPerPlayer) return false;

        Optional<Dinosaur> target = dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.TRAP)
                .min(Comparator.comparingInt(d -> d.position.manhattan(player.engineer)));

        if (target.isEmpty()) return false;

        Point predicted = predictNextBioStep(target.get());
        if (predicted != null && map.isPlaced(predicted)) {
            player.traps.add(new Trap(player.id, predicted));
            player.engineer = predicted;
            log("Игрок " + player.id + " поставил ловушку на " + predicted);
            return true;
        }

        return false;
    }

    private boolean attemptCapture(PlayerState player) {
        List<Dinosaur> needed = dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .sorted(Comparator.comparingInt(d -> d.position.manhattan(player.hunter)))
                .toList();

        for (Dinosaur dino : needed) {
            if (dino.species.captureMethod == CaptureMethod.TRACKING) {
                if (player.hunter.manhattan(dino.position) <= 1) {
                    double chance = config.trackingBaseSuccess + config.trackingStepBonus * random.nextInt(config.trackingMaxSteps);
                    if (random.nextDouble() < chance) {
                        capture(player, dino, "выслеживание");
                        result.trackingCaptures++;
                    }
                    return true;
                }

                player.hunter = stepTowardPlaced(player.hunter, dino.position);
                return true;
            }

            if (dino.species.captureMethod == CaptureMethod.HUNT) {
                if (player.hunterBait <= 0) return false;

                if (player.hunter.manhattan(dino.position) <= 2) {
                    double chance = random.nextBoolean() ? config.huntBaseSuccess : config.huntPreparedSuccess;
                    if (random.nextDouble() < chance) {
                        capture(player, dino, "охота");
                        result.huntCaptures++;
                    }
                    player.hunterBait--;
                    return true;
                }

                player.hunter = stepTowardPlaced(player.hunter, dino.position);
                return true;
            }
        }

        return false;
    }

    private void dinosaurPhase() {
        for (Dinosaur dino : new ArrayList<>(dinosaurs)) {
            if (dino.captured || dino.removed) continue;

            if (dino.species == Species.VELOCITAURUS) {
                huntWithVelocitaurus(dino);
            }

            Point before = dino.position;
            dino.lastPosition = before;
            moveByBioTrail(dino);

            if (!before.equals(dino.position)) {
                log(dino.species.displayName + " #" + dino.id + " " + before + " -> " + dino.position);
                checkTrapCapture(dino);
            }

            if (dino.species == Species.CRYPTOGNATH) {
                stealBaitIfPossible(dino);
            }

            if (dino.species == Species.VELOCITAURUS) {
                attackRangerIfPossible(dino);
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

    private void moveByBioTrail(Dinosaur dino) {
        Point target = predictNextBioTarget(dino);

        if (target == null) {
            Point frontier = map.nearestUnexploredFrontier(dino.position);
            if (frontier == null) return;

            Point next = stepTowardFrontier(dino.position, frontier);
            if (!map.isPlaced(next) && next.equals(frontier)) {
                dino.removed = true;
                log(dino.species.displayName + " #" + dino.id + " ушёл в неизвестную часть острова " + frontier);
            } else {
                dino.position = next;
            }
            return;
        }

        int steps = Math.max(1, Math.min(dino.species.agility, 3));
        for (int i = 0; i < steps; i++) {
            if (dino.position.equals(target)) break;
            dino.position = stepTowardPlaced(dino.position, target);
        }

        Tile current = map.tile(dino.position);
        Biome nextBiome = dino.species.bioTrail.get((dino.trailIndex + 1) % dino.species.bioTrail.size());
        if (current != null && current.biome == nextBiome) {
            dino.trailIndex = (dino.trailIndex + 1) % dino.species.bioTrail.size();
        }
    }

    private Point predictNextBioStep(Dinosaur dino) {
        Point target = predictNextBioTarget(dino);
        if (target == null) return null;
        return stepTowardPlaced(dino.position, target);
    }

    private Point predictNextBioTarget(Dinosaur dino) {
        Biome nextBiome = dino.species.bioTrail.get((dino.trailIndex + 1) % dino.species.bioTrail.size());
        return map.nearestPlacedBiome(dino.position, nextBiome);
    }

    private Point stepTowardPlaced(Point from, Point target) {
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

    private boolean isPassable(Point p) {
        Tile tile = map.tile(p);
        return tile != null && !tile.biome.blocksMostMovement();
    }

    private void checkTrapCapture(Dinosaur dino) {
        if (dino.species.captureMethod != CaptureMethod.TRAP) return;

        for (PlayerState player : players) {
            if (!player.needs(dino.species)) continue;

            for (Trap trap : player.traps) {
                if (trap.active && trap.position.equals(dino.position)) {
                    trap.active = false;
                    capture(player, dino, "ловушка");
                    result.trapCaptures++;
                    return;
                }
            }
        }
    }

    private void stealBaitIfPossible(Dinosaur dino) {
        for (PlayerState player : players) {
            if (player.hunterBait > 0 && player.hunter.equals(dino.position)) {
                player.hunterBait--;
                log("Криптогнат украл приманку у игрока " + player.id);
            }
        }
    }

    private void attackRangerIfPossible(Dinosaur dino) {
        for (PlayerState player : players) {
            boolean hunterNearby = player.hunter.manhattan(dino.position) <= dino.species.huntRadius;
            boolean engineerNearby = player.engineer.manhattan(dino.position) <= dino.species.huntRadius;
            boolean driverNearby = player.driver.manhattan(dino.position) <= dino.species.huntRadius;

            /*
             * Разведчика не трогаем: по правилам динозавры на него не обращают внимания.
             */
            if ((hunterNearby || engineerNearby || driverNearby) && random.nextDouble() < 0.20) {
                player.turnsSkipped = 1;
                player.returnTeamToBase(map.base);
                log("Велоцитаурус напал на команду игрока " + player.id);
            }
        }
    }

    private void capture(PlayerState player, Dinosaur dino, String method) {
        dino.captured = true;
        player.captured.add(dino.species);
        log("ПОЙМАН: игрок " + player.id + " поймал " + dino.species.displayName + " (" + method + ")");
    }

    private void updateResult() {
        result.rounds = round;
        result.openedTiles = map.openedCount();
        result.spawnedDinosaurs = dinosaurs.size();
        result.capturedDinosaurs = (int) dinosaurs.stream().filter(d -> d.captured).count();
        result.completedPlayers = (int) players.stream().filter(PlayerState::isComplete).count();
    }

    private void log(String message) {
        log.addFirst(message);
        while (log.size() > 18) log.removeLast();
    }
}
