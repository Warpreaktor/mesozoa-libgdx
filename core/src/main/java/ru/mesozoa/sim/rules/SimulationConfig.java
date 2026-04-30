package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.model.Species;

import java.util.EnumMap;
import java.util.Map;

public final class SimulationConfig {
    public int width = 18;
    public int height = 10;
    public int players = 4;
    public int maxRounds = 80;
    public int scoutOpenActions = 1;
    public int maxTrapsPerPlayer = 3;

    public double trackingBaseSuccess = 0.20;
    public double trackingStepBonus = 0.20;
    public int trackingMaxSteps = 3;
    public double huntBaseSuccess = 0.35;
    public double huntPreparedSuccess = 0.65;

    public final Map<Species, Integer> spawnTiles = new EnumMap<>(Species.class);

    public SimulationConfig() {
        spawnTiles.put(Species.GALLIMIMON, 6);
        spawnTiles.put(Species.DRIORNIS, 6);
        spawnTiles.put(Species.CRYPTOGNATH, 6);
        spawnTiles.put(Species.VELOCITAURUS, 4);
        spawnTiles.put(Species.MONOCERATUS, 4);
        spawnTiles.put(Species.VOCAREZAUROLOPH, 4);
    }
}
