package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/** Видовой профиль Дриорниса: Лиственный лес → Пойма → Луг. */
public final class DriornisProfile extends AbstractBioTrailDinosaurProfile {
    public DriornisProfile() {
        super(Species.DRIORNIS, List.of(Biome.BROADLEAF_FOREST, Biome.FLOODPLAIN, Biome.MEADOW));
    }
}
