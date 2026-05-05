package ru.mesozoa.sim.report;

import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.config.InventoryConfig;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.simulation.GameMap;
import ru.mesozoa.sim.simulation.GameSimulation;
import ru.mesozoa.sim.tile.Tile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Headless-прогонщик партий для проверки баланса и поведения AI.
 *
 * Класс лежит в test-sourceSet и запускается отдельной Gradle-задачей
 * {@code :core:runBalanceReport}. Он не требует JUnit: это утилитарный
 * симуляционный тест, который гоняет много партий, собирает метрики по логам и
 * финальному состоянию карты, а затем пишет Markdown-отчёт и CSV-файлы.
 */
public final class MesozoaBatchReportRunner {

    private MesozoaBatchReportRunner() {
    }

    /**
     * Запускает серию партий с параметрами из system properties.
     *
     * Поддерживаемые параметры: {@code meso.games}, {@code meso.players},
     * {@code meso.maxRounds}, {@code meso.seedStart},
     * {@code meso.taskDinosaurCount}, {@code meso.outputDir}.
     *
     * @param args не используются; параметры передаются через Gradle properties
     * @throws IOException если не удалось записать отчёт
     */
    public static void main(String[] args) throws IOException {
        RunnerConfig config = RunnerConfig.fromSystemProperties();
        Files.createDirectories(config.outputDir());

        ArrayList<GameStats> games = new ArrayList<>();
        ArrayList<PlayerSpeciesRow> playerSpeciesRows = new ArrayList<>();

        long startedAt = System.nanoTime();
        for (int i = 0; i < config.games(); i++) {
            long seed = config.seedStart() + i;
            GameStats stats = runSingleGame(i + 1, seed, config);
            games.add(stats);
            playerSpeciesRows.addAll(stats.playerSpeciesRows());
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        ReportWriter writer = new ReportWriter(config, games, playerSpeciesRows, elapsedMillis);
        writer.writeAll();

        System.out.println("Mesozoa balance report written to: " + config.outputDir().toAbsolutePath());
        System.out.println("Markdown: " + config.outputDir().resolve("mesozoa_batch_report.md").toAbsolutePath());
    }

    /**
     * Прогоняет одну партию до завершения или до защитного лимита микрошагов.
     *
     * @param index порядковый номер партии в batch-прогоне
     * @param seed seed партии
     * @param config параметры batch-прогона
     * @return собранная статистика партии
     */
    private static GameStats runSingleGame(int index, long seed, RunnerConfig config) {
        GameConfig gameConfig = new GameConfig();
        gameConfig.players = config.players();
        gameConfig.maxRounds = config.maxRounds();
        gameConfig.headquartersTaskDinosaurCount = config.taskDinosaurCount();

        GameSimulation simulation = new GameSimulation(gameConfig, new InventoryConfig(), seed);
        GameStats stats = new GameStats(index, seed, config.players(), simulation);
        simulation.setLogListener(message -> stats.recordLog(simulation.round, message));

        int microSteps = 0;
        int microStepLimit = Math.max(1_000, config.maxRounds() * (config.players() * 8 + 32));
        while (!simulation.gameOver && microSteps < microStepLimit) {
            simulation.stepOneTurn();
            stats.observeCaptures(simulation);
            microSteps++;
        }

        if (!simulation.gameOver) {
            stats.microStepLimitHit = true;
        }

        stats.finish(simulation, microSteps);
        return stats;
    }

    /** Параметры batch-прогона. */
    private record RunnerConfig(
            int games,
            int players,
            int maxRounds,
            long seedStart,
            int taskDinosaurCount,
            Path outputDir
    ) {
        private static RunnerConfig fromSystemProperties() {
            return new RunnerConfig(
                    intProperty("meso.games", 100),
                    intProperty("meso.players", 2),
                    intProperty("meso.maxRounds", 280),
                    longProperty("meso.seedStart", 20260505L),
                    intProperty("meso.taskDinosaurCount", 3),
                    Path.of(System.getProperty("meso.outputDir", "build/reports/mesozoa-balance"))
            );
        }

        private static int intProperty(String name, int fallback) {
            String value = System.getProperty(name);
            if (value == null || value.isBlank()) return fallback;
            return Integer.parseInt(value.trim());
        }

        private static long longProperty(String name, long fallback) {
            String value = System.getProperty(name);
            if (value == null || value.isBlank()) return fallback;
            return Long.parseLong(value.trim());
        }
    }

    /** Сводная статистика одной партии. */
    private static final class GameStats {
        final int gameIndex;
        final long seed;
        final int expectedPlayers;
        final ArrayList<PlayerSpeciesRow> playerSpeciesRows = new ArrayList<>();
        final EnumMap<CaptureMethod, Integer> taskByMethod = new EnumMap<>(CaptureMethod.class);
        final EnumMap<CaptureMethod, Integer> capturedByMethod = new EnumMap<>(CaptureMethod.class);
        final EnumMap<Species, SpeciesStats> speciesStats = new EnumMap<>(Species.class);
        final ArrayList<RoundLog> interestingLogs = new ArrayList<>();
        final Map<Integer, Set<Species>> observedCapturedByPlayer = new HashMap<>();

        int rounds;
        int openedTiles;
        int spawnedDinosaurs;
        int capturedDinosaurs;
        int completedPlayers;
        int microSteps;
        int finalRoadSegments;
        int finalBridges;
        int activeHuntsAtEnd;
        int activeTrackingAtEnd;
        int trappedAwaitingPickupAtEnd;
        int visibleUncapturedTaskSpeciesAtEnd;
        int hiddenUncapturedTaskSpeciesAtEnd;
        boolean microStepLimitHit;

        int plannerSkips;
        int plannerBestSkipSignals;
        int noAvailableRanger;
        int scoutNoTiles;
        int engineerNoUseful;
        int engineerCannotAdvanceRoad;
        int engineerNoLegalTrapCell;
        int roadBuilds;
        int bridgeBuilds;
        int trapPlacementEvents;
        int trapTokensPlaced;
        int trapRepositions;
        int staleTrapsRecovered;
        int driverWaits;
        int driverCannotReach;
        int driverNotEnoughActions;
        int driverArrivals;
        int deliveries;
        int huntStarts;
        int huntCardDraws;
        int huntWaits;
        int huntFailures;
        int huntRelocations;
        int trackingStarts;
        int trackingAttempts;
        int trackingTrailMoves;
        int trackingStalls;
        int trackingFailures;
        int cryptognathBaitThefts;
        int predatorKills;

        GameStats(int gameIndex, long seed, int expectedPlayers, GameSimulation simulation) {
            this.gameIndex = gameIndex;
            this.seed = seed;
            this.expectedPlayers = expectedPlayers;

            for (CaptureMethod method : CaptureMethod.values()) {
                taskByMethod.put(method, 0);
                capturedByMethod.put(method, 0);
            }
            for (Species species : Species.values()) {
                speciesStats.put(species, new SpeciesStats(species));
            }

            for (PlayerState player : simulation.players) {
                observedCapturedByPlayer.put(player.id, EnumSet.noneOf(Species.class));
                for (Species species : player.task) {
                    CaptureMethod method = Dinosaur.captureMethodOf(species);
                    taskByMethod.merge(method, 1, Integer::sum);
                    speciesStats.get(species).taskCount++;
                    playerSpeciesRows.add(new PlayerSpeciesRow(gameIndex, seed, player.id, species, method));
                }
            }
        }

        void recordLog(int round, String message) {
            if (message.contains("планировщик пропустил ход")) plannerSkips++;
            if (message.contains("стратегически лучше пропустить")) plannerBestSkipSignals++;
            if (message.contains("нет доступного рейнджера")) noAvailableRanger++;
            if (message.contains("не может разведать новый участок")) scoutNoTiles++;
            if (message.contains("не нашёл полезного инженерного действия")) engineerNoUseful++;
            if (message.contains("не смог приблизить дорогу")) engineerCannotAdvanceRoad++;
            if (message.contains("не нашёл легальной клетки для ловушки")) engineerNoLegalTrapCell++;
            if (message.contains("построил дорогу")) roadBuilds++;
            if (message.contains("построил мост")) bridgeBuilds++;
            if (message.contains("выставил ловушки")) {
                trapPlacementEvents++;
                trapTokensPlaced += trailingInteger(message);
            }
            if (message.contains("переставил ловушки")) {
                trapRepositions++;
                trapTokensPlaced += trailingInteger(message);
            }
            if (message.contains("снял старую ловушку")) staleTrapsRecovered++;
            if (message.contains("ждёт задачи: вывозить пока некого")) driverWaits++;
            if (message.contains("не может доехать") || message.contains("нет связанной дороги")) driverCannotReach++;
            if (message.contains("не хватает действий")) driverNotEnoughActions++;
            if (message.contains("ВОДИТЕЛЬ ПРИЕХАЛ")) driverArrivals++;
            if (message.contains("ДОСТАВЛЕН")) deliveries++;
            if (message.contains("начал засаду")) huntStarts++;
            if (message.contains("добрал карту охоты")) huntCardDraws++;
            if (message.contains("ждёт в засаде")) huntWaits++;
            if (message.contains("ПРОВАЛ ОХОТЫ")) huntFailures++;
            if (message.contains("переносит засаду")) huntRelocations++;
            if (message.contains("начал выслеживание")) trackingStarts++;
            if (message.startsWith("Выслеживание ")) trackingAttempts++;
            if (message.contains("оставил след")) trackingTrailMoves++;
            if (message.contains("идёт по следу")) trackingStalls++;
            if (message.contains("ПРОВАЛ ВЫСЛЕЖИВАНИЯ")) trackingFailures++;
            if (message.contains("Криптогнат украл приманку")) cryptognathBaitThefts++;
            if (message.contains("съел рейнджера")) predatorKills++;

            if (isInteresting(message)) {
                interestingLogs.add(new RoundLog(round, message));
            }
        }

        void observeCaptures(GameSimulation simulation) {
            for (PlayerState player : simulation.players) {
                Set<Species> observed = observedCapturedByPlayer.get(player.id);
                for (Species species : player.captured) {
                    if (observed.add(species)) {
                        CaptureMethod method = Dinosaur.captureMethodOf(species);
                        capturedByMethod.merge(method, 1, Integer::sum);
                        speciesStats.get(species).capturedCount++;
                        speciesStats.get(species).captureRounds.add(simulation.round);
                        playerSpeciesRows.stream()
                                .filter(row -> row.playerId == player.id && row.species == species)
                                .findFirst()
                                .ifPresent(row -> row.captureRound = simulation.round);
                    }
                }
            }
        }

        void finish(GameSimulation simulation, int microSteps) {
            observeCaptures(simulation);
            this.rounds = simulation.round;
            this.openedTiles = simulation.result.openedTiles;
            this.spawnedDinosaurs = simulation.result.spawnedDinosaurs;
            this.capturedDinosaurs = simulation.result.capturedDinosaurs;
            this.completedPlayers = simulation.result.completedPlayers;
            this.microSteps = microSteps;
            this.finalRoadSegments = countRoadSegments(simulation.map);
            this.finalBridges = countBridges(simulation.map);
            this.activeHuntsAtEnd = (int) simulation.players.stream().filter(player -> player.activeHunt != null).count();
            this.activeTrackingAtEnd = (int) simulation.players.stream().filter(player -> player.activeTracking != null).count();
            this.trappedAwaitingPickupAtEnd = (int) simulation.dinosaurs.stream()
                    .filter(dinosaur -> dinosaur.trapped && !dinosaur.captured && !dinosaur.removed)
                    .count();

            for (Map.Entry<Species, Integer> entry : simulation.result.firstSpawnRound.entrySet()) {
                speciesStats.get(entry.getKey()).firstSpawnRounds.add(entry.getValue());
            }

            for (PlayerSpeciesRow row : playerSpeciesRows) {
                Integer spawnRound = simulation.result.firstSpawnRound.get(row.species);
                if (spawnRound != null) row.firstSpawnRound = spawnRound;
            }

            for (PlayerState player : simulation.players) {
                for (Species species : player.task) {
                    if (player.captured.contains(species)) continue;
                    boolean visible = simulation.dinosaurs.stream()
                            .anyMatch(dinosaur -> dinosaur.species == species && !dinosaur.removed && !dinosaur.captured);
                    if (visible) visibleUncapturedTaskSpeciesAtEnd++;
                    else hiddenUncapturedTaskSpeciesAtEnd++;
                }
            }
        }

        List<PlayerSpeciesRow> playerSpeciesRows() {
            return playerSpeciesRows;
        }

        int unfinishedPlayers() {
            return expectedPlayers - completedPlayers;
        }

        int suspiciousScore() {
            int score = 0;
            score += unfinishedPlayers() * 1_000;
            if (rounds >= 280) score += 600;
            if (microStepLimitHit) score += 800;
            score += activeTrackingAtEnd * 300;
            score += activeHuntsAtEnd * 120;
            score += trappedAwaitingPickupAtEnd * 180;
            score += engineerNoUseful * 20;
            score += driverCannotReach * 15;
            score += plannerSkips * 8;
            score += trackingFailures * 12;
            score += huntRelocations * 6;
            score += Math.max(0, roadBuilds - 25) * 3;
            return score;
        }

        private boolean isInteresting(String message) {
            return message.contains("ПРОВАЛ")
                    || message.contains("не нашёл")
                    || message.contains("не может")
                    || message.contains("стратегически лучше пропустить")
                    || message.contains("планировщик пропустил")
                    || message.contains("переносит засаду")
                    || message.contains("идёт по следу")
                    || message.contains("снял старую ловушку")
                    || message.contains("ДОСТАВЛЕН")
                    || message.contains("ПОЙМАН")
                    || message.contains("В ЛОВУШКЕ");
        }

        private static int countRoadSegments(GameMap map) {
            int count = 0;
            for (Map.Entry<ru.mesozoa.sim.model.Point, Tile> entry : map.entries()) {
                count += entry.getValue().roadDirections.size();
            }
            return count;
        }

        private static int countBridges(GameMap map) {
            int count = 0;
            for (Map.Entry<ru.mesozoa.sim.model.Point, Tile> entry : map.entries()) {
                if (entry.getValue().hasBridge) count++;
            }
            return count;
        }

        private static int trailingInteger(String message) {
            int end = message.length() - 1;
            while (end >= 0 && Character.isWhitespace(message.charAt(end))) end--;
            int start = end;
            while (start >= 0 && Character.isDigit(message.charAt(start))) start--;
            if (start == end) return 0;
            return Integer.parseInt(message.substring(start + 1, end + 1));
        }
    }

    /** Статистика по виду динозавра в серии партий. */
    private static final class SpeciesStats {
        final Species species;
        int taskCount;
        int capturedCount;
        final ArrayList<Integer> firstSpawnRounds = new ArrayList<>();
        final ArrayList<Integer> captureRounds = new ArrayList<>();

        SpeciesStats(Species species) {
            this.species = species;
        }
    }

    /** Строка CSV по одной цели игрока. */
    private static final class PlayerSpeciesRow {
        final int gameIndex;
        final long seed;
        final int playerId;
        final Species species;
        final CaptureMethod method;
        Integer firstSpawnRound;
        Integer captureRound;

        PlayerSpeciesRow(int gameIndex, long seed, int playerId, Species species, CaptureMethod method) {
            this.gameIndex = gameIndex;
            this.seed = seed;
            this.playerId = playerId;
            this.species = species;
            this.method = method;
        }

        boolean captured() {
            return captureRound != null;
        }
    }

    /** Событие журнала с номером раунда. */
    private record RoundLog(int round, String message) {
    }

    /** Записывает Markdown и CSV по итогам batch-прогона. */
    private static final class ReportWriter {
        private final RunnerConfig config;
        private final List<GameStats> games;
        private final List<PlayerSpeciesRow> playerSpeciesRows;
        private final long elapsedMillis;

        ReportWriter(
                RunnerConfig config,
                List<GameStats> games,
                List<PlayerSpeciesRow> playerSpeciesRows,
                long elapsedMillis
        ) {
            this.config = config;
            this.games = games;
            this.playerSpeciesRows = playerSpeciesRows;
            this.elapsedMillis = elapsedMillis;
        }

        void writeAll() throws IOException {
            writeGamesCsv();
            writePlayerSpeciesCsv();
            writeAiEventsCsv();
            writeSuspiciousCsv();
            writeMarkdown();
        }

        private void writeGamesCsv() throws IOException {
            try (BufferedWriter out = writer("games.csv")) {
                out.write(String.join(",",
                        "game", "seed", "rounds", "completedPlayers", "unfinishedPlayers",
                        "openedTiles", "spawnedDinosaurs", "capturedDinosaurs", "microSteps",
                        "roadBuilds", "bridgeBuilds", "finalRoadSegments", "finalBridges",
                        "trapTokensPlaced", "trapRepositions", "staleTrapsRecovered",
                        "plannerSkips", "engineerNoUseful", "driverCannotReach",
                        "huntStarts", "huntFailures", "huntRelocations",
                        "trackingStarts", "trackingAttempts", "trackingFailures", "trackingStalls",
                        "activeHuntsAtEnd", "activeTrackingAtEnd", "trappedAwaitingPickupAtEnd",
                        "visibleUncapturedTaskSpeciesAtEnd", "hiddenUncapturedTaskSpeciesAtEnd",
                        "microStepLimitHit", "suspiciousScore"));
                out.newLine();
                for (GameStats game : games) {
                    out.write(csvLine(
                            game.gameIndex, game.seed, game.rounds, game.completedPlayers, game.unfinishedPlayers(),
                            game.openedTiles, game.spawnedDinosaurs, game.capturedDinosaurs, game.microSteps,
                            game.roadBuilds, game.bridgeBuilds, game.finalRoadSegments, game.finalBridges,
                            game.trapTokensPlaced, game.trapRepositions, game.staleTrapsRecovered,
                            game.plannerSkips, game.engineerNoUseful, game.driverCannotReach,
                            game.huntStarts, game.huntFailures, game.huntRelocations,
                            game.trackingStarts, game.trackingAttempts, game.trackingFailures, game.trackingStalls,
                            game.activeHuntsAtEnd, game.activeTrackingAtEnd, game.trappedAwaitingPickupAtEnd,
                            game.visibleUncapturedTaskSpeciesAtEnd, game.hiddenUncapturedTaskSpeciesAtEnd,
                            game.microStepLimitHit, game.suspiciousScore()));
                    out.newLine();
                }
            }
        }

        private void writePlayerSpeciesCsv() throws IOException {
            try (BufferedWriter out = writer("player_species.csv")) {
                out.write("game,seed,playerId,species,method,firstSpawnRound,captureRound,captured");
                out.newLine();
                for (PlayerSpeciesRow row : playerSpeciesRows) {
                    out.write(csvLine(
                            row.gameIndex,
                            row.seed,
                            row.playerId,
                            row.species.displayName,
                            row.method,
                            nullable(row.firstSpawnRound),
                            nullable(row.captureRound),
                            row.captured()));
                    out.newLine();
                }
            }
        }

        private void writeAiEventsCsv() throws IOException {
            try (BufferedWriter out = writer("ai_events.csv")) {
                out.write(String.join(",",
                        "game", "seed", "plannerBestSkipSignals", "noAvailableRanger", "scoutNoTiles",
                        "engineerCannotAdvanceRoad", "engineerNoLegalTrapCell", "driverWaits",
                        "driverNotEnoughActions", "driverArrivals", "deliveries",
                        "huntCardDraws", "huntWaits", "trackingTrailMoves", "cryptognathBaitThefts", "predatorKills"));
                out.newLine();
                for (GameStats game : games) {
                    out.write(csvLine(
                            game.gameIndex, game.seed, game.plannerBestSkipSignals, game.noAvailableRanger, game.scoutNoTiles,
                            game.engineerCannotAdvanceRoad, game.engineerNoLegalTrapCell, game.driverWaits,
                            game.driverNotEnoughActions, game.driverArrivals, game.deliveries,
                            game.huntCardDraws, game.huntWaits, game.trackingTrailMoves, game.cryptognathBaitThefts, game.predatorKills));
                    out.newLine();
                }
            }
        }

        private void writeSuspiciousCsv() throws IOException {
            try (BufferedWriter out = writer("suspicious_examples.csv")) {
                out.write("game,seed,round,score,message");
                out.newLine();
                for (GameStats game : suspiciousGames(20)) {
                    List<RoundLog> tail = game.interestingLogs.stream()
                            .skip(Math.max(0, game.interestingLogs.size() - 25))
                            .toList();
                    if (tail.isEmpty()) {
                        out.write(csvLine(game.gameIndex, game.seed, game.rounds, game.suspiciousScore(), "нет интересных логов"));
                        out.newLine();
                    } else {
                        for (RoundLog log : tail) {
                            out.write(csvLine(game.gameIndex, game.seed, log.round(), game.suspiciousScore(), log.message()));
                            out.newLine();
                        }
                    }
                }
            }
        }

        private void writeMarkdown() throws IOException {
            try (BufferedWriter out = writer("mesozoa_batch_report.md")) {
                out.write("# Mesozoa batch report");
                out.newLine();
                out.newLine();
                out.write("## Параметры прогона");
                out.newLine();
                out.newLine();
                out.write("```text");
                out.newLine();
                out.write("games = " + config.games());
                out.newLine();
                out.write("players = " + config.players());
                out.newLine();
                out.write("maxRounds = " + config.maxRounds());
                out.newLine();
                out.write("seedStart = " + config.seedStart());
                out.newLine();
                out.write("taskDinosaurCount = " + config.taskDinosaurCount());
                out.newLine();
                out.write("elapsedMillis = " + elapsedMillis);
                out.newLine();
                out.write("```");
                out.newLine();
                out.newLine();

                writeFinishSummary(out);
                writeDurationSummary(out);
                writeCaptureMethodSummary(out);
                writeSpeciesSummary(out);
                writeAiSummary(out);
                writeSuspiciousSummary(out);
                writeFilesSummary(out);
            }
        }

        private void writeFinishSummary(BufferedWriter out) throws IOException {
            Map<Integer, Long> byCompleted = games.stream()
                    .collect(Collectors.groupingBy(game -> game.completedPlayers, LinkedHashMap::new, Collectors.counting()));

            out.write("## Завершение партий");
            out.newLine();
            out.newLine();
            for (int completed = config.players(); completed >= 0; completed--) {
                out.write("- Выполнили задание " + completed + " / " + config.players()
                        + ": " + byCompleted.getOrDefault(completed, 0L));
                out.newLine();
            }
            out.write("- Упёрлись в защитный лимит микрошагов: "
                    + games.stream().filter(game -> game.microStepLimitHit).count());
            out.newLine();
            out.newLine();
        }

        private void writeDurationSummary(BufferedWriter out) throws IOException {
            out.write("## Длительность");
            out.newLine();
            out.newLine();
            out.write("```text");
            out.newLine();
            out.write("Все партии: среднее " + oneDecimal(average(games.stream().map(game -> game.rounds).toList()))
                    + ", медиана " + oneDecimal(median(games.stream().map(game -> game.rounds).toList()))
                    + ", min..max " + minRound() + ".." + maxRound());
            out.newLine();

            List<Integer> completedRounds = games.stream()
                    .filter(game -> game.completedPlayers == config.players())
                    .map(game -> game.rounds)
                    .toList();
            out.write("Полностью завершённые: среднее " + oneDecimal(average(completedRounds))
                    + ", медиана " + oneDecimal(median(completedRounds)));
            out.newLine();
            out.write("```");
            out.newLine();
            out.newLine();
        }

        private void writeCaptureMethodSummary(BufferedWriter out) throws IOException {
            out.write("## По способам поимки");
            out.newLine();
            out.newLine();
            out.write("| Метод | Целей | Поймано | Успешность | Средний раунд поимки |");
            out.newLine();
            out.write("|---|---:|---:|---:|---:|");
            out.newLine();
            for (CaptureMethod method : CaptureMethod.values()) {
                int tasks = playerSpeciesRows.stream().filter(row -> row.method == method).toList().size();
                List<Integer> captureRounds = playerSpeciesRows.stream()
                        .filter(row -> row.method == method)
                        .filter(PlayerSpeciesRow::captured)
                        .map(row -> row.captureRound)
                        .toList();
                out.write("| " + method + " | " + tasks + " | " + captureRounds.size()
                        + " | " + percent(captureRounds.size(), tasks)
                        + " | " + oneDecimal(average(captureRounds)) + " |");
                out.newLine();
            }
            out.newLine();
        }

        private void writeSpeciesSummary(BufferedWriter out) throws IOException {
            out.write("## По видам");
            out.newLine();
            out.newLine();
            out.write("| Вид | Метод | Целей | Поймано | Успешность | Ср. первый спаун | Ср. поимка |");
            out.newLine();
            out.write("|---|---|---:|---:|---:|---:|---:|");
            out.newLine();

            for (Species species : Species.values()) {
                List<PlayerSpeciesRow> rows = playerSpeciesRows.stream()
                        .filter(row -> row.species == species)
                        .toList();
                List<Integer> spawnRounds = rows.stream()
                        .map(row -> row.firstSpawnRound)
                        .filter(round -> round != null)
                        .toList();
                List<Integer> captureRounds = rows.stream()
                        .map(row -> row.captureRound)
                        .filter(round -> round != null)
                        .toList();
                out.write("| " + species.displayName
                        + " | " + Dinosaur.captureMethodOf(species)
                        + " | " + rows.size()
                        + " | " + captureRounds.size()
                        + " | " + percent(captureRounds.size(), rows.size())
                        + " | " + oneDecimal(average(spawnRounds))
                        + " | " + oneDecimal(average(captureRounds))
                        + " |");
                out.newLine();
            }
            out.newLine();
        }

        private void writeAiSummary(BufferedWriter out) throws IOException {
            out.write("## Поведение AI: агрегаты");
            out.newLine();
            out.newLine();
            out.write("```text");
            out.newLine();
            writeMetric(out, "plannerSkips", games.stream().mapToInt(game -> game.plannerSkips).sum());
            writeMetric(out, "engineerNoUseful", games.stream().mapToInt(game -> game.engineerNoUseful).sum());
            writeMetric(out, "roadBuilds", games.stream().mapToInt(game -> game.roadBuilds).sum());
            writeMetric(out, "bridgeBuilds", games.stream().mapToInt(game -> game.bridgeBuilds).sum());
            writeMetric(out, "trapTokensPlaced", games.stream().mapToInt(game -> game.trapTokensPlaced).sum());
            writeMetric(out, "trapRepositions", games.stream().mapToInt(game -> game.trapRepositions).sum());
            writeMetric(out, "staleTrapsRecovered", games.stream().mapToInt(game -> game.staleTrapsRecovered).sum());
            writeMetric(out, "driverCannotReach", games.stream().mapToInt(game -> game.driverCannotReach).sum());
            writeMetric(out, "huntFailures", games.stream().mapToInt(game -> game.huntFailures).sum());
            writeMetric(out, "huntRelocations", games.stream().mapToInt(game -> game.huntRelocations).sum());
            writeMetric(out, "trackingFailures", games.stream().mapToInt(game -> game.trackingFailures).sum());
            writeMetric(out, "trackingStalls", games.stream().mapToInt(game -> game.trackingStalls).sum());
            writeMetric(out, "activeTrackingAtEnd", games.stream().mapToInt(game -> game.activeTrackingAtEnd).sum());
            writeMetric(out, "trappedAwaitingPickupAtEnd", games.stream().mapToInt(game -> game.trappedAwaitingPickupAtEnd).sum());
            out.write("```");
            out.newLine();
            out.newLine();

            out.write("Средние значения на партию:");
            out.newLine();
            out.newLine();
            out.write("```text");
            out.newLine();
            writeAverageMetric(out, "roads/game", games.stream().mapToInt(game -> game.roadBuilds).sum());
            writeAverageMetric(out, "bridges/game", games.stream().mapToInt(game -> game.bridgeBuilds).sum());
            writeAverageMetric(out, "plannerSkips/game", games.stream().mapToInt(game -> game.plannerSkips).sum());
            writeAverageMetric(out, "engineerNoUseful/game", games.stream().mapToInt(game -> game.engineerNoUseful).sum());
            writeAverageMetric(out, "huntFailures/game", games.stream().mapToInt(game -> game.huntFailures).sum());
            writeAverageMetric(out, "trackingFailures/game", games.stream().mapToInt(game -> game.trackingFailures).sum());
            out.write("```");
            out.newLine();
            out.newLine();
        }

        private void writeSuspiciousSummary(BufferedWriter out) throws IOException {
            out.write("## Самые подозрительные партии");
            out.newLine();
            out.newLine();
            out.write("| Игра | Seed | Раунды | Выполнили | Score | Дороги | Engineer noop | Skip | Hunt fail | Track fail | Активный след |");
            out.newLine();
            out.write("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
            out.newLine();
            for (GameStats game : suspiciousGames(10)) {
                out.write("| " + game.gameIndex
                        + " | " + game.seed
                        + " | " + game.rounds
                        + " | " + game.completedPlayers + "/" + config.players()
                        + " | " + game.suspiciousScore()
                        + " | " + game.roadBuilds
                        + " | " + game.engineerNoUseful
                        + " | " + game.plannerSkips
                        + " | " + game.huntFailures
                        + " | " + game.trackingFailures
                        + " | " + game.activeTrackingAtEnd
                        + " |");
                out.newLine();
            }
            out.newLine();
            out.write("Детальные хвосты интересных логов лежат в `suspicious_examples.csv`.");
            out.newLine();
            out.newLine();
        }

        private void writeFilesSummary(BufferedWriter out) throws IOException {
            out.write("## Сгенерированные файлы");
            out.newLine();
            out.newLine();
            out.write("- `games.csv` — одна строка на партию.");
            out.newLine();
            out.write("- `player_species.csv` — одна строка на цель игрока.");
            out.newLine();
            out.write("- `ai_events.csv` — дополнительные счётчики поведения AI.");
            out.newLine();
            out.write("- `suspicious_examples.csv` — хвосты важных логов по худшим партиям.");
            out.newLine();
        }

        private void writeMetric(BufferedWriter out, String name, int value) throws IOException {
            out.write(name + " = " + value);
            out.newLine();
        }

        private void writeAverageMetric(BufferedWriter out, String name, int total) throws IOException {
            out.write(name + " = " + oneDecimal(total / (double) Math.max(1, games.size())));
            out.newLine();
        }

        private List<GameStats> suspiciousGames(int limit) {
            return games.stream()
                    .sorted(Comparator
                            .comparingInt(GameStats::suspiciousScore).reversed()
                            .thenComparing(Comparator.comparingInt((GameStats game) -> game.rounds).reversed()))
                    .limit(limit)
                    .toList();
        }

        private int minRound() {
            return games.stream().mapToInt(game -> game.rounds).min().orElse(0);
        }

        private int maxRound() {
            return games.stream().mapToInt(game -> game.rounds).max().orElse(0);
        }

        private BufferedWriter writer(String fileName) throws IOException {
            return Files.newBufferedWriter(config.outputDir().resolve(fileName), StandardCharsets.UTF_8);
        }
    }

    private static String nullable(Integer value) {
        return value == null ? "" : Integer.toString(value);
    }

    private static String percent(int numerator, int denominator) {
        if (denominator == 0) return "n/a";
        return oneDecimal(100.0 * numerator / denominator) + "%";
    }

    private static double average(List<Integer> values) {
        if (values.isEmpty()) return Double.NaN;
        return values.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
    }

    private static double median(List<Integer> values) {
        if (values.isEmpty()) return Double.NaN;
        ArrayList<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static String oneDecimal(double value) {
        if (Double.isNaN(value)) return "n/a";
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String csvLine(Object... values) {
        ArrayList<String> escaped = new ArrayList<>(values.length);
        for (Object value : values) {
            escaped.add(csv(value));
        }
        return String.join(",", escaped);
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
