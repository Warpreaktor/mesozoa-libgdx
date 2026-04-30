package ru.mesozoa.sim.report;

import ru.mesozoa.sim.model.Species;

import java.util.EnumMap;
import java.util.Map;

public final class GameResult {
    public int rounds;
    public int openedTiles;
    public int completedPlayers;
    public int spawnedDinosaurs;
    public int capturedDinosaurs;
    public int predatorKills;
    public int trapCaptures;
    public int trackingCaptures;
    public int huntCaptures;
    public final Map<Species, Integer> firstSpawnRound = new EnumMap<>(Species.class);
}
