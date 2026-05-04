package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/** Видовой профиль Криптогната: Хвойный лес → Болото → Хвойный лес. */
public final class CryptognathProfile extends AbstractBioTrailDinosaurProfile {
    public CryptognathProfile() {
        super(Species.CRYPTOGNATH, List.of(Biome.CONIFEROUS_FOREST, Biome.SWAMP, Biome.CONIFEROUS_FOREST));
    }
}
