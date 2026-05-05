package ru.mesozoa.sim.simulation;

import ru.mesozoa.sim.action.RangerActionExecutor;
import ru.mesozoa.sim.ranger.ai.RangerTurnPlanner;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.config.InventoryConfig;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.dinosaur.DinosaurAi;
import ru.mesozoa.sim.dinosaur.DinosaurActionPlanner;
import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.report.GameResult;
import ru.mesozoa.sim.tile.TileBag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.EnumSet;
import java.util.Set;

/**
 * Класс отвечает за подсчёт очков, за очередность ходов игрока и за прочими общими(системными) игровыми механиками.
 */
public final class GameSimulation {

    public final GameConfig gameConfig;

    public final InventoryConfig inventoryConfig;

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

    /** AI и прогнозы движения динозавров. */
    public DinosaurAi dinosaurAi;

    /** Исполнитель перемещения динозавров в фазу «Динозавры живут». */
    private DinosaurActionPlanner dinosaurActionPlanner;

    /** Технический резолвер последствий после завершения перемещения динозавров. */
    private DinosaurPhaseConsequenceResolver dinosaurPhaseConsequenceResolver;

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
                          long seed) {

        this.gameConfig = gameConfig;
        this.inventoryConfig = inventoryConfig;
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

        dinosaurAi = new DinosaurAi(this);
        dinosaurActionPlanner = new DinosaurActionPlanner(this, dinosaurAi);
        dinosaurPhaseConsequenceResolver = new DinosaurPhaseConsequenceResolver(this);
        rangerTurnPlanner = new RangerTurnPlanner(this);
        rangerActionExecutor = new RangerActionExecutor(this);

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
        dinosaurActionPlanner.dinosaurPhase(activeTrackingDinosaurIds());
        dinosaurPhaseConsequenceResolver.resolveAfterDinosaurPhase();
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
        log("РАУНД-" + round);
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
                .min(Comparator.comparingInt(dinosaur -> from == null ? 0 : dinosaur.position.chebyshev(from)));
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

        dinosaur.trapped = false;
        dinosaur.trappedByPlayerId = 0;
        dinosaur.captured = true;
        player.captured.add(dinosaur.species);
        player.clearCaptureFailures(dinosaur.id);

        trap.ifPresent(value -> {
            value.trappedDinosaurId = 0;
            value.active = false;
        });
        clearTrailMarkerHoldingDinosaur(player, dinosaur);

        log("ДОСТАВЛЕН: водитель игрока " + player.id
                + " вывез " + dinosaur.displayName
                + " #" + dinosaur.id + " на базу"
                + (trap.isPresent() ? " из ловушки" : " по жетону следа"));
        return true;
    }

    /**
     * Снимает жетон следа, которым отмечали обездвиженного по выслеживанию динозавра.
     *
     * @param player владелец жетона
     * @param dinosaur доставленный динозавр
     */
    private void clearTrailMarkerHoldingDinosaur(PlayerState player, Dinosaur dinosaur) {
        player.trailTokens.removeIf(token -> token.captureMarker && token.dinosaurId == dinosaur.id);
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


    /**
     * Возвращает динозавров, которые сейчас не должны делать обычный шаг фазы
     * «Динозавры живут», потому что их движение управляется фазой выслеживания.
     *
     * @return ID динозавров в активных цепочках следов
     */
    private Set<Integer> activeTrackingDinosaurIds() {
        return players.stream()
                .map(player -> player.activeTracking)
                .filter(tracking -> tracking != null)
                .map(tracking -> tracking.dinosaurId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void updateResult() {
        result.rounds = round;
        result.openedTiles = map.openedCount();
        result.spawnedDinosaurs = dinosaurs.size();
        result.capturedDinosaurs = (int) dinosaurs.stream().filter(d -> d.captured).count();
        result.completedPlayers = (int) players.stream().filter(PlayerState::isComplete).count();
    }

    /**
     * Добавляет сообщение в журнал партии.
     * Журнал хранит заметно больше строк, чем помещается в HUD: правый лог теперь
     * прокручивается, поэтому старые события можно дочитать, а не гадать по
     * обрывкам, как археолог по одному зубу динозавра.
     *
     * @param message текст сообщения
     */
    public void log(String message) {
        log.addFirst(message);
        while (log.size() > 500) log.removeLast();
    }
}
