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

/**
 * Класс отвечает за подсчёт очков, за очередность ходов игрока и за прочими общими(системными) игровыми механиками.
 */
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
