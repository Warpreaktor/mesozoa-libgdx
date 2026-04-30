package ru.mesozoa.sim.model;

import java.util.List;

public enum Species {
    GALLIMIMON("Галлимимон", "G", "gallimimon.png", SizeClass.S, DietType.HERBIVORE, CaptureMethod.TRAP, 5, 0, Biome.MEADOW, List.of(Biome.MEADOW, Biome.SWAMP, Biome.CONIFEROUS_FOREST)),
    DRIORNIS("Дриорнис", "D", "driornis.png", SizeClass.S, DietType.HERBIVORE, CaptureMethod.TRAP, 4, 0, Biome.BROADLEAF_FOREST, List.of(Biome.BROADLEAF_FOREST, Biome.FLOODPLAIN, Biome.MEADOW)),
    CRYPTOGNATH("Криптогнат", "K", "cryptognath.png", SizeClass.S, DietType.SMALL_PREDATOR, CaptureMethod.TRAP, 5, 0, Biome.CONIFEROUS_FOREST, List.of(Biome.CONIFEROUS_FOREST, Biome.SWAMP, Biome.CONIFEROUS_FOREST)),
    VELOCITAURUS("Велоцитаурус", "V", "velocitaurus.png", SizeClass.M, DietType.PREDATOR, CaptureMethod.HUNT, 5, 1, Biome.MEADOW, List.of(Biome.MEADOW, Biome.BROADLEAF_FOREST, Biome.CONIFEROUS_FOREST, Biome.RIVER)),
    MONOCERATUS("Моноцератус", "M", "monoceratus.png", SizeClass.M, DietType.HERBIVORE, CaptureMethod.TRACKING, 2, 0, Biome.FLOODPLAIN, List.of(Biome.FLOODPLAIN, Biome.RIVER, Biome.MEADOW, Biome.CONIFEROUS_FOREST)),
    VOCAREZAUROLOPH("Вокарезауролоф", "W", "vocarezauroloph.png", SizeClass.M, DietType.HERBIVORE, CaptureMethod.TRACKING, 3, 0, Biome.RIVER, List.of(Biome.RIVER, Biome.MEADOW, Biome.BROADLEAF_FOREST, Biome.FLOODPLAIN));

    public final String displayName;
    public final String shortCode;
    public final String imagePath;
    public final SizeClass size;
    public final DietType diet;
    public final CaptureMethod captureMethod;
    public final int agility;
    public final int huntRadius;
    public final Biome spawnBiome;
    public final List<Biome> bioTrail;

    Species(String displayName, String shortCode, String imagePath, SizeClass size, DietType diet, CaptureMethod captureMethod, int agility, int huntRadius, Biome spawnBiome, List<Biome> bioTrail) {
        this.displayName = displayName;
        this.shortCode = shortCode;
        this.imagePath = imagePath;
        this.size = size;
        this.diet = diet;
        this.captureMethod = captureMethod;
        this.agility = agility;
        this.huntRadius = huntRadius;
        this.spawnBiome = spawnBiome;
        this.bioTrail = bioTrail;
    }

    public boolean isHerbivore() {
        return diet == DietType.HERBIVORE;
    }
}
