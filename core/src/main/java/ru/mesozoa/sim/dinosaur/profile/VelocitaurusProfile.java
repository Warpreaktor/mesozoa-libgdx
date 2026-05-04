package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/** Видовой профиль Велоцитауруса: Луг → Лиственный лес → Хвойный лес → Река. */
public final class VelocitaurusProfile extends AbstractBioTrailDinosaurProfile {
    public VelocitaurusProfile() {
        super(Species.VELOCITAURUS, List.of(Biome.MEADOW, Biome.BROADLEAF_FOREST, Biome.CONIFEROUS_FOREST, Biome.RIVER));
    }
}
