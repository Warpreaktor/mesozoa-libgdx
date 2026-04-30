package ru.mesozoa.sim.model;

public enum Biome {
    BROADLEAF_FOREST("Лиственный лес", "broadleaf_forest.png"),
    CONIFEROUS_FOREST("Хвойный лес", "coniferous_forest.png"),
    MEADOW("Луг", "meadow.png"),
    SWAMP("Болото", "swamp.png"),
    RIVER("Река", "river.png"),
    LAKE("Озеро", "lake.png"),
    FLOODPLAIN("Пойма", "floodplain.png"),
    MOUNTAIN("Горы", "mountain.png"),
    LANDING("База", "landing.png");

    public final String displayName;
    public final String imagePath;

    Biome(String displayName, String imagePath) {
        this.displayName = displayName;
        this.imagePath = imagePath;
    }

    public boolean isWaterLike() {
        return this == RIVER || this == LAKE || this == SWAMP || this == FLOODPLAIN;
    }

    public boolean blocksMostMovement() {
        return this == LAKE || this == MOUNTAIN;
    }
}
