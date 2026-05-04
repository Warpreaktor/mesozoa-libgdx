package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/** Видовой профиль Моноцератуса: Пойма → Река → Луг → Хвойный лес. */
public final class MonoceratusProfile extends AbstractBioTrailDinosaurProfile {
    public MonoceratusProfile() {
        super(Species.MONOCERATUS, List.of(Biome.FLOODPLAIN, Biome.RIVER, Biome.MEADOW, Biome.CONIFEROUS_FOREST));
    }
}
