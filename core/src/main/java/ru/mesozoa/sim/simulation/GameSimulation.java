package ru.mesozoa.sim.simulation;

import ru.mesozoa.sim.action.RangerActionExecutor;
import ru.mesozoa.sim.ranger.ai.RangerTurnPlanner;
import ru.mesozoa.sim.rules.HeadquartersTaskGenerator;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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

    /** Генератор случайных заданий штаба для игроков. */
    private HeadquartersTaskGenerator headquartersTaskGenerator;

    /** AI и прогнозы движения динозавров. */
    public DinosaurAi dinosaurAi;

    /** Исполнитель перемещения динозавров в фазу «Динозавры живут». */
    private DinosaurActionPlanner dinosaurActionPlanner;

    /** Технический резолвер последствий после завершения перемещения динозавров. */
    private DinosaurPhaseConsequenceResolver dinosaurPhaseConsequenceResolver;

    private boolean roundStarted = false;
    private int activeRangerIndex = 0;

    /** Внешний слушатель полного журнала для headless-прогонов баланса. */
    private Consumer<String> logListener;

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
        headquartersTaskGenerator = new HeadquartersTaskGenerator(gameConfig);

        List<Species> headquartersTaskDeck = headquartersTaskGenerator.createTaskDeck(random);
        for (int i = 0; i < gameConfig.players; i++) {
            int playerId = i + 1;
            PlayerState player = new PlayerState(playerId, RangerColor.forPlayerId(playerId), map.base);
            assignTask(player, headquartersTaskDeck);
            players.add(player);
            log("Игрок " + player.id + " (" + player.color.assetSuffix + ") карты штаба: " + taskToText(player));
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
                executeMandatoryScoutAction(player);
                activePlayerUsedRoles.add(RangerRole.SCOUT);
                updateResult();
                checkGameOverAfterPartialStep();
                if (gameOver) return;
            }

            RangerPlan plan = rangerTurnPlanner.chooseNextPlanForTurn(player, activePlayerUsedRoles);

            if (plan == null) {
                log("Игрок " + player.id + ": планировщик пропустил ход как стратегически более полезное действие, чем пустая активация");
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
     * Подключает внешний обработчик полного журнала партии.
     *
     * HUD хранит только ограниченный хвост сообщений, а batch-прогон баланса
     * должен видеть все события партии: выбор AI, провалы охоты, дороги, ловушки
     * и прочие маленькие радости автоматизированного зоопарка.
     *
     * @param logListener обработчик новых сообщений или null, если внешний сбор не нужен
     */
    public void setLogListener(Consumer<String> logListener) {
        this.logListener = logListener;
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

    /**
     * Выполняет обязательную бесплатную разведку в начале хода игрока.
     *
     * Разведчик больше не конкурирует за две обычные активации. Каждый игрок
     * сначала открывает тайл, а уже затем выбирает двух специалистов из
     * инженера, охотника и водителя. Да, вертолёт наконец-то перестал спорить
     * с инженером за рабочее время, цивилизация шевельнулась.
     *
     * @param player игрок, чей разведчик действует
     */
    private void executeMandatoryScoutAction(PlayerState player) {
        RangerPlan scoutPlan = new RangerPlan(
                player.scoutRanger,
                ru.mesozoa.sim.ranger.RangerPlanType.SCOUT_EXPLORE,
                new AiScore(100.0, "обязательная разведка в начале хода"),
                null
        );
        log("Обязательная разведка игрока " + player.id);
        rangerActionExecutor.executePlan(player, scoutPlan);
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
        Optional<String> endReason = gameEndReason();
        if (endReason.isPresent()) {
            gameOver = true;
            log(endReason.get());
            logFinalStandings();
            return;
        }

        roundStarted = false;
        activeRangerIndex = 0;
        activePlayerUsedRoles = EnumSet.noneOf(RangerRole.class);
        activePlayerActionIndex = 0;
    }

    private void checkGameOverAfterPartialStep() {
        Optional<String> endReason = gameEndReason();
        if (endReason.isPresent()) {
            gameOver = true;
            log(endReason.get());
            logFinalStandings();
        }
    }

    /**
     * Возвращает причину завершения партии, если выполнено одно из условий конца.
     *
     * В очковом режиме партия может закончиться не только по лимиту раундов или
     * полному открытию основных тайлов, но и сразу после достижения победного
     * порога очков. Это делает партию короче и не заставляет лидера досиживать
     * формальные раунды, пока остальные изображают экспедиционный комитет.
     *
     * @return текст причины завершения или пустой результат
     */
    private Optional<String> gameEndReason() {
        if (gameConfig.endWhenCapturePointsReached && topCapturePoints() >= gameConfig.capturePointsToWin) {
            PlayerState leader = players.stream()
                    .max(Comparator.comparingInt(player -> player.capturePoints))
                    .orElse(null);
            return Optional.of("Партия завершена: игрок "
                    + (leader == null ? "?" : leader.id)
                    + " набрал " + topCapturePoints()
                    + " очк. из " + gameConfig.capturePointsToWin);
        }

        if (round >= gameConfig.maxRounds) {
            return Optional.of("Партия завершена: достигнут лимит раундов " + gameConfig.maxRounds);
        }

        if (gameConfig.endWhenMainTileBagIsEmpty && tileBag.isEmpty()) {
            return Optional.of("Партия завершена: открыты все основные тайлы острова");
        }

        if (gameConfig.endByCompletedPlayers && hasEnoughCompletedPlayersToEnd()) {
            return Optional.of("Партия завершена: выполнено заданий "
                    + completedPlayersCount() + " / " + players.size()
                    + ", порог завершения " + completedPlayersToEnd());
        }

        return Optional.empty();
    }

    /** Возвращает максимальное число очков среди игроков. */
    private int topCapturePoints() {
        return players.stream()
                .mapToInt(player -> player.capturePoints)
                .max()
                .orElse(0);
    }

    /**
     * Пишет итоговую таблицу мест по текущим очкам.
     */
    private void logFinalStandings() {
        ArrayList<PlayerState> standings = new ArrayList<>(players);
        standings.sort(Comparator
                .comparingInt((PlayerState player) -> player.capturePoints).reversed()
                .thenComparingInt((PlayerState player) -> player.deliveredDinosaurs).reversed()
                .thenComparingInt(player -> player.id));

        int place = 1;
        for (PlayerState player : standings) {
            log(place + " место: игрок " + player.id
                    + " — " + player.capturePoints + " очк., доставлено "
                    + player.deliveredDinosaurs + " динозавров");
            place++;
        }
    }

    /**
     * Проверяет новое условие автоматического завершения партии.
     *
     * По умолчанию партия заканчивается, когда все игроки кроме одного закрыли
     * задание штаба: для дуэли это 1/2, для партии на четверых — 3/4.
     *
     * @return true, если достигнут порог завершения
     */
    private boolean hasEnoughCompletedPlayersToEnd() {
        return completedPlayersCount() >= completedPlayersToEnd();
    }

    /** Возвращает число игроков, выполнивших задание штаба. */
    private int completedPlayersCount() {
        return (int) players.stream().filter(PlayerState::isComplete).count();
    }

    /**
     * Возвращает порог завершения партии по текущему конфигу.
     *
     * @return число игроков, после которого партия автоматически заканчивается
     */
    public int completedPlayersToEnd() {
        if (gameConfig.completedPlayersToEnd > 0) {
            return Math.min(players.size(), gameConfig.completedPlayersToEnd);
        }
        return Math.max(1, players.size() - 1);
    }

    /**
     * Выдаёт игроку карты задания штаба из общей колоды партии.
     *
     * @param player игрок, которому назначаются карты
     * @param taskDeck общая перемешанная колода задания
     */
    private void assignTask(PlayerState player, List<Species> taskDeck) {
        player.addTaskCards(headquartersTaskGenerator.drawTaskCards(taskDeck));
    }

    private String taskToText(PlayerState player) {
        ArrayList<String> names = new ArrayList<>();
        for (Species species : player.taskCards) {
            names.add(species.displayName + " x2");
        }
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
     * Возвращает ожидаемую ценность доставки динозавра для игрока.
     *
     * Базовые очки зависят от метода поимки, а незакрытая карта задания этого
     * вида удваивает очки за следующую доставку.
     *
     * @param player игрок, для которого считается ценность
     * @param dinosaur потенциальная добыча
     * @return сколько очков принесёт доставка прямо сейчас
     */
    public int capturePointsForPlayer(PlayerState player, Dinosaur dinosaur) {
        if (player == null || dinosaur == null) return 0;
        return capturePointsFor(dinosaur) * player.scoreMultiplierFor(dinosaur.species);
    }

    /**
     * Проверяет, стоит ли AI активно ловить динозавра в очковом режиме.
     *
     * Сейчас любой вид приносит очки, поэтому ответ почти всегда true. Метод
     * нужен, чтобы стратегии не были размазаны по коду: если завтра появятся
     * штрафы за лишних динозавров или запретные виды, править придётся здесь,
     * а не в каждом AI-классе, как любят делать люди перед катастрофой.
     */
    public boolean isWorthCapturing(PlayerState player, Dinosaur dinosaur) {
        return dinosaur != null
                && !dinosaur.captured
                && !dinosaur.trapped
                && !dinosaur.removed
                && capturePointsForPlayer(player, dinosaur) > 0;
    }

    /**
     * Ищет ближайшего обездвиженного динозавра, которого игроку выгодно вывезти.
     *
     * В конкурентном режиме у добычи на карте нет владельца: водитель любого
     * игрока может присоединить свою дорогу к клетке и увезти зверя. Поэтому
     * цель выбирается не по владельцу ловушки, а по выгоде для текущего игрока:
     * сначала виды из задания штаба, затем бонусная добыча ради очков.
     *
     * @param player игрок, для которого ищется вывоз
     * @param from клетка, от которой считается близость
     * @param requireDriverRoute true, если нужен уже готовый маршрут водителя
     * @param requireTaskSpecies true, если учитывать только виды из задания
     * @return ближайший ожидающий вывоза динозавр
     */
    public Optional<Dinosaur> nearestAwaitingPickupDinosaur(
            PlayerState player,
            Point from,
            boolean requireDriverRoute,
            boolean requireTaskSpecies
    ) {
        return dinosaurs.stream()
                .filter(this::isAwaitingPickup)
                .filter(dinosaur -> !requireTaskSpecies || player.needs(dinosaur.species))
                .filter(dinosaur -> !requireDriverRoute || canDriverExtractTrappedDinosaur(player, dinosaur))
                .min(Comparator
                        .comparingInt((Dinosaur dinosaur) -> player.needs(dinosaur.species) ? 0 : 1)
                        .thenComparingInt(dinosaur -> from == null ? 0 : dinosaur.position.chebyshev(from)));
    }

    /**
     * Совместимый метод для старой логики AI: теперь он ищет не «свою» ловушку,
     * а любого нужного обездвиженного динозавра, которого можно украсть или вывезти.
     */
    public Optional<Dinosaur> nearestTrappedNeededDinosaurAwaitingPickup(
            PlayerState player,
            Point from,
            boolean requireDriverRoute
    ) {
        return nearestAwaitingPickupDinosaur(player, from, requireDriverRoute, true);
    }

    /**
     * Проверяет, ждёт ли динозавр вывоза на базу.
     *
     * @param dinosaur проверяемая фишка
     * @return true, если зверь обездвижен, но ещё не доставлен
     */
    public boolean isAwaitingPickup(Dinosaur dinosaur) {
        return dinosaur != null
                && dinosaur.trapped
                && !dinosaur.captured
                && !dinosaur.removed;
    }

    /**
     * Проверяет, является ли обездвиженный динозавр полезной целью для игрока.
     *
     * @param dinosaur динозавр на карте
     * @param player игрок, который может его вывезти
     * @return true, если динозавр ждёт вывоза и закрывает задание игрока
     */
    public boolean isAwaitingPickupForPlayer(Dinosaur dinosaur, PlayerState player) {
        return isAwaitingPickup(dinosaur) && player != null && player.needs(dinosaur.species);
    }

    /**
     * Старое имя оставлено для совместимости с существующим AI-кодом.
     * В новом конкурентном правиле оно больше не проверяет владельца ловушки.
     */
    public boolean isTrappedByPlayer(Dinosaur dinosaur, PlayerState player) {
        return isAwaitingPickupForPlayer(dinosaur, player);
    }

    /**
     * Проверяет, может ли водитель вывезти обездвиженного динозавра за одну активацию.
     *
     * Водитель тратит активацию как полный рейс: доехать по связанной дорожной
     * сети до добычи и вернуться на базу уже с динозавром. Именно это открывает
     * прямую конкуренцию: чужая ловушка без собственной дороги — это просто
     * подарок для более расторопного игрока.
     *
     * @param player игрок, чей водитель проверяется
     * @param dinosaur динозавр, ожидающий вывоза
     * @return true, если есть путь от водителя до добычи и обратно на базу
     */
    public boolean canDriverExtractTrappedDinosaur(PlayerState player, Dinosaur dinosaur) {
        return isAwaitingPickup(dinosaur)
                && player != null
                && map.hasDriverPath(player.driverRanger.position(), dinosaur.position)
                && map.hasDriverPath(dinosaur.position, map.base);
    }

    /**
     * Доставляет обездвиженного динозавра на базу и начисляет очки водителю.
     *
     * Зачёт получает не тот, кто поставил ловушку или начал охоту, а тот, чей
     * водитель реально привёз зверя на базу. Если вид входит в задание этого
     * игрока, цель штаба закрывается; если нет — игрок всё равно получает очки.
     *
     * @param player игрок, чей водитель сделал рейс
     * @param dinosaur динозавр, ожидающий вывоза
     * @return true, если вывоз выполнен
     */
    public boolean extractTrappedDinosaurToBase(PlayerState player, Dinosaur dinosaur) {
        if (!canDriverExtractTrappedDinosaur(player, dinosaur)) {
            return false;
        }

        int originalHolderId = dinosaur.trappedByPlayerId;
        Optional<Trap> trap = trapHoldingDinosaur(dinosaur);
        int basePoints = capturePointsFor(dinosaur);
        int multiplier = player.scoreMultiplierFor(dinosaur.species);
        int points = basePoints * multiplier;
        boolean taskCapture = player.needs(dinosaur.species);

        dinosaur.trapped = false;
        dinosaur.trappedByPlayerId = 0;
        dinosaur.captured = true;
        player.registerDeliveredDinosaur(dinosaur.species, points);
        player.clearCaptureFailures(dinosaur.id);

        trap.ifPresent(value -> {
            value.trappedDinosaurId = 0;
            value.active = false;
        });
        clearTrailMarkerHoldingDinosaur(dinosaur);

        String theftText = originalHolderId != 0 && originalHolderId != player.id
                ? "; добыча уведена у игрока " + originalHolderId
                : "";
        String taskText = taskCapture ? "; цель штаба закрыта" : "; бонусная добыча";

        log("ДОСТАВЛЕН: водитель игрока " + player.id
                + " вывез " + dinosaur.displayName
                + " #" + dinosaur.id + " на базу"
                + " (+" + points + " очк." + (multiplier > 1 ? ", x" + multiplier + " за карту штаба" : "") + ")"
                + taskText
                + theftText
                + (trap.isPresent() ? " из ловушки" : " по маркеру поимки"));
        return true;
    }

    /**
     * Возвращает победные очки за доставленного динозавра.
     *
     * @param dinosaur доставленный динозавр
     * @return очки: S = 1, M-травоядный = 2, M-хищник = 3
     */
    public int capturePointsFor(Dinosaur dinosaur) {
        if (dinosaur == null) return 0;
        return switch (dinosaur.captureMethod) {
            case TRAP -> 1;
            case TRACKING -> 2;
            case HUNT -> 3;
        };
    }

    /**
     * Снимает любой жетон следа, которым отмечали обездвиженного динозавра.
     *
     * @param dinosaur доставленный динозавр
     */
    private void clearTrailMarkerHoldingDinosaur(Dinosaur dinosaur) {
        for (PlayerState player : players) {
            player.trailTokens.removeIf(token -> token.captureMarker && token.dinosaurId == dinosaur.id);
        }
    }

    /**
     * Ищет любую ловушку на карте, которая удерживает конкретного динозавра.
     *
     * @param dinosaur динозавр в ловушке
     * @return ловушка с указанным динозавром
     */
    private Optional<Trap> trapHoldingDinosaur(Dinosaur dinosaur) {
        return players.stream()
                .flatMap(player -> player.traps.stream())
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
                .filter(Objects::nonNull)
                .map(tracking -> tracking.dinosaurId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void updateResult() {
        result.rounds = round;
        result.openedTiles = map.openedCount();
        result.spawnedDinosaurs = dinosaurs.size();
        result.capturedDinosaurs = (int) dinosaurs.stream().filter(d -> d.captured).count();
        result.completedPlayers = (int) players.stream().filter(PlayerState::isComplete).count();
        result.totalCapturePoints = players.stream().mapToInt(player -> player.capturePoints).sum();
        result.topCapturePoints = topCapturePoints();
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
        if (logListener != null) {
            logListener.accept(message);
        }
    }
}
