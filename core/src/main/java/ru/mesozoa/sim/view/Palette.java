package ru.mesozoa.sim.view;

import com.badlogic.gdx.graphics.Color;
import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

public final class Palette {
    private Palette() {}

    public static Color biome(Biome biome) {
        return switch (biome) {
            case BROADLEAF_FOREST -> new Color(0.18f, 0.46f, 0.20f, 1f);
            case CONIFEROUS_FOREST -> new Color(0.05f, 0.32f, 0.22f, 1f);
            case MEADOW -> new Color(0.56f, 0.68f, 0.25f, 1f);
            case SWAMP -> new Color(0.23f, 0.32f, 0.20f, 1f);
            case RIVER -> new Color(0.18f, 0.48f, 0.78f, 1f);
            case LAKE -> new Color(0.10f, 0.35f, 0.63f, 1f);
            case FLOODPLAIN -> new Color(0.45f, 0.62f, 0.43f, 1f);
            case MOUNTAIN -> new Color(0.42f, 0.40f, 0.37f, 1f);
            case LANDING -> new Color(0f, 0f, 0f, 0f);
        };
    }

    public static Color species(Species species) {
        return switch (species) {
            case GALLIMIMON -> new Color(1.0f, 0.85f, 0.15f, 1f);
            case DRIORNIS -> new Color(0.75f, 1.0f, 0.35f, 1f);
            case CRYPTOGNATH -> new Color(0.35f, 0.95f, 0.35f, 1f);
            case VELOCITAURUS -> new Color(0.85f, 0.12f, 0.12f, 1f);
            case MONOCERATUS -> new Color(0.95f, 0.55f, 0.22f, 1f);
            case VOCAREZAUROLOPH -> new Color(0.65f, 0.42f, 1.0f, 1f);
        };
    }
}
