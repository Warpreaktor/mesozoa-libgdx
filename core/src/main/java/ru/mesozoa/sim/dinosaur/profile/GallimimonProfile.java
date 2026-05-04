package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/** Видовой профиль Галлимимона */
public final class GallimimonProfile extends AbstractBioTrailDinosaurProfile {
    public GallimimonProfile() {
        super(Species.GALLIMIMON, List.of(Biome.MEADOW, Biome.SWAMP, Biome.CONIFEROUS_FOREST));
    }
}
